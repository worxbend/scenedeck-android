package com.scenedeck.android.core.obs.internal

import com.rejeq.ktobs.AuthError
import com.rejeq.ktobs.EventOpCode
import com.rejeq.ktobs.ObsAuthException
import com.rejeq.ktobs.ObsCloseReason
import com.rejeq.ktobs.ObsEventSubs
import com.rejeq.ktobs.ObsRequestException
import com.rejeq.ktobs.ObsSession
import com.rejeq.ktobs.event.config.CurrentProfileChangedEvent
import com.rejeq.ktobs.event.config.CurrentProfileChangedEventData
import com.rejeq.ktobs.event.config.CurrentSceneCollectionChangedEvent
import com.rejeq.ktobs.event.config.CurrentSceneCollectionChangedEventData
import com.rejeq.ktobs.event.config.ProfileListChangedEvent
import com.rejeq.ktobs.event.config.ProfileListChangedEventData
import com.rejeq.ktobs.event.config.SceneCollectionListChangedEvent
import com.rejeq.ktobs.event.config.SceneCollectionListChangedEventData
import com.rejeq.ktobs.event.inputs.InputAudioBalanceChangedEvent
import com.rejeq.ktobs.event.inputs.InputAudioBalanceChangedEventData
import com.rejeq.ktobs.event.inputs.InputAudioMonitorTypeChangedEvent
import com.rejeq.ktobs.event.inputs.InputAudioMonitorTypeChangedEventData
import com.rejeq.ktobs.event.inputs.InputAudioSyncOffsetChangedEvent
import com.rejeq.ktobs.event.inputs.InputAudioSyncOffsetChangedEventData
import com.rejeq.ktobs.event.inputs.InputCreatedEvent
import com.rejeq.ktobs.event.inputs.InputCreatedEventData
import com.rejeq.ktobs.event.inputs.InputMuteStateChangedEvent
import com.rejeq.ktobs.event.inputs.InputMuteStateChangedEventData
import com.rejeq.ktobs.event.inputs.InputNameChangedEvent
import com.rejeq.ktobs.event.inputs.InputNameChangedEventData
import com.rejeq.ktobs.event.inputs.InputRemovedEvent
import com.rejeq.ktobs.event.inputs.InputRemovedEventData
import com.rejeq.ktobs.event.inputs.InputVolumeChangedEvent
import com.rejeq.ktobs.event.inputs.InputVolumeChangedEventData
import com.rejeq.ktobs.event.inputs.InputVolumeMetersEvent
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackEndedEvent
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackEndedEventData
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackStartedEvent
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackStartedEventData
import com.rejeq.ktobs.event.outputs.RecordStateChangedEvent
import com.rejeq.ktobs.event.outputs.RecordStateChangedEventData
import com.rejeq.ktobs.event.outputs.ReplayBufferSavedEvent
import com.rejeq.ktobs.event.outputs.ReplayBufferSavedEventData
import com.rejeq.ktobs.event.outputs.ReplayBufferStateChangedEvent
import com.rejeq.ktobs.event.outputs.ReplayBufferStateChangedEventData
import com.rejeq.ktobs.event.outputs.StreamStateChangedEvent
import com.rejeq.ktobs.event.outputs.StreamStateChangedEventData
import com.rejeq.ktobs.event.outputs.VirtualcamStateChangedEvent
import com.rejeq.ktobs.event.outputs.VirtualcamStateChangedEventData
import com.rejeq.ktobs.event.sceneitems.SceneItemEnableStateChangedEvent
import com.rejeq.ktobs.event.sceneitems.SceneItemEnableStateChangedEventData
import com.rejeq.ktobs.event.scenes.CurrentPreviewSceneChangedEvent
import com.rejeq.ktobs.event.scenes.CurrentPreviewSceneChangedEventData
import com.rejeq.ktobs.event.scenes.CurrentProgramSceneChangedEvent
import com.rejeq.ktobs.event.scenes.CurrentProgramSceneChangedEventData
import com.rejeq.ktobs.event.scenes.SceneCreatedEvent
import com.rejeq.ktobs.event.scenes.SceneCreatedEventData
import com.rejeq.ktobs.event.scenes.SceneListChangedEvent
import com.rejeq.ktobs.event.scenes.SceneListChangedEventData
import com.rejeq.ktobs.event.scenes.SceneNameChangedEvent
import com.rejeq.ktobs.event.scenes.SceneNameChangedEventData
import com.rejeq.ktobs.event.scenes.SceneRemovedEvent
import com.rejeq.ktobs.event.scenes.SceneRemovedEventData
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionChangedEvent
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionChangedEventData
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionDurationChangedEvent
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionDurationChangedEventData
import com.rejeq.ktobs.event.transitions.SceneTransitionEndedEvent
import com.rejeq.ktobs.event.transitions.SceneTransitionEndedEventData
import com.rejeq.ktobs.event.transitions.SceneTransitionStartedEvent
import com.rejeq.ktobs.event.transitions.SceneTransitionStartedEventData
import com.rejeq.ktobs.event.ui.StudioModeStateChangedEvent
import com.rejeq.ktobs.event.ui.StudioModeStateChangedEventData
import com.rejeq.ktobs.ktor.ObsSessionBuilder
import com.rejeq.ktobs.ktor.runDefaultReceiver
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
import com.rejeq.ktobs.request.outputs.getLastReplayBufferReplay
import com.rejeq.ktobs.request.outputs.getReplayBufferStatus
import com.rejeq.ktobs.request.outputs.getVirtualCamStatus
import com.rejeq.ktobs.request.outputs.saveReplayBuffer
import com.rejeq.ktobs.request.outputs.toggleReplayBuffer
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
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsNotConnectedException
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.internal.protocol.InputVolumeMetersPayload
import com.scenedeck.android.core.obs.internal.protocol.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

