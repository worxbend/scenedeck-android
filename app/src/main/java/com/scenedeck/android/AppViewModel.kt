package com.scenedeck.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.ui.components.StatusStripState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-shell level dependencies: settings persistence, the OBS session, telemetry. */
@HiltViewModel
class AppViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
    val sessionHolder: ObsSessionHolder,
    statsRepository: StatsRepository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = sessionHolder.connectionState

    /** StatusStrip feed: live telemetry from the 1 Hz poll + connection state. */
    val stripState: StateFlow<StatusStripState> = statsRepository.telemetry
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
    val onboardingCompleted: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }
}
