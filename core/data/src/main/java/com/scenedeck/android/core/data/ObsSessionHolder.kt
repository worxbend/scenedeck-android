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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    private val preparing = MutableStateFlow(false)
    private val commandLock = Any()
    private var commandGeneration = 0L
    private var commandJob: Job? = null

    /** Gates the one-shot auto-connect in init (explicit connect/disconnect also count). */
    private val autoConnectAttempted = AtomicBoolean(false)

    val connectionState: StateFlow<ConnectionState> =
        combine(client.connectionState, failure, preparing) { state, error, pending ->
                if (pending) ConnectionState.Connecting else error ?: state
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

    /** Awaits this request's terminal state, never a stale state from an earlier attempt. */
    suspend fun connectAndAwait(profileId: Long): ConnectionState {
        autoConnectAttempted.set(true)
        val request = enqueueConnect(profileId)
        return try {
            request.await()
        } catch (cancelled: CancellationException) {
            request.cancel()
            throw cancelled
        }
    }

    private fun enqueueConnect(profileId: Long) =
        enqueue(connecting = true) {
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

    private fun enqueue(
        connecting: Boolean = false,
        operation: suspend () -> Unit,
    ): Deferred<ConnectionState> =
        synchronized(commandLock) {
            val previous = commandJob
            previous?.cancel()
            val generation = ++commandGeneration
            preparing.value = connecting
            failure.value = null
            appScope
                .async {
                    try {
                        previous?.join()
                        currentCoroutineContext().ensureActive()
                        performOperation(operation)
                    } finally {
                        synchronized(commandLock) {
                            if (commandGeneration == generation) preparing.value = false
                        }
                    }
                }
                .also { commandJob = it }
        }

    private suspend fun performOperation(operation: suspend () -> Unit): ConnectionState {
        val result = requestResult { operation() }
        result.exceptionOrNull()?.let { error ->
            // Failed preparation must not leave an old Ready session behind a Failed overlay.
            requestResult { client.disconnect() }
            reportFailure(error)
        }
        currentCoroutineContext().ensureActive()
        return failure.value ?: client.connectionState.value
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
