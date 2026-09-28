package com.scenedeck.android.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.ConnectionProfile
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Form values for creating/updating a profile (password blank = keep stored one). */
data class ProfileDraft(
    val name: String,
    val host: String,
    val port: Int,
    val password: String? = null,
)

/** Outcome of a "Test connection" handshake against an unsaved/edited target. */
sealed interface TestConnectionState {
    data object Idle : TestConnectionState

    data object Testing : TestConnectionState

    data class Success(val version: ObsVersionInfo) : TestConnectionState

    data class Failure(val error: ConnectionError) : TestConnectionState
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    private val settings: SettingsRepository,
    private val sessionHolder: ObsSessionHolder,
) : ViewModel() {

    val profileList: StateFlow<List<ConnectionProfile>> = profiles.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionState: StateFlow<ConnectionState> = sessionHolder.connectionState

    val lastUsedProfileId: StateFlow<Long?> = settings.settings
        .map { it.lastUsedProfileId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _testState = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testState: StateFlow<TestConnectionState> = _testState

    fun connect(profileId: Long) = sessionHolder.connect(profileId)

    fun disconnect() = sessionHolder.disconnect()

    /**
     * Creates or updates a profile. A blank [ProfileDraft.password] leaves any stored
     * password untouched; a non-blank one replaces it (Keystore-backed, never in Room).
     */
    fun saveProfile(id: Long?, draft: ProfileDraft, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val profileId = if (id == null) {
                profiles.add(draft.name, draft.host, draft.port)
            } else {
                val existing = profiles.byId(id) ?: return@launch
                profiles.update(
                    existing.copy(
                        name = draft.name.trim(),
                        host = draft.host.trim(),
                        port = draft.port,
                    ),
                )
                id
            }
            if (!draft.password.isNullOrBlank()) secrets.setPassword(profileId, draft.password)
            onSaved(profileId)
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            secrets.setPassword(id, null)
            profiles.delete(id)
            settings.update {
                if (it.lastUsedProfileId == id) it.copy(lastUsedProfileId = null) else it
            }
        }
    }

    /** Runs the real Hello/Identify handshake with a throwaway client. */
    fun testConnection(host: String, port: Int, password: String?) {
        if (_testState.value is TestConnectionState.Testing) return
        viewModelScope.launch {
            _testState.value = TestConnectionState.Testing
            _testState.value = runTestHandshake(host, port, password)
        }
    }

    fun resetTestState() {
        _testState.value = TestConnectionState.Idle
    }

    @Suppress("TooGenericExceptionCaught") // any socket/crypto failure maps to Unreachable
    private suspend fun runTestHandshake(host: String, port: Int, password: String?): TestConnectionState {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            withTimeout(TEST_TIMEOUT_MS) {
                val client = ObsClient(scope)
                client.connect(host = host, port = port, password = password?.takeIf { it.isNotBlank() })
                when (val state = client.connectionState.value) {
                    is ConnectionState.Ready -> TestConnectionState.Success(state.sessionInfo)
                    is ConnectionState.Failed -> TestConnectionState.Failure(state.error)
                    else -> TestConnectionState.Failure(ConnectionError.Protocol("Handshake did not complete"))
                }
            }
        } catch (e: Exception) {
            TestConnectionState.Failure(ConnectionError.Unreachable(e))
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 15_000L
    }
}
