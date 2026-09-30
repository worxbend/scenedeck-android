package com.scenedeck.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.background.KeepAliveController
import com.scenedeck.android.background.SceneSwitchResult
import com.scenedeck.android.background.SceneSwitcher
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.ui.components.StatusStripState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-shell level dependencies: settings persistence, the OBS session, telemetry. */
@HiltViewModel
class AppViewModel
@Inject
constructor(
    val settingsRepository: SettingsRepository,
    val sessionHolder: ObsSessionHolder,
    private val sceneSwitcher: SceneSwitcher,
    private val keepAliveController: KeepAliveController,
    statsRepository: StatsRepository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = sessionHolder.connectionState

    /** StatusStrip feed: live telemetry from the 1 Hz poll + connection state. */
    val stripState: StateFlow<StatusStripState> =
        statsRepository.telemetry
            .map { telemetry ->
                StatusStripState(
                    connection = telemetry.connection,
                    fps = telemetry.stats?.activeFps ?: 0.0,
                    droppedFrames = telemetry.stats?.outputSkippedFrames ?: 0,
                    cpuPercent = telemetry.stats?.cpuUsage ?: 0.0,
                    bitrateKbps = telemetry.bitrateKbps,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusStripState())

    /** `null` while the first settings read is in flight. */
    val onboardingCompleted: StateFlow<Boolean?> =
        settingsRepository.settings
            .map { it.onboardingCompleted }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One-shot results of deep-link scene switches (true = switched). */
    private val _sceneSwitchResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val sceneSwitchResults: SharedFlow<Boolean> = _sceneSwitchResults

    init {
        // Foreground launch re-arms the keep-alive service if the user enabled it
        // (covers force-stop / system kills that STICKY restart did not survive).
        viewModelScope.launch {
            if (keepAliveController.keepAliveEnabled.first()) keepAliveController.start()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }

    /** `scenedeck://scene/{name}` automation entry point (connects first if needed). */
    fun onSceneLink(sceneName: String) {
        viewModelScope.launch {
            val result = sceneSwitcher.switchTo(sceneName)
            _sceneSwitchResults.tryEmit(result == SceneSwitchResult.Success)
        }
    }
}
