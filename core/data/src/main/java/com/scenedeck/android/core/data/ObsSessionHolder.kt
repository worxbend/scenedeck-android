package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of the single [ObsClient] session (docs/ARCHITECTURE.md — state ownership).
 * Repositories/features talk to it; nothing else constructs clients. Activity-scoped for now; the
 * keep-alive foreground service is M7.
 */
@Singleton
class ObsSessionHolder
@Inject
constructor(
    val client: ObsClient,
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    private val settings: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val failure = MutableStateFlow<ConnectionState.Failed?>(null)
    private val commandLock = Any()
    private var commandJob: Job? = null

    /** Gates the one-shot auto-connect in init (explicit connect/disconnect also count). */
    private val autoConnectAttempted = AtomicBoolean(false)

    val connectionState: StateFlow<ConnectionState> =
        combine(client.connectionState, failure) { state, error ->
                error ?: state
            }
            .stateIn(appScope, SharingStarted.Eagerly, client.connectionState.value)

    /** Onboarding completed at least once (drives auto-connect + :app start logic). */
    val onboardingCompleted: StateFlow<Boolean> =
        settings.settings
            .map { it.onboardingCompleted }
            .catch { error ->
                if (error is CancellationException) throw error
                // Local settings-read hiccup: skip auto-connect, but don't masquerade a disk
                // error as an OBS connection failure.
                emit(false)
            }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        // Auto-connect to the last-used profile once onboarding is done.
        appScope.launch {
            requestResult {
                val snapshot = settings.settings.first { it.onboardingCompleted }
                val profileId = snapshot.lastUsedProfileId
                if (profileId != null && autoConnectAttempted.compareAndSet(false, true))
                    enqueueConnect(profileId)
            }
                .onFailure { error ->
                    // A settings-read (disk) failure is not an OBS connection failure.
                    if (error !is IOException) reportFailure()
                }
        }
    }

    /** New commands cancel and join previous preparation/handshake before running. */
    fun connect(profileId: Long) {
        autoConnectAttempted.set(true)
        enqueueConnect(profileId)
    }

    private fun enqueueConnect(profileId: Long) = enqueue {
        val profile = profiles.byId(profileId) ?: error("Saved profile no longer exists")
        val password = secrets.passwordFor(profile.id)
        profiles.markUsed(profile.id)
        settings.setLastUsedProfileId(profile.id)
        currentCoroutineContext().ensureActive()
        client.connect(profile.host, profile.port, password)
    }

    fun disconnect() {
        autoConnectAttempted.set(true)
        enqueue { client.disconnect() }
    }

    private fun enqueue(operation: suspend () -> Unit) {
        synchronized(commandLock) {
            val previous = commandJob
            previous?.cancel()
            commandJob = appScope.launch {
                previous?.join()
                failure.value = null
                requestResult { operation() }.onFailure { reportFailure(it) }
            }
        }
    }

    private fun reportFailure(error: Throwable? = null) {
        // Never expose raw credential/Keystore exception messages to UI or logs.
        failure.value =
            client.connectionState.value as? ConnectionState.Failed
                ?: ConnectionState.Failed(
                    when (error) {
                        // The saved password is gone for good: an auth-style failure so the
                        // UI prompts re-entry instead of retrying a dead credential.
                        is SecretsDecryptException ->
                            ConnectionError.Auth(
                                "Saved OBS password is no longer readable — re-enter it"
                            )

                        else -> ConnectionError.Protocol("Unable to open saved OBS session")
                    }
                )
    }
}
