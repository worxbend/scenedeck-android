package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of the single [ObsClient] session (docs/ARCHITECTURE.md —
 * state ownership). Repositories/features talk to it; nothing else constructs clients.
 * Activity-scoped for now; the keep-alive foreground service is M7.
 */
@Singleton
class ObsSessionHolder @Inject constructor(
    val client: ObsClient,
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    private val settings: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    val connectionState: StateFlow<ConnectionState> = client.connectionState

    /** Onboarding completed at least once (drives auto-connect + :app start logic). */
    val onboardingCompleted: StateFlow<Boolean> = settings.settings
        .map { it.onboardingCompleted }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        // Auto-connect to the last-used profile once onboarding is done.
        appScope.launch {
            val snapshot = settings.settings.first { it.onboardingCompleted }
            snapshot.lastUsedProfileId?.let { connect(it) }
        }
    }

    /** Connects to a saved profile (fetches host/port + decrypted password). */
    fun connect(profileId: Long) {
        appScope.launch {
            val profile = profiles.byId(profileId) ?: return@launch
            profiles.markUsed(profile.id)
            settings.setLastUsedProfileId(profile.id)
            client.connect(
                host = profile.host,
                port = profile.port,
                password = secrets.passwordFor(profile.id),
            )
        }
    }

    fun disconnect() {
        appScope.launch { client.disconnect() }
    }
}
