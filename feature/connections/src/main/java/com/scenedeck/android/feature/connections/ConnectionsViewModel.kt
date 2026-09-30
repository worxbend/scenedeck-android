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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
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
) {
    override fun toString(): String =
        "ProfileDraft(name=$name, host=$host, port=$port, password=<redacted>)"
}

/** Outcome of a "Test connection" handshake against an unsaved/edited target. */
sealed interface TestConnectionState {
    data object Idle : TestConnectionState

    data object Testing : TestConnectionState

    data class Success(val version: ObsVersionInfo) : TestConnectionState

    data class Failure(val error: ConnectionError) : TestConnectionState
}

enum class ProfileOperationError {
    SAVE,
    DELETE,
}

sealed interface ProfileOperationState {
    data object Idle : ProfileOperationState

    data object Saving : ProfileOperationState

    data object Deleting : ProfileOperationState

    data class Failure(val error: ProfileOperationError) : ProfileOperationState
}

@HiltViewModel
class ConnectionsViewModel
@Inject
constructor(
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    private val settings: SettingsRepository,
    private val sessionHolder: ObsSessionHolder,
) : ViewModel() {

    val profileList: StateFlow<List<ConnectionProfile>> =
        profiles.profiles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val connectionState: StateFlow<ConnectionState> = sessionHolder.connectionState

    val lastUsedProfileId: StateFlow<Long?> =
        settings.settings
            .map { it.lastUsedProfileId }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _testState = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testState: StateFlow<TestConnectionState> = _testState

    private val persistence = ProfilePersistence(profiles, secrets)
    private val _operationState =
        MutableStateFlow<ProfileOperationState>(ProfileOperationState.Idle)
    val operationState: StateFlow<ProfileOperationState> = _operationState

    fun connect(profileId: Long) = sessionHolder.connect(profileId)

    fun disconnect() = sessionHolder.disconnect()

    /**
     * Creates or updates a profile. A blank [ProfileDraft.password] leaves any stored password
     * untouched; a non-blank one replaces it (Keystore-backed, never in Room).
     */
    fun saveProfile(id: Long?, draft: ProfileDraft, onSaved: (Long) -> Unit = {}) {
        runProfileOperation(ProfileOperationState.Saving, ProfileOperationError.SAVE) {
            onSaved(persistence.save(id, draft))
        }
    }

    fun deleteProfile(id: Long) {
        runProfileOperation(ProfileOperationState.Deleting, ProfileOperationError.DELETE) {
            persistence.delete(id)
            settings.update {
                if (it.lastUsedProfileId == id) it.copy(lastUsedProfileId = null) else it
            }
        }
    }

    fun clearOperationError() {
        if (_operationState.value is ProfileOperationState.Failure)
            _operationState.value = ProfileOperationState.Idle
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Storage failures become sanitized UI state; cancellation propagates.
    private fun runProfileOperation(
        busyState: ProfileOperationState,
        error: ProfileOperationError,
        operation: suspend () -> Unit,
    ) {
        if (
            _operationState.value == ProfileOperationState.Saving ||
                _operationState.value == ProfileOperationState.Deleting
        )
            return
        _operationState.value = busyState
        viewModelScope.launch {
            try {
                operation()
                _operationState.value = ProfileOperationState.Idle
            } catch (cancelled: CancellationException) {
                _operationState.value = ProfileOperationState.Idle
                throw cancelled
            } catch (_: Exception) {
                _operationState.value = ProfileOperationState.Failure(error)
            }
        }
    }

    /** Runs the real Hello/Identify handshake with a throwaway client. */
    private var testJob: Job? = null

    fun testConnection(host: String, port: Int, password: String?, profileId: Long? = null) {
        if (_testState.value is TestConnectionState.Testing) return
        testJob = viewModelScope.launch {
            _testState.value = TestConnectionState.Testing
            val result = runTestHandshake(host, port, password, profileId)
            coroutineContext.ensureActive()
            _testState.value = result
        }
    }

    fun resetTestState() {
        testJob?.cancel()
        testJob = null
        _testState.value = TestConnectionState.Idle
    }

    @Suppress("TooGenericExceptionCaught") // any socket/crypto failure maps to Unreachable
    private suspend fun runTestHandshake(
        host: String,
        port: Int,
        password: String?,
        profileId: Long?,
    ): TestConnectionState {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            withTimeout(TEST_TIMEOUT_MS) {
                val effectivePassword = persistence.passwordForTest(password, profileId)
                val client = ObsClient(scope)
                client.connect(
                    host = host,
                    port = port,
                    password = effectivePassword,
                )
                when (val state = client.connectionState.value) {
                    is ConnectionState.Ready -> TestConnectionState.Success(state.sessionInfo)
                    is ConnectionState.Failed -> TestConnectionState.Failure(state.error)
                    else ->
                        TestConnectionState.Failure(
                            ConnectionError.Protocol("Handshake did not complete")
                        )
                }
            }
        } catch (e: TimeoutCancellationException) {
            TestConnectionState.Failure(ConnectionError.Unreachable(e))
        } catch (e: CancellationException) {
            throw e
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
