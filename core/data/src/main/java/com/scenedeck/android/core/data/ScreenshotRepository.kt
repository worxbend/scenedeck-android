package com.scenedeck.android.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Scene preview thumbnails via OBS `GetSourceScreenshot` (FEATURE_SPEC §8, M6).
 *
 * THROTTLE POLICY (deliberate, see brief):
 * - Polls only while: connected (Ready) AND a collector is subscribed (screen
 *   visible) AND the "Scene previews" setting is ON (default ON).
 * - Program + preview scenes refresh every [HOT_REFRESH_MS] (~2.5 s); every other
 *   deck scene every [COLD_REFRESH_MS] (~10 s).
 * - At most [MAX_CONCURRENT] requests in flight; requests are 360 px-wide JPEG
 *   at quality 50.
 * - A failed/expired capture keeps the previous frame; the card falls back to
 *   its icon when there is nothing to show. Black/quiet scenes show whatever
 *   OBS returns (no cleverness).
 */
@Singleton
class ScreenshotRepository @Inject constructor(
    private val client: ObsClient,
    private val settings: SettingsRepository,
    private val obsState: ObsStateRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    /** sceneName → latest captured frame (decoded off the main thread). */
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    /** Injectable sample clock (wall clock in production; virtual in tests). */
    internal var nowMs: () -> Long = System::currentTimeMillis

    private val lastFetchMs = mutableMapOf<String, Long>()
    private val semaphore = Semaphore(MAX_CONCURRENT)

    init {
        scope.launch {
            combine(
                client.connectionState,
                settings.settings,
                obsState.deckState,
            ) { connection, userSettings, deck ->
                Triple(connection is ConnectionState.Ready, userSettings.scenePreviewsEnabled, deck)
            }.collectLatest { (ready, previewsEnabled, deck) ->
                if (ready && previewsEnabled && _thumbnails.subscriptionCount.value > 0) {
                    pollLoop(deck)
                } else {
                    _thumbnails.value = emptyMap()
                    lastFetchMs.clear()
                }
            }
        }
    }

    private suspend fun pollLoop(deck: DeckState) {
        val hotScenes = listOfNotNull(deck.currentProgramScene, deck.previewScene).toSet()
        val sceneNames = deck.scenes.map { it.name }
        while (currentCoroutineContext().isActive) {
            val now = nowMs()
            sceneNames.forEach { name ->
                val interval = if (name in hotScenes) HOT_REFRESH_MS else COLD_REFRESH_MS
                val last = lastFetchMs[name]
                if (last == null || now - last >= interval) {
                    lastFetchMs[name] = now
                    scope.launch { capture(name) }
                }
            }
            delay(TICK_MS)
        }
    }

    private suspend fun capture(sceneName: String) {
        semaphore.withPermit {
            runCatching {
                val bytes = client.getSourceScreenshot(
                    sourceName = sceneName,
                    format = "jpeg",
                    compressionQuality = JPEG_QUALITY,
                    width = WIDTH_PX,
                )
                val bitmap = decodeBitmap(bytes) ?: return@runCatching
                _thumbnails.value = _thumbnails.value + (sceneName to bitmap)
            }
            // On failure: keep the previous frame (or nothing → icon fallback).
        }
    }

    internal fun decodeBitmap(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private companion object {
        const val HOT_REFRESH_MS = 2_500L
        const val COLD_REFRESH_MS = 10_000L
        const val TICK_MS = 500L
        const val MAX_CONCURRENT = 2
        const val JPEG_QUALITY = 50
        const val WIDTH_PX = 360
    }
}
