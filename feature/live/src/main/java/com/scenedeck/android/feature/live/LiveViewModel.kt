package com.scenedeck.android.feature.live

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.OutputAction
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.OutputSafetyGate
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.ScreenshotRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Which transport action needs a confirmation dialog. */
enum class TransportConfirmation {
    START_STREAM,
    STOP_STREAM,
    START_RECORD,
    STOP_RECORD,
}

@HiltViewModel
@Suppress("TooManyFunctions") // deck + transport + studio-mode intents
class LiveViewModel
@Inject
constructor(
    private val obsState: ObsStateRepository,
    private val settings: SettingsRepository,
    private val client: ObsClient,
    screenshots: ScreenshotRepository,
    stats: StatsRepository,
) : ViewModel() {

    /** Scene thumbnails for deck cards (throttled inside the repository). */
    val thumbnails: StateFlow<Map<String, Bitmap>> = screenshots.thumbnails

    val deckState: StateFlow<DeckState> = obsState.deckState
    val telemetry: StateFlow<Telemetry> = stats.telemetry

    val outputSafety: StateFlow<OutputSafety> =
        settings.settings
            .map {
                OutputSafety(
                    confirmStartStream = it.confirmStartStream,
                    confirmStopStream = it.confirmStopStream,
                    confirmStartRecord = it.confirmStartRecord,
                    confirmStopRecord = it.confirmStopRecord,
                )
            }
            // Eagerly: this value gates transport actions via .value — the UI never
            // subscribes to it, so WhileSubscribed would keep it at the default forever.
            .stateIn(viewModelScope, SharingStarted.Eagerly, OutputSafety())

    val hapticsEnabled: StateFlow<Boolean> =
        settings.settings
            .map { it.haptics }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** "Scene previews" setting (default ON): gate for the screenshot pipeline. */
    val previewsEnabled: StateFlow<Boolean> =
        settings.settings
            .map { it.scenePreviewsEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val motionLevel: StateFlow<MotionLevel> =
        settings.settings
            .map {
                coroutineResult { MotionLevel.valueOf(it.motionLevel) }
                    .getOrDefault(MotionLevel.FULL)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MotionLevel.FULL)

    /** Scene whose switch is taking > ~300 ms (per-card pending spinner). */
    private val _pendingScene = MutableStateFlow<String?>(null)
    val pendingScene: StateFlow<String?> = _pendingScene.asStateFlow()
    private var pendingWatch: Job? = null

    private val confirmationSession = OutputConfirmationSession()
    private val _confirmation = MutableStateFlow<TransportConfirmation?>(null)
    val confirmation: StateFlow<TransportConfirmation?> = _confirmation.asStateFlow()

    /**
     * One-shot user-facing errors (OBS request failures) for the snackbar. Small replay so a
     * failure emitted during collector teardown (e.g. rotation) or two failures in the same frame
     * are not silently lost.
     */
    private val _errors = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // ── Deck ────────────────────────────────────────────────────────────────

    /** Tap semantics: studio mode → switch preview; otherwise → switch program. */
    @Suppress("ReturnCount") // guard clauses keep tap semantics readable
    fun onSceneTap(sceneName: String) {
        if (deckState.value.connectionState !is ConnectionState.Ready) return
        if (deckState.value.studioMode) {
            if (deckState.value.previewScene == sceneName) return
            viewModelScope.launch {
                coroutineResult { obsState.setCurrentPreviewScene(sceneName) }
                    .onFailure { _errors.tryEmit("Couldn't preview $sceneName") }
            }
            return
        }
        if (deckState.value.currentProgramScene == sceneName) return
        pendingWatch?.cancel()
        pendingWatch = viewModelScope.launch {
            val showSpinner = launch {
                delay(PENDING_SPINNER_DELAY_MS)
                _pendingScene.value = sceneName
            }
            try {
                coroutineResult { obsState.setCurrentProgramScene(sceneName) }
                    .onFailure { _errors.tryEmit("Couldn't switch to $sceneName") }
                // LAN is fast: wait for the change event, then hide the spinner.
                withTimeoutOrNull(PENDING_TIMEOUT_MS) {
                    deckState.first { it.currentProgramScene == sceneName }
                }
            } finally {
                showSpinner.cancel()
                if (_pendingScene.value == sceneName) _pendingScene.value = null
            }
        }
    }

    fun saveSceneMeta(
        sceneName: String,
        primary: Boolean,
        accentColorArgb: Long?,
        iconName: String?,
    ) {
        viewModelScope.launch {
            coroutineResult {
                obsState.updateSceneMeta(
                    sceneName = sceneName,
                    role = if (primary) SceneRole.PRIMARY else SceneRole.SECONDARY,
                    accentColorArgb = accentColorArgb,
                    iconName = iconName,
                )
            }
                .onFailure { _errors.tryEmit("Couldn't save scene details") }
        }
    }

    // ── Virtual camera (M7) ────────────────────────────────────

    // ── Scene item visibility (M7) ──────────────────────────────────────────

    /** Bumped on SceneItemEnableStateChanged so open sheets refetch. */
    private val _sceneItemsVersion = MutableStateFlow(0)
    val sceneItemsVersion: StateFlow<Int> = _sceneItemsVersion.asStateFlow()

    suspend fun loadSceneItems(
        sceneName: String
    ): List<com.scenedeck.android.core.model.SceneItemInfo> = coroutineResult {
        obsState.getSceneItemList(sceneName)
    }
        .getOrDefault(emptyList())

    fun toggleSceneItem(sceneName: String, itemId: Int, enabled: Boolean) {
        viewModelScope.launch {
            coroutineResult { obsState.setSceneItemEnabled(sceneName, itemId, enabled) }
                .onFailure { _errors.tryEmit("Couldn't toggle scene source") }
        }
    }

    fun toggleVirtualCam() {
        viewModelScope.launch {
            coroutineResult { obsState.toggleVirtualCam() }
                .onFailure { _errors.tryEmit("Couldn't toggle virtual cam") }
        }
    }

    // ── Studio mode & transitions (M6) ──────────────────────────────────────

    fun togglePreviews(enabled: Boolean) {
        viewModelScope.launch { settings.setScenePreviewsEnabled(enabled) }
    }

    fun toggleStudioMode(enabled: Boolean) {
        viewModelScope.launch {
            coroutineResult { obsState.setStudioModeEnabled(enabled) }
                .onFailure { _errors.tryEmit("Couldn't toggle studio mode") }
        }
    }

    /** TRANSITION: commits preview → program with the current transition. */
    fun onTransitionClick() {
        viewModelScope.launch {
            coroutineResult { obsState.triggerStudioModeTransition() }
                .onFailure { _errors.tryEmit("Transition failed") }
        }
    }

    /** CUT: instant swap of preview to program. */
    fun onCutClick() {
        val preview = deckState.value.previewScene ?: return
        viewModelScope.launch {
            coroutineResult { obsState.setCurrentProgramScene(preview) }
                .onFailure { _errors.tryEmit("Couldn't cut to $preview") }
        }
    }

    fun selectTransition(transitionName: String) {
        viewModelScope.launch {
            coroutineResult { obsState.setCurrentSceneTransition(transitionName) }
                .onFailure { _errors.tryEmit("Couldn't set transition") }
        }
    }

    private var durationJob: Job? = null

    /** Duration slider writes are debounced (trailing 200 ms). */
    fun setTransitionDuration(durationMs: Int) {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            delay(DURATION_DEBOUNCE_MS)
            coroutineResult { obsState.setCurrentSceneTransitionDuration(durationMs) }
                .onFailure { _errors.tryEmit("Couldn't set transition duration") }
        }
    }

    fun reorderDeck(orderedSceneNames: List<String>) {
        viewModelScope.launch {
            coroutineResult { obsState.reorderDeck(orderedSceneNames) }
                .onFailure { _errors.tryEmit("Couldn't save the scene order") }
        }
    }

    // ── Transport (Output Safety, FEATURE_SPEC §4) ──────────────────────────

    private fun currentOutputSession(): ConnectionState.Ready? =
        (client.connectionState.value as? ConnectionState.Ready)?.takeIf {
            telemetry.value.connection === it
        }

    fun onStreamClick() {
        val session = currentOutputSession() ?: return
        val active = telemetry.value.stream?.active ?: return
        when (OutputSafetyGate.streamAction(active, outputSafety.value)) {
            OutputAction.PERFORM -> toggleStream(active, session)
            OutputAction.REQUIRE_CONFIRMATION -> {
                confirmationSession.bind(session)
                _confirmation.value =
                    if (active) TransportConfirmation.STOP_STREAM
                    else TransportConfirmation.START_STREAM
            }
        }
    }

    fun onRecordClick() {
        val session = currentOutputSession() ?: return
        val active = telemetry.value.record?.active ?: return
        when (OutputSafetyGate.recordAction(active, outputSafety.value)) {
            OutputAction.PERFORM -> toggleRecord(active, session)
            OutputAction.REQUIRE_CONFIRMATION -> {
                confirmationSession.bind(session)
                _confirmation.value =
                    if (active) TransportConfirmation.STOP_RECORD
                    else TransportConfirmation.START_RECORD
            }
        }
    }

    fun confirmPending() {
        val session = client.connectionState.value
        if (!confirmationSession.matches(session) || session !is ConnectionState.Ready) {
            dismissConfirmation()
            return
        }
        val action = confirmation.value
        dismissConfirmation()
        when (action) {
            TransportConfirmation.START_STREAM -> toggleStream(false, session)
            TransportConfirmation.STOP_STREAM -> toggleStream(true, session)
            TransportConfirmation.START_RECORD -> toggleRecord(false, session)
            TransportConfirmation.STOP_RECORD -> toggleRecord(true, session)
            null -> Unit
        }
        _confirmation.value = null
    }

    fun dismissConfirmation() {
        confirmationSession.clear()
        _confirmation.value = null
    }

    private fun toggleStream(active: Boolean, session: ConnectionState.Ready) {
        viewModelScope.launch {
            if (client.connectionState.value !== session) return@launch
            coroutineResult { if (active) client.stopStream() else client.startStream() }
                .onFailure { _errors.tryEmit(it.toTransportMessage("stream")) }
        }
    }

    private fun toggleRecord(active: Boolean, session: ConnectionState.Ready) {
        viewModelScope.launch {
            if (client.connectionState.value !== session) return@launch
            coroutineResult { if (active) client.stopRecord() else client.startRecord() }
                .onFailure { _errors.tryEmit(it.toTransportMessage("recording")) }
        }
    }

    private fun Throwable.toTransportMessage(what: String): String =
        when (this) {
            is ObsRequestFailedException ->
                "OBS refused to control the $what: ${message ?: statusCode}"
            else -> "Couldn't control the $what: ${message ?: "connection error"}"
        }

    init {
        viewModelScope.launch {
            client.connectionState.collect { connection ->
                if (!confirmationSession.matches(connection)) dismissConfirmation()
            }
        }
        viewModelScope.launch {
            client.events.collect { event ->
                if (
                    event is com.scenedeck.android.core.model.ObsEvent.SceneItemEnableStateChanged
                ) {
                    _sceneItemsVersion.value += 1
                }
            }
        }
    }

    private companion object {
        const val PENDING_SPINNER_DELAY_MS = 300L
        const val PENDING_TIMEOUT_MS = 5_000L
        const val DURATION_DEBOUNCE_MS = 200L
    }
}
