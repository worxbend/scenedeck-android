package com.scenedeck.android.core.obs.internal

import com.rejeq.ktobs.ObsEventSubs
import com.rejeq.ktobs.ObsRequestException
import com.rejeq.ktobs.ObsSession
import com.rejeq.ktobs.request.config.getProfileList
import com.rejeq.ktobs.request.config.getSceneCollectionList
import com.rejeq.ktobs.request.config.setCurrentProfile
import com.rejeq.ktobs.request.config.setCurrentSceneCollection
import com.rejeq.ktobs.request.general.getStats
import com.rejeq.ktobs.request.general.getVersion
import com.rejeq.ktobs.request.inputs.getInputAudioBalance
import com.rejeq.ktobs.request.inputs.getInputAudioMonitorType
import com.rejeq.ktobs.request.inputs.getInputAudioSyncOffset
import com.rejeq.ktobs.request.inputs.getInputMute
import com.rejeq.ktobs.request.inputs.getInputVolume
import com.rejeq.ktobs.request.inputs.getSpecialInputs
import com.rejeq.ktobs.request.inputs.setInputAudioBalance
import com.rejeq.ktobs.request.inputs.setInputAudioMonitorType
import com.rejeq.ktobs.request.inputs.setInputAudioSyncOffset
import com.rejeq.ktobs.request.inputs.setInputMute
import com.rejeq.ktobs.request.inputs.setInputVolume
import com.rejeq.ktobs.request.mediainputs.getMediaInputStatus
import com.rejeq.ktobs.request.mediainputs.setMediaInputCursor
import com.rejeq.ktobs.request.mediainputs.triggerMediaInputAction
import com.rejeq.ktobs.request.outputs.getVirtualCamStatus
import com.rejeq.ktobs.request.outputs.toggleVirtualCam
import com.rejeq.ktobs.request.record.getRecordStatus
import com.rejeq.ktobs.request.record.startRecord
import com.rejeq.ktobs.request.record.stopRecord
import com.rejeq.ktobs.request.sceneitems.getSceneItemEnabled
import com.rejeq.ktobs.request.sceneitems.getSceneItemList
import com.rejeq.ktobs.request.sceneitems.setSceneItemEnabled
import com.rejeq.ktobs.request.scenes.getCurrentPreviewScene
import com.rejeq.ktobs.request.scenes.getCurrentProgramScene
import com.rejeq.ktobs.request.scenes.getSceneList
import com.rejeq.ktobs.request.scenes.setCurrentPreviewScene
import com.rejeq.ktobs.request.scenes.setCurrentProgramScene
import com.rejeq.ktobs.request.sources.getSourceScreenshot
import com.rejeq.ktobs.request.stream.getStreamStatus
import com.rejeq.ktobs.request.stream.startStream
import com.rejeq.ktobs.request.stream.stopStream
import com.rejeq.ktobs.request.transitions.getCurrentSceneTransition
import com.rejeq.ktobs.request.transitions.getSceneTransitionList
import com.rejeq.ktobs.request.transitions.setCurrentSceneTransition
import com.rejeq.ktobs.request.transitions.setCurrentSceneTransitionDuration
import com.rejeq.ktobs.request.transitions.triggerStudioModeTransition
import com.rejeq.ktobs.request.ui.getStudioModeEnabled
import com.rejeq.ktobs.request.ui.setStudioModeEnabled
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsNotConnectedException
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.ScreenshotRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

private const val MAX_CONCURRENT_REQUESTS = 8
private const val EVENTS_BUFFER = 64
private const val METERS_BUFFER = 4

private const val DATA_URI_PREFIX = "base64,"

/** OBS `imageData` is a data URI ("data:image/jpeg;base64,<payload>"). */
internal fun decodeImageData(imageData: String): ByteArray {
    val payload = imageData.substringAfter(DATA_URI_PREFIX, imageData)
    return java.util.Base64.getDecoder().decode(payload)
}

private fun defaultHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        install(WebSockets) {
            contentConverter =
                KotlinxWebsocketSerializationConverter(Json { ignoreUnknownKeys = true })
            pingIntervalMillis = 20_000
        }
    }

