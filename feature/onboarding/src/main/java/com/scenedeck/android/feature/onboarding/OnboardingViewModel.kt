package com.scenedeck.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Connect-step state for the first-run wizard. */
sealed interface OnboardingConnectState {
    data object Editing : OnboardingConnectState

    data object Connecting : OnboardingConnectState

    data class Failed(val error: ConnectionError) : OnboardingConnectState

    data class Connected(val version: ObsVersionInfo, val sceneCount: Int) : OnboardingConnectState
}

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    private val settings: SettingsRepository,
    private val sessionHolder: ObsSessionHolder,
    private val client: ObsClient,
) : ViewModel() {

    private var connectJob: Job? = null
    private val _connectState =
        MutableStateFlow<OnboardingConnectState>(OnboardingConnectState.Editing)
    val connectState: StateFlow<OnboardingConnectState> = _connectState

    /** Saves the profile, connects through the shared session, verifies via scene list. */
    fun connect(name: String, host: String, port: Int, password: String?) {
        if (_connectState.value is OnboardingConnectState.Connecting) return
        _connectState.value = OnboardingConnectState.Connecting
        connectJob = viewModelScope.launch {
            coroutineResult { connectProfile(name, host, port, password) }
                .onFailure {
                    _connectState.value =
                        OnboardingConnectState.Failed(
                            ConnectionError.Protocol("Unable to save or connect this profile")
                        )
                }
        }
    }

    private suspend fun connectProfile(name: String, host: String, port: Int, password: String?) {
        val profileId = profiles.add(name.trim().ifBlank { "My OBS" }, host, port)
        if (!password.isNullOrBlank()) secrets.setPassword(profileId, password)

        when (val terminal = sessionHolder.connectAndAwait(profileId)) {
            is ConnectionState.Ready -> {
                val sceneCount = coroutineResult {
                    client.getSceneList().scenes.size
                }
                    .getOrDefault(0)
                currentCoroutineContext().ensureActive()
                _connectState.value =
                    OnboardingConnectState.Connected(terminal.sessionInfo, sceneCount)
            }

            is ConnectionState.Failed ->
                _connectState.value = OnboardingConnectState.Failed(terminal.error)

            else -> _connectState.value = OnboardingConnectState.Editing
        }
    }

    fun resetConnect() {
        connectJob?.cancel()
        _connectState.value = OnboardingConnectState.Editing
    }

    fun finish() {
        viewModelScope.launch { settings.setOnboardingCompleted(true) }
    }
}
