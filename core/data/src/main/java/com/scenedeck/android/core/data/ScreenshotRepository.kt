package com.scenedeck.android.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ScreenshotRequest
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Scene preview thumbnails via OBS `GetSourceScreenshot` (FEATURE_SPEC §8, M6).
 *
 * THROTTLE POLICY (deliberate, see brief):
 * - Polls only while: connected (Ready) AND a collector is subscribed (screen visible) AND the
 *   "Scene previews" setting is ON (default ON).
 * - Program + preview scenes refresh every [HOT_REFRESH_MS] (~2.5 s); every other deck scene every
 *   [COLD_REFRESH_MS] (~10 s).
 * - At most [MAX_CONCURRENT] requests in flight; requests are 360 px-wide JPEG at quality 50.
 * - A failed/expired capture keeps the previous frame; the card falls back to its icon when there
 *   is nothing to show. Black/quiet scenes show whatever OBS returns (no cleverness).
 */
@Singleton
class ScreenshotRepository
@Inject
constructor(
    private val client: ObsClient,
    private val settings: SettingsRepository,
    private val obsState: ObsStateRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _thumbnails = MutableStateFlow(SceneThumbnails())

    /** sceneName → latest captured frame (decoded off the main thread). */
    val thumbnails: StateFlow<SceneThumbnails> = _thumbnails.asStateFlow()

    /** Elapsed time must remain stable when the device wall clock changes. */
    internal var nowMs: () -> Long = ::previewMonotonicTimeMs

    // Owned by the poller; collectLatest joins all captures before changing configuration.
    private val lastFetchMs = mutableMapOf<String, Long>()

    private data class CaptureConfiguration(
        val enabled: Boolean,
        val workspace: WorkspaceSession?,
        val scenes: List<String>,
        val hotScenes: Set<String>,
    )

    init {
        scope.launch {
            combine(
                    client.connectionState,
                    settings.settings,
                    obsState.deckState,
                    _thumbnails.subscriptionCount,
                ) { connection, userSettings, deck, subscribers ->
                    CaptureConfiguration(
                        workspace = deck.workspace,
                        enabled =
                            connection is ConnectionState.Ready &&
                                deck.workspace?.let(obsState::isWorkspaceCurrent) == true &&
                                subscribers > 0 &&
                                userSettings.scenePreviewsEnabled,
                        scenes = deck.allScenes.map { it.name }.distinct(),
                        hotScenes =
                            listOfNotNull(deck.currentProgramScene, deck.previewScene).toSet(),
                    )
                }
                .distinctUntilChanged()
                .collectLatest { configuration ->
                    if (_thumbnails.value.workspace != configuration.workspace) {
                        _thumbnails.value = SceneThumbnails(configuration.workspace)
                        lastFetchMs.clear()
                    }
                    if (configuration.enabled) {
                        val liveNames = configuration.scenes.toSet()
                        lastFetchMs.keys.retainAll(liveNames)
                        _thumbnails.update {
                            it.copy(
                                frames = it.frames.filterKeys { name -> name in liveNames },
                                failedScenes = it.failedScenes.intersect(liveNames),
                            )
                        }
                        pollLoop(configuration)
                    } else {
                        _thumbnails.value = SceneThumbnails()
                        lastFetchMs.clear()
                    }
                }
        }
    }

    private suspend fun pollLoop(configuration: CaptureConfiguration) {
        while (currentCoroutineContext().isActive) {
            val now = nowMs()
            val due = dueScenes(configuration, now)
            if (due.isEmpty()) {
                delay(TICK_MS)
            } else {
                captureBatch(requireNotNull(configuration.workspace), due, now)
                yield()
            }
        }
    }

    private suspend fun captureBatch(origin: WorkspaceSession, names: List<String>, now: Long) {
        var completed = false
        try {
            coroutineScope {
                names.forEach { name ->
                    currentCoroutineContext().ensureActive()
                    lastFetchMs[name] = now
                    launch { capture(origin, name) }
                }
            }
            completed = true
        } finally {
            // Interrupted requests should be eligible when the next configuration resumes.
            if (!completed) names.forEach { lastFetchMs.remove(it) }
        }
    }

    private fun dueScenes(configuration: CaptureConfiguration, now: Long): List<String> {
        val due =
            configuration.scenes
                .filter { name ->
                    val interval =
                        if (name in configuration.hotScenes) HOT_REFRESH_MS else COLD_REFRESH_MS
                    lastFetchMs[name]?.let { now - it >= interval } ?: true
                }
                .sortedWith(
                    compareBy<String> { if (it in configuration.hotScenes) 0 else 1 }
                        .thenBy { lastFetchMs[it] ?: Long.MIN_VALUE }
                )
        val batch = due.take(MAX_CONCURRENT)
        val cold = due.firstOrNull { it !in configuration.hotScenes }
        // Two slow hot captures must not permanently starve the rest of the deck.
        return if (
            batch.size == MAX_CONCURRENT &&
                batch.all { it in configuration.hotScenes } &&
                cold != null
        ) {
            listOf(batch.first(), cold)
        } else batch
    }

    private suspend fun capture(origin: WorkspaceSession, sceneName: String) {
        val bitmap = requestResult {
            val bytes =
                client.withSession(origin.token) {
                    getSourceScreenshot(
                        ScreenshotRequest(
                            sourceName = sceneName,
                            format = "jpeg",
                            compressionQuality = JPEG_QUALITY,
                            width = WIDTH_PX,
                        )
                    )
                }
            // A valid black image is still a successful capture and must replace the old frame.
            decodeBitmap(bytes)
        }
            .getOrNull()
        currentCoroutineContext().ensureActive()
        val capturedAt = nowMs()
        _thumbnails.update { state ->
            when {
                state.workspace != origin || !obsState.isWorkspaceCurrent(origin) -> state
                bitmap == null -> state.copy(failedScenes = state.failedScenes + sceneName)
                else ->
                    state.copy(
                        frames = state.frames + (sceneName to SceneThumbnail(bitmap, capturedAt)),
                        failedScenes = state.failedScenes - sceneName,
                    )
            }
        }
    }

    internal fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.size > ScreenshotRequest.MAX_IMAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val validDimensions =
            bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight <= MAX_DECODED_PIXELS
        return if (validDimensions) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
    }

    private companion object {
        const val HOT_REFRESH_MS = 2_500L
        const val COLD_REFRESH_MS = 10_000L
        const val TICK_MS = 500L
        const val MAX_CONCURRENT = 2
        const val JPEG_QUALITY = 50
        const val WIDTH_PX = 360
        const val MAX_DECODED_PIXELS = 1_048_576L
    }
}