/**
 * ktobs/Ktor-backed [ObsClient]. Delegates the session state machine and reconnect loop to
 * [ObsSessionRunner] and event fan-out to [ObsEventDispatcher]; this class keeps the public flows,
 * the request lane (semaphore + error mapping) and the request surface. All ktobs/Ktor types stay
 * inside this module.
 */
@Suppress("TooManyFunctions") // the OBS request surface is intentionally broad
internal class KtobsObsClient(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient = defaultHttpClient(),
    eventSubs: ObsEventSubs = ObsEventSubs.All + ObsEventSubs.InputVolumeMeters,
    backoffMillis: (attempt: Int) -> Long = ::exponentialBackoffMillis,
    connectTimeoutMs: Long = 10_000,
) : ObsClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events =
        MutableSharedFlow<ObsEvent>(
            extraBufferCapacity = EVENTS_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val events: SharedFlow<ObsEvent> = _events.asSharedFlow()

    private val _volumeMeters =
        MutableSharedFlow<List<VolumeMeterReading>>(
            extraBufferCapacity = METERS_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = _volumeMeters.asSharedFlow()

    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val eventDispatcher = ObsEventDispatcher(_events, _volumeMeters)
    private val sessionRunner =
        ObsSessionRunner(
                scope,
                httpClient,
                _connectionState,
                requestSemaphore,
                eventDispatcher::dispatch,
            )
            .apply {
                this.eventSubs = eventSubs
                this.backoffMillis = backoffMillis
                this.connectTimeoutMs = connectTimeoutMs
            }

    init {
        // A client is scoped, including the engine threads and connection pool.
        scope.coroutineContext[Job]?.invokeOnCompletion { httpClient.close() }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override suspend fun connect(host: String, port: Int, password: String?) =
        sessionRunner.connect(host, port, password)

    override suspend fun disconnect() = sessionRunner.disconnect()

    // ── Requests ────────────────────────────────────────────────────────────

    private suspend fun <T> request(block: suspend (ObsSession) -> T): T =
        requestSemaphore.withPermit {
            val s =
                sessionRunner.session?.takeIf { _connectionState.value is ConnectionState.Ready }
                    ?: throw ObsNotConnectedException()
            try {
                block(s)
            } catch (e: ObsRequestException) {
                throw ObsRequestFailedException(e.type, e.code.toString(), e.message, e)
            }
        }

    override suspend fun getVersion() = request { it.getVersion().toDomain() }

    override suspend fun getStats() = request { it.getStats().toDomain() }

    override suspend fun getSceneList() = request { it.getSceneList().toDomain() }

    override suspend fun getCurrentProgramScene() = request {
        it.getCurrentProgramScene().toDomain()
    }

    override suspend fun setCurrentProgramScene(sceneName: String) = request {
        it.setCurrentProgramScene(name = sceneName)
    }

    override suspend fun getSceneItemList(sceneName: String) = request { s ->
        s.getSceneItemList(sceneName = sceneName).map { it.toDomain() }
    }

    override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = request {
        it.getSceneItemEnabled(sceneName = sceneName, sceneItemId = sceneItemId)
    }

    override suspend fun getSpecialInputs() = request { it.getSpecialInputs().toDomain() }

    override suspend fun getInputMute(inputName: String) = request {
        it.getInputMute(inputName = inputName)
    }

    override suspend fun setInputMute(inputName: String, muted: Boolean) = request {
        it.setInputMute(name = inputName, muted = muted)
    }

    override suspend fun getInputVolume(inputName: String) = request {
        it.getInputVolume(name = inputName).mul
    }

    override suspend fun setInputVolume(inputName: String, volumeMul: Double) = request {
        it.setInputVolume(name = inputName, mul = volumeMul)
    }

    override suspend fun getStreamStatus() = request { it.getStreamStatus().toDomain() }

    override suspend fun startStream() = request { it.startStream() }

    override suspend fun stopStream() = request { it.stopStream() }

    override suspend fun getRecordStatus() = request { it.getRecordStatus().toDomain() }

    override suspend fun startRecord() = request { it.startRecord() }

    override suspend fun stopRecord() {
        request { it.stopRecord() }
    }

    override suspend fun getProfileList() = request { it.getProfileList().toDomain() }

    override suspend fun setCurrentProfile(profileName: String) = request {
        it.setCurrentProfile(profileName)
    }

    override suspend fun getSceneCollectionList() = request {
        it.getSceneCollectionList().toDomain()
    }

    override suspend fun setCurrentSceneCollection(collectionName: String) = request {
        it.setCurrentSceneCollection(collectionName)
    }

    // ── Studio mode & transitions (M6) ──────────────────────────────────────

    override suspend fun getStudioModeEnabled() = request { it.getStudioModeEnabled() }

    override suspend fun setStudioModeEnabled(enabled: Boolean) = request {
        it.setStudioModeEnabled(enabled)
    }

    override suspend fun getCurrentPreviewScene() = request {
        it.getCurrentPreviewScene().toDomain()
    }

    override suspend fun setCurrentPreviewScene(sceneName: String) = request {
        it.setCurrentPreviewScene(name = sceneName)
    }

    override suspend fun triggerStudioModeTransition() = request {
        it.triggerStudioModeTransition()
    }

    override suspend fun getSceneTransitionList() = request {
        it.getSceneTransitionList().toDomain()
    }

    override suspend fun getCurrentSceneTransition() = request {
        it.getCurrentSceneTransition().toDomain()
    }

    override suspend fun setCurrentSceneTransition(transitionName: String) = request {
        it.setCurrentSceneTransition(transitionName)
    }

    override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = request {
        it.setCurrentSceneTransitionDuration(durationMs)
    }

    override suspend fun getSourceScreenshot(screenshot: ScreenshotRequest): ByteArray = request {
        val response =
            it.getSourceScreenshot(
                /* sourceName = */ screenshot.sourceName,
                /* sourceUuid = */ null,
                /* imageFormat = */ screenshot.format,
                /* imageWidth = */ screenshot.width,
                /* imageHeight = */ screenshot.height,
                /* imageCompressionQuality = */ screenshot.compressionQuality,
            )
        decodeImageData(response.imageData)
    }

    // ── Power features (M7) ─────────────────────────────────────────────────

    override suspend fun getVirtualCamStatus() = request { it.getVirtualCamStatus() }

    override suspend fun toggleVirtualCam() = request { it.toggleVirtualCam() }

    override suspend fun getMediaInputStatus(inputName: String) = request {
        it.getMediaInputStatus(inputName = inputName).toDomain()
    }

    override suspend fun setMediaInputCursor(inputName: String, cursorMs: Long) = request {
        it.setMediaInputCursor(inputName = inputName, mediaCursor = cursorMs)
    }

    override suspend fun triggerMediaInputAction(inputName: String, action: MediaActionKind) =
        request {
            it.triggerMediaInputAction(inputName = inputName, mediaAction = action.toKtobs())
        }

    override suspend fun setSceneItemEnabled(
        sceneName: String,
        sceneItemId: Int,
        enabled: Boolean,
    ) = request {
        it.setSceneItemEnabled(
            sceneName = sceneName,
            sceneUuid = null,
            sceneItemId = sceneItemId,
            enabled = enabled,
        )
    }

    override suspend fun getInputAudioBalance(inputName: String) = request {
        it.getInputAudioBalance(inputName = inputName)
    }

    override suspend fun setInputAudioBalance(inputName: String, balance: Double) = request {
        it.setInputAudioBalance(name = inputName, balance = balance)
    }

    override suspend fun getInputAudioSyncOffset(inputName: String) = request {
        it.getInputAudioSyncOffset(inputName = inputName)
    }

    override suspend fun setInputAudioSyncOffset(inputName: String, offsetMs: Int) = request {
        it.setInputAudioSyncOffset(name = inputName, offset = offsetMs)
    }

    override suspend fun getInputAudioMonitorType(inputName: String) = request {
        it.getInputAudioMonitorType(inputName = inputName).toDomain()
    }

    override suspend fun setInputAudioMonitorType(inputName: String, monitorType: MonitorTypeKind) =
        request {
            it.setInputAudioMonitorType(name = inputName, type = monitorType.toKtobs())
        }
}
