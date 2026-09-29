package com.scenedeck.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.OutputAction
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.OutputSafetyGate
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.data.Telemetry
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
class LiveViewModel @Inject constructor(
    private val obsState: ObsStateRepository,
    private val settings: SettingsRepository,
    private val client: ObsClient,
    stats: StatsRepository,
) : ViewModel() {

    val deckState: StateFlow<DeckState> = obsState.deckState
    val telemetry: StateFlow<Telemetry> = stats.telemetry

    val outputSafety: StateFlow<OutputSafety> = settings.settings
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

    val hapticsEnabled: StateFlow<Boolean> = settings.settings
        .map { it.haptics }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Scene whose switch is taking > ~300 ms (per-card pending spinner). */
    private val _pendingScene = MutableStateFlow<String?>(null)
    val pendingScene: StateFlow<String?> = _pendingScene.asStateFlow()
    private var pendingWatch: Job? = null

    private val _confirmation = MutableStateFlow<TransportConfirmation?>(null)
    val confirmation: StateFlow<TransportConfirmation?> = _confirmation.asStateFlow()

    /** One-shot user-facing errors (OBS request failures) for the snackbar. */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // ── Deck ────────────────────────────────────────────────────────────────

    fun onSceneTap(sceneName: String) {
        if (deckState.value.connectionState !is ConnectionState.Ready) return
        if (deckState.value.currentProgramScene == sceneName) return
        pendingWatch?.cancel()
        pendingWatch = viewModelScope.launch {
            val showSpinner = launch {
                delay(PENDING_SPINNER_DELAY_MS)
                _pendingScene.value = sceneName
            }
            runCatching { obsState.setCurrentProgramScene(sceneName) }
                .onFailure { _errors.tryEmit("Couldn't switch to $sceneName") }
            // LAN is fast: wait for the change event, then hide the spinner.
            withTimeoutOrNull(PENDING_TIMEOUT_MS) {
                deckState.first { it.currentProgramScene == sceneName }
            }
            showSpinner.cancel()
            _pendingScene.value = null
        }
    }

    fun saveSceneMeta(sceneName: String, primary: Boolean, accentColorArgb: Long?, iconName: String?) {
        viewModelScope.launch {
            obsState.updateSceneMeta(
                sceneName = sceneName,
                role = if (primary) SceneRole.PRIMARY else SceneRole.SECONDARY,
                accentColorArgb = accentColorArgb,
                iconName = iconName,
            )
        }
    }

    fun reorderDeck(orderedSceneNames: List<String>) {
        viewModelScope.launch { obsState.reorderDeck(orderedSceneNames) }
    }

    // ── Transport (Output Safety, FEATURE_SPEC §4) ──────────────────────────

    fun onStreamClick() {
        val active = telemetry.value.stream?.active == true
        when (OutputSafetyGate.streamAction(active, outputSafety.value)) {
            OutputAction.PERFORM -> toggleStream(active)
            OutputAction.REQUIRE_CONFIRMATION ->
                _confirmation.value =
                    if (active) TransportConfirmation.STOP_STREAM else TransportConfirmation.START_STREAM
        }
    }

    fun onRecordClick() {
        val active = telemetry.value.record?.active == true
        when (OutputSafetyGate.recordAction(active, outputSafety.value)) {
            OutputAction.PERFORM -> toggleRecord(active)
            OutputAction.REQUIRE_CONFIRMATION ->
                _confirmation.value =
                    if (active) TransportConfirmation.STOP_RECORD else TransportConfirmation.START_RECORD
        }
    }

    fun confirmPending() {
        when (confirmation.value) {
            TransportConfirmation.START_STREAM -> toggleStream(false)
            TransportConfirmation.STOP_STREAM -> toggleStream(true)
            TransportConfirmation.START_RECORD -> toggleRecord(false)
            TransportConfirmation.STOP_RECORD -> toggleRecord(true)
            null -> Unit
        }
        _confirmation.value = null
    }

    fun dismissConfirmation() {
        _confirmation.value = null
    }

    private fun toggleStream(active: Boolean) {
        viewModelScope.launch {
            runCatching { if (active) client.stopStream() else client.startStream() }
                .onFailure { _errors.tryEmit(it.toTransportMessage("stream")) }
        }
    }

    private fun toggleRecord(active: Boolean) {
        viewModelScope.launch {
            runCatching { if (active) client.stopRecord() else client.startRecord() }
                .onFailure { _errors.tryEmit(it.toTransportMessage("recording")) }
        }
    }

    private fun Throwable.toTransportMessage(what: String): String = when (this) {
        is ObsRequestFailedException -> "OBS refused to control the $what: ${message ?: statusCode}"
        else -> "Couldn't control the $what: ${message ?: "connection error"}"
    }

    private companion object {
        const val PENDING_SPINNER_DELAY_MS = 300L
        const val PENDING_TIMEOUT_MS = 5_000L
    }
}