private const val BASE_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5
private const val CLOSE_REASON_TIMEOUT_MS = 1_000L
private const val MAX_CONCURRENT_REQUESTS = 8
private const val EVENTS_BUFFER = 64
private const val METERS_BUFFER = 4

/** Exponential backoff: 1 s, 2 s, 4 s, 8 s, 16 s, then capped at 30 s. */
internal fun exponentialBackoffMillis(attempt: Int): Long {
    require(attempt >= 1) { "attempt must be >= 1, was $attempt" }
    val shift = (attempt - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
    return minOf(BASE_BACKOFF_MS shl shift, MAX_BACKOFF_MS)
}

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

private class SessionLostException(
    cause: Throwable?,
    val closeReason: ObsCloseReason?,
) : Exception("OBS WebSocket session ended unexpectedly", cause)

private data class SessionEnd(
    val failure: Throwable?,
    val closeReason: ObsCloseReason?,
)

/**
 * ktobs/Ktor-backed [ObsClient]. Owns the session state machine, reconnect loop, request semaphore
 * and event fan-out. All ktobs/Ktor types stay inside this module.
 */
@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
internal class KtobsObsClient(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient = defaultHttpClient(),
    private val eventSubs: ObsEventSubs = ObsEventSubs.All + ObsEventSubs.InputVolumeMeters,
    private val backoffMillis: (attempt: Int) -> Long = ::exponentialBackoffMillis,
    private val connectTimeoutMs: Long = 10_000,
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
    private val sessionMutex = Mutex()

    @Volatile private var session: ObsSession? = null
    @Volatile private var disconnectRequested = false
    private var sessionJob: Job? = null
    private var closeSignal: CompletableDeferred<Unit>? = null
    private var connectOutcome: CompletableDeferred<Unit>? = null

    init {
        // A client is scoped, including the engine threads and connection pool.
        scope.coroutineContext[Job]?.invokeOnCompletion { httpClient.close() }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override suspend fun connect(host: String, port: Int, password: String?) {
        val outcome = CompletableDeferred<Unit>()
        sessionMutex.withLock {
            disconnectLocked()
            disconnectRequested = false
            connectOutcome = outcome
            sessionJob =
                scope
                    .launch { sessionLoop(host, port, password) }
                    .also { job ->
                        job.invokeOnCompletion { cause ->
                            if (cause != null) {
                                _connectionState.value = ConnectionState.Disconnected
                                outcome.completeExceptionally(cause)
                            }
                        }
                    }
        }
        // Suspends until the first attempt reaches Ready or Failed.
        try {
            outcome.await()
        } catch (e: CancellationException) {
            // Cancelling a pending connect must not leave an orphan OBS session.
            withContext(NonCancellable) {
                sessionMutex.withLock {
                    if (connectOutcome === outcome) disconnectLocked()
                }
            }
            throw e
        }
    }

    override suspend fun disconnect() {
        sessionMutex.withLock { disconnectLocked() }
    }

    private suspend fun disconnectLocked() {
        disconnectRequested = true
        connectOutcome?.complete(Unit)
        closeSignal?.complete(Unit)
        val job = sessionJob
        sessionJob = null
        job?.cancelAndJoin()
        session = null
        _connectionState.value = ConnectionState.Disconnected
    }

    @Suppress("ReturnCount") // terminal-state early exits keep the state machine readable
    private suspend fun sessionLoop(host: String, port: Int, password: String?) {
        var attempt = 0
        var connected = false
        _connectionState.value = ConnectionState.Connecting
        while (currentCoroutineContext().isActive) {
            try {
                runSession(host, port, password) {
                    connected = true
                    attempt = 0
                    connectOutcome?.complete(Unit)
                }
                // Normal return: disconnect() was requested.
                break
            } catch (e: TimeoutCancellationException) {
                // Timeout is a retryable socket failure, not caller cancellation.
                if (recordSessionFailure(e, connected)) return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (recordSessionFailure(e, connected)) return
            }
            attempt++
            _connectionState.value = ConnectionState.Reconnecting(attempt)
            delay(backoffMillis(attempt))
        }
    }

    /** Initial and authentication failures are terminal; established socket loss retries. */
    private fun recordSessionFailure(failure: Exception, connected: Boolean): Boolean {
        if (connected && failure !is ObsAuthException) return false
        _connectionState.value = ConnectionState.Failed(failure.toConnectionError())
        connectOutcome?.complete(Unit)
        return true
    }

    private fun newSessionBuilder(
        host: String,
        port: Int,
        password: String?,
        socketEnded: CompletableDeferred<SessionEnd>,
    ): ObsSessionBuilder =
        ObsSessionBuilder(httpClient).apply {
            this.host = host
            this.port = port
            this.password = password
            this.eventSubs = this@KtobsObsClient.eventSubs
            this.onEvent = { event -> dispatchEvent(event) }
            this.receiver = { s ->
                var failure: Throwable? = null
                var closeReason: ObsCloseReason? = null
                try {
                    runDefaultReceiver(s)
                } catch (e: ClosedReceiveChannelException) {
                    failure = e
                    closeReason =
                        withTimeoutOrNull(CLOSE_REASON_TIMEOUT_MS) { s.ws.getCloseReason() }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Exception) {
                    failure = t
                    throw t
                } finally {
                    socketEnded.complete(SessionEnd(failure, closeReason))
                }
            }
        }

    /**
     * Opens the WebSocket, performs Hello/Identify, then suspends until the socket ends. Returns
     * normally only when [disconnect] was requested; throws on unexpected loss. [onReady] fires
     * once the session is fully up.
     */
    private suspend fun runSession(
        host: String,
        port: Int,
        password: String?,
        onReady: () -> Unit,
    ): Unit = coroutineScope {
        _connectionState.value = ConnectionState.Identifying

        val sessionDeferred = CompletableDeferred<ObsSession>()
        val socketEnded = CompletableDeferred<SessionEnd>()
        val closeSignal = CompletableDeferred<Unit>()
        this@KtobsObsClient.closeSignal = closeSignal
        val builder = newSessionBuilder(host, port, password, socketEnded)

        val connectJob = launch {
            try {
                builder.connect {
                    sessionDeferred.complete(this)
                    closeSignal.await()
                }
                socketEnded.complete(SessionEnd(failure = null, closeReason = null))
            } catch (e: CancellationException) {
                sessionDeferred.completeExceptionally(e)
                socketEnded.complete(SessionEnd(failure = null, closeReason = null))
                throw e
            } catch (t: Exception) {
                sessionDeferred.completeExceptionally(t)
                socketEnded.complete(SessionEnd(failure = t, closeReason = null))
            }
        }

        try {
            val s = withTimeout(connectTimeoutMs) { sessionDeferred.await() }
            session = s
            // Prove the request lane and collect session info in one round-trip.
            val version =
                withTimeout(connectTimeoutMs) {
                    requestSemaphore.withPermit { s.getVersion().toDomain() }
                }
            _connectionState.value = ConnectionState.Ready(version)
            onReady()

            val end = socketEnded.await()
            if (!disconnectRequested) {
                throw SessionLostException(end.failure, end.closeReason)
            }
        } finally {
            session = null
            this@KtobsObsClient.closeSignal = null
            connectJob.cancel()
        }
    }

    // ── Requests ────────────────────────────────────────────────────────────

    private suspend fun <T> request(block: suspend (ObsSession) -> T): T =
        requestSemaphore.withPermit {
            val s =
                session?.takeIf { _connectionState.value is ConnectionState.Ready }
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

    override suspend fun getSourceScreenshot(
        sourceName: String,
        format: String,
        compressionQuality: Int,
        width: Int?,
        height: Int?,
    ): ByteArray = request {
        val response =
            it.getSourceScreenshot(
                /* sourceName = */ sourceName,
                /* sourceUuid = */ null,
                /* imageFormat = */ format,
                /* imageWidth = */ width,
                /* imageHeight = */ height,
                /* imageCompressionQuality = */ compressionQuality,
            )
        decodeImageData(response.imageData)
    }

    // ── Power features (M7) ─────────────────────────────────────────────────

    override suspend fun getVirtualCamStatus() = request { it.getVirtualCamStatus() }

    override suspend fun toggleVirtualCam() = request { it.toggleVirtualCam() }

    override suspend fun getReplayBufferStatus() = request { it.getReplayBufferStatus() }

    override suspend fun toggleReplayBuffer() = request { it.toggleReplayBuffer() }

    override suspend fun saveReplayBuffer() = request { it.saveReplayBuffer() }

    override suspend fun getLastReplayBufferReplay() = request { it.getLastReplayBufferReplay() }

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

    // ── Event fan-out ───────────────────────────────────────────────────────

    private fun ObsSession.dispatchEvent(event: EventOpCode) {
        // A malformed event must never kill the receiver loop.
        runCatching {
            when (event.eventType) {
                InputVolumeMetersEvent -> dispatchMeters(event)
                CurrentProgramSceneChangedEvent,
                SceneListChangedEvent,
                SceneCreatedEvent,
                SceneRemovedEvent,
                SceneNameChangedEvent,
                SceneItemEnableStateChangedEvent -> dispatchSceneEvent(event)
                InputCreatedEvent,
                InputRemovedEvent,
                InputNameChangedEvent,
                InputMuteStateChangedEvent,
                InputVolumeChangedEvent -> dispatchInputEvent(event)
                StreamStateChangedEvent,
                RecordStateChangedEvent,
                VirtualcamStateChangedEvent,
                ReplayBufferStateChangedEvent,
                ReplayBufferSavedEvent -> dispatchOutputEvent(event)
                MediaInputPlaybackStartedEvent,
                MediaInputPlaybackEndedEvent,
                InputAudioBalanceChangedEvent,
                InputAudioSyncOffsetChangedEvent,
                InputAudioMonitorTypeChangedEvent -> dispatchInputEvent(event)
                CurrentProfileChangedEvent,
                ProfileListChangedEvent,
                CurrentSceneCollectionChangedEvent,
                SceneCollectionListChangedEvent,
                StudioModeStateChangedEvent -> dispatchConfigEvent(event)
                CurrentPreviewSceneChangedEvent,
                SceneTransitionStartedEvent,
                SceneTransitionEndedEvent,
                CurrentSceneTransitionChangedEvent,
                CurrentSceneTransitionDurationChangedEvent -> dispatchStudioEvent(event)
            }
        }
    }

    private fun ObsSession.dispatchMeters(event: EventOpCode) {
        val data = event.eventData ?: return
        val payload = ws.json.decodeFromJsonElement<InputVolumeMetersPayload>(data)
        _volumeMeters.tryEmit(payload.toDomain())
    }

    private fun ObsSession.dispatchSceneEvent(event: EventOpCode) {
        val domain: ObsEvent =
            when (event.eventType) {
                CurrentProgramSceneChangedEvent ->
                    ObsEvent.CurrentProgramSceneChanged(
                        event.get<CurrentProgramSceneChangedEventData>().sceneName
                    )
                SceneListChangedEvent ->
                    ObsEvent.SceneListChanged(
                        event.get<SceneListChangedEventData>().scenes.map {
                            SceneSummary(name = it.name, index = it.index)
                        }
                    )
                SceneCreatedEvent ->
                    event.get<SceneCreatedEventData>().let {
                        ObsEvent.SceneCreated(it.sceneName, it.isGroup)
                    }
                SceneRemovedEvent ->
                    ObsEvent.SceneRemoved(event.get<SceneRemovedEventData>().sceneName)
                SceneNameChangedEvent ->
                    event.get<SceneNameChangedEventData>().let {
                        ObsEvent.SceneNameChanged(it.oldSceneName, it.sceneName)
                    }
                SceneItemEnableStateChangedEvent ->
                    event.get<SceneItemEnableStateChangedEventData>().let {
                        ObsEvent.SceneItemEnableStateChanged(
                            it.sceneName,
                            it.sceneItemId.toInt(),
                            it.sceneItemEnabled,
                        )
                    }
                else -> return
            }
        _events.tryEmit(domain)
    }

    @Suppress("CyclomaticComplexMethod") // flat event-type dispatch table
    private fun ObsSession.dispatchInputEvent(event: EventOpCode) {
        val domain: ObsEvent =
            when (event.eventType) {
                InputCreatedEvent ->
                    event.get<InputCreatedEventData>().let {
                        ObsEvent.InputCreated(it.inputName, it.inputKind)
                    }
                InputRemovedEvent ->
                    ObsEvent.InputRemoved(event.get<InputRemovedEventData>().inputName)
                InputNameChangedEvent ->
                    event.get<InputNameChangedEventData>().let {
                        ObsEvent.InputNameChanged(it.oldInputName, it.inputName)
                    }
                InputMuteStateChangedEvent ->
                    event.get<InputMuteStateChangedEventData>().let {
                        ObsEvent.InputMuteStateChanged(it.inputName, it.inputMuted)
                    }
                InputVolumeChangedEvent ->
                    event.get<InputVolumeChangedEventData>().let {
                        ObsEvent.InputVolumeChanged(
                            it.inputName,
                            it.inputVolumeMul,
                            it.inputVolumeDb,
                        )
                    }
                MediaInputPlaybackStartedEvent ->
                    ObsEvent.MediaInputPlaybackStarted(
                        event.get<MediaInputPlaybackStartedEventData>().inputName
                    )
                MediaInputPlaybackEndedEvent ->
                    ObsEvent.MediaInputPlaybackEnded(
                        event.get<MediaInputPlaybackEndedEventData>().inputName
                    )
                InputAudioBalanceChangedEvent ->
                    event.get<InputAudioBalanceChangedEventData>().let {
                        ObsEvent.InputAudioBalanceChanged(it.inputName, it.inputAudioBalance)
                    }
                InputAudioSyncOffsetChangedEvent ->
                    event.get<InputAudioSyncOffsetChangedEventData>().let {
                        ObsEvent.InputAudioSyncOffsetChanged(it.inputName, it.inputAudioSyncOffset)
                    }
                InputAudioMonitorTypeChangedEvent ->
                    event.get<InputAudioMonitorTypeChangedEventData>().let {
                        ObsEvent.InputAudioMonitorTypeChanged(
                            it.inputName,
                            it.monitorType.toDomain(),
                        )
                    }
                else -> return
            }
        _events.tryEmit(domain)
    }

    private fun ObsSession.dispatchOutputEvent(event: EventOpCode) {
        val domain: ObsEvent =
            when (event.eventType) {
                StreamStateChangedEvent ->
                    event.get<StreamStateChangedEventData>().let {
                        ObsEvent.StreamStateChanged(it.outputActive, it.outputState)
                    }
                VirtualcamStateChangedEvent ->
                    event.get<VirtualcamStateChangedEventData>().let {
                        ObsEvent.VirtualcamStateChanged(it.outputActive, it.outputState)
                    }
                ReplayBufferStateChangedEvent ->
                    event.get<ReplayBufferStateChangedEventData>().let {
                        ObsEvent.ReplayBufferStateChanged(it.outputActive, it.outputState)
                    }
                ReplayBufferSavedEvent ->
                    ObsEvent.ReplayBufferSaved(
                        event.get<ReplayBufferSavedEventData>().savedReplayPath
                    )
                RecordStateChangedEvent ->
                    event.get<RecordStateChangedEventData>().let {
                        ObsEvent.RecordStateChanged(it.outputActive, it.outputState, it.outputPath)
                    }
                else -> return
            }
        _events.tryEmit(domain)
    }

    private fun ObsSession.dispatchStudioEvent(event: EventOpCode) {
        val domain: ObsEvent =
            when (event.eventType) {
                CurrentPreviewSceneChangedEvent ->
                    ObsEvent.CurrentPreviewSceneChanged(
                        event.get<CurrentPreviewSceneChangedEventData>().sceneName
                    )
                SceneTransitionStartedEvent ->
                    ObsEvent.SceneTransitionStarted(
                        event.get<SceneTransitionStartedEventData>().transitionName
                    )
                SceneTransitionEndedEvent ->
                    ObsEvent.SceneTransitionEnded(
                        event.get<SceneTransitionEndedEventData>().transitionName
                    )
                CurrentSceneTransitionChangedEvent ->
                    ObsEvent.CurrentSceneTransitionChanged(
                        event.get<CurrentSceneTransitionChangedEventData>().transitionName
                    )
                CurrentSceneTransitionDurationChangedEvent ->
                    ObsEvent.CurrentSceneTransitionDurationChanged(
                        event
                            .get<CurrentSceneTransitionDurationChangedEventData>()
                            .transitionDuration
                    )
                else -> return
            }
        _events.tryEmit(domain)
    }

    private fun ObsSession.dispatchConfigEvent(event: EventOpCode) {
        val domain: ObsEvent =
            when (event.eventType) {
                CurrentProfileChangedEvent ->
                    ObsEvent.CurrentProfileChanged(
                        event.get<CurrentProfileChangedEventData>().profileName
                    )
                ProfileListChangedEvent ->
                    ObsEvent.ProfileListChanged(event.get<ProfileListChangedEventData>().profiles)
                CurrentSceneCollectionChangedEvent ->
                    ObsEvent.CurrentSceneCollectionChanged(
                        event.get<CurrentSceneCollectionChangedEventData>().sceneCollectionName
                    )
                SceneCollectionListChangedEvent ->
                    ObsEvent.SceneCollectionListChanged(
                        event.get<SceneCollectionListChangedEventData>().sceneCollections
                    )
                StudioModeStateChangedEvent ->
                    ObsEvent.StudioModeStateChanged(
                        event.get<StudioModeStateChangedEventData>().studioModeEnabled
                    )
                else -> return
            }
        _events.tryEmit(domain)
    }

    // ── Error mapping ───────────────────────────────────────────────────────

    private fun ObsAuthException.toConnectionError(): ConnectionError =
        when (val k = kind) {
            is AuthError.InvalidRpc ->
                ConnectionError.Protocol("Unsupported OBS RPC version ${k.endpointVersion}")
            is AuthError.Unexpected -> ConnectionError.Auth(k.reason?.message)
            else -> ConnectionError.Auth(message)
        }

    private fun Throwable.toConnectionError(): ConnectionError =
        when (this) {
            is ObsAuthException -> toConnectionError()
            is SessionLostException ->
                closeReason?.let { ConnectionError.Closed(it.code.toInt(), it.message) }
                    ?: ConnectionError.Unreachable(cause ?: this)
            is SerializationException ->
                ConnectionError.Protocol(message ?: "Protocol decode error")
            is TimeoutCancellationException -> ConnectionError.Unreachable(this)
            else -> ConnectionError.Unreachable(this)
        }
}
