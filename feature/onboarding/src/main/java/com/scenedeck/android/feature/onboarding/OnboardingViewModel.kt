package com.scenedeck.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Connect-step state for the first-run wizard. */
sealed interface OnboardingConnectState {
    data object Editing : OnboardingConnectState

    data object Connecting : OnboardingConnectState

    data class Failed(val error: ConnectionError) : OnboardingConnectState

    data class Connected(val version: ObsVersionInfo, val sceneCount: Int) : OnboardingConnectState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    private val settings: SettingsRepository,
    private val sessionHolder: ObsSessionHolder,
    private val client: ObsClient,
) : ViewModel() {

    private val _connectState = MutableStateFlow<OnboardingConnectState>(OnboardingConnectState.Editing)
    val connectState: StateFlow<OnboardingConnectState> = _connectState

    /** Saves the profile, connects through the shared session, verifies via scene list. */
    fun connect(name: String, host: String, port: Int, password: String?) {
        if (_connectState.value is OnboardingConnectState.Connecting) return
        viewModelScope.launch {
            _connectState.value = OnboardingConnectState.Connecting
            val profileId = profiles.add(name.trim().ifBlank { "My OBS" }, host, port)
            if (!password.isNullOrBlank()) secrets.setPassword(profileId, password)

            sessionHolder.connect(profileId)
            when (val terminal = sessionHolder.connectionState.first {
                it is ConnectionState.Ready || it is ConnectionState.Failed
            }) {
                is ConnectionState.Ready -> {
                    val sceneCount = runCatching { client.getSceneList().scenes.size }.getOrDefault(0)
                    _connectState.value = OnboardingConnectState.Connected(terminal.sessionInfo, sceneCount)
                }

                is ConnectionState.Failed ->
                    _connectState.value = OnboardingConnectState.Failed(terminal.error)

                else -> _connectState.value = OnboardingConnectState.Editing
            }
        }
    }

    fun resetConnect() {
        _connectState.value = OnboardingConnectState.Editing
    }

    fun finish() {
        viewModelScope.launch { settings.setOnboardingCompleted(true) }
    }
}
