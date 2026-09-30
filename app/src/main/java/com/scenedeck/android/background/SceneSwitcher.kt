package com.scenedeck.android.background

import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of a [SceneSwitcher.switchTo] call. */
enum class SceneSwitchResult {
    Success,

    /** No saved profile to connect to (onboarding never finished). */
    NoProfile,

    /** Connection never reached Ready in time (or terminally failed). */
    NotConnected,

    /** OBS rejected the scene switch (e.g. unknown scene name). */
    RequestFailed,
}

/**
 * Connect-then-act scene switch shared by the Glance widget and the `scenedeck://scene/{name}` deep
 * link. Works from a cold process: if the session is not [ConnectionState.Ready], connects to the
 * last-used profile first and waits.
 */
@Singleton
class SceneSwitcher
@Inject
constructor(
    private val client: ObsClient,
    private val sessionHolder: ObsSessionHolder,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val deck: ObsStateRepository,
) {
    suspend fun switchTo(sceneName: String): SceneSwitchResult =
        ensureReady()
            ?: runCatching { deck.setCurrentProgramScene(sceneName) }
                .fold(
                    onSuccess = { SceneSwitchResult.Success },
                    onFailure = { failure ->
                        if (failure is CancellationException) throw failure
                        SceneSwitchResult.RequestFailed
                    },
                )

    /** null = session is Ready; otherwise the failure to report to the caller. */
    private suspend fun ensureReady(): SceneSwitchResult? =
        if (client.connectionState.value is ConnectionState.Ready) {
            null
        } else {
            connectToLastUsed()
        }

    private suspend fun connectToLastUsed(): SceneSwitchResult? {
        val profileId = settings.settings.first().lastUsedProfileId ?: profiles.lastUsed()?.id
        if (profileId == null) return SceneSwitchResult.NoProfile
        sessionHolder.connect(profileId)
        val terminal =
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                client.connectionState.first {
                    it is ConnectionState.Ready || it is ConnectionState.Failed
                }
            }
        return if (terminal is ConnectionState.Ready) null else SceneSwitchResult.NotConnected
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
