package com.scenedeck.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.background.KeepAliveController
import com.scenedeck.android.background.SceneSwitcher
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.designsystem.theme.UiDensity
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.navigation.SceneLinkFeedback
import com.scenedeck.android.ui.components.StatusStripState
import com.scenedeck.android.ui.components.toStatusStripState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
            .combine(connectionState) { telemetry, connection ->
                telemetry.copy(connection = connection).toStatusStripState()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusStripState())

    /** `null` while the first settings read is in flight. */
    val onboardingCompleted: StateFlow<Boolean?> =
        settingsRepository.settings
            .map { it.onboardingCompleted }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sceneLinkMessage = MutableStateFlow<String?>(null)

    /** Pending, sanitized feedback for a failed scene deep link; null when nothing to show. */
    val sceneLinkMessage: StateFlow<String?> = _sceneLinkMessage.asStateFlow()

    /** Layout density for [com.scenedeck.android.core.designsystem.theme.SceneDeckTheme]. */
    val uiDensity: StateFlow<UiDensity> =
        settingsRepository.settings
            .map { UiDensity.fromName(it.uiDensity) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiDensity.COMFORTABLE)

    init {
        // Foreground launch re-arms the keep-alive service if the user enabled it
        // (covers force-stop / system kills that STICKY restart did not survive).
        viewModelScope.launch {
            if (keepAliveController.keepAliveEnabled.first()) keepAliveController.start()
        }
    }

    /** "Retry now": skip the remaining OBS reconnect backoff (no-op outside backoff). */
    fun retryNow() {
        sessionHolder.retryNow()
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }

    /** `scenedeck://scene/{name}` automation entry point (connects first if needed). */
    fun onSceneLink(sceneName: String) {
        viewModelScope.launch {
            val result = sceneSwitcher.switchTo(sceneName)
            SceneLinkFeedback.messageFor(result, sceneName)?.let { _sceneLinkMessage.value = it }
        }
    }

    /** Clears [message] once shown; a newer message posted meanwhile is kept. */
    fun onSceneLinkMessageShown(message: String) {
        _sceneLinkMessage.compareAndSet(message, null)
    }
}
