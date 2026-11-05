package com.scenedeck.android.background

import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.WorkspaceContext
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
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
 * widget's recorded profile (or last-used profile for a deep link) first and waits. A widget never
 * silently changes OBS collections or follows a different workspace.
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
    private val workspaces: WorkspaceContext,
) {
    suspend fun switchTo(
        sceneName: String,
        expectedWorkspace: WorkspaceKey? = null,
    ): SceneSwitchResult =
        ensureReady(expectedWorkspace?.profileId) ?: switchInWorkspace(sceneName, expectedWorkspace)

    private suspend fun switchInWorkspace(
        sceneName: String,
        expected: WorkspaceKey?,
    ): SceneSwitchResult {
        val origin = awaitWorkspace() ?: return SceneSwitchResult.NotConnected
        return if (expected != null && origin.key != expected) SceneSwitchResult.RequestFailed
        else dispatch(origin, sceneName)
    }

    private suspend fun awaitWorkspace(): WorkspaceSession? =
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            combine(workspaces.state, client.connectionState) { workspace, connection ->
                    workspace to connection
                }
                .first { (workspace, connection) ->
                    (workspace is WorkspaceState.Ready &&
                        workspaces.isCurrent(workspace.session)) ||
                        workspace == WorkspaceState.Failed ||
                        connection is ConnectionState.Failed ||
                        connection == ConnectionState.Disconnected
                }
                .first
                .let { it as? WorkspaceState.Ready }
                ?.session
        }

    private suspend fun dispatch(origin: WorkspaceSession, sceneName: String): SceneSwitchResult {
        return runCatching { deck.setCurrentProgramScene(origin, sceneName) }
            .fold(
                onSuccess = { SceneSwitchResult.Success },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    SceneSwitchResult.RequestFailed
                },
            )
    }

    /** null = session is Ready; otherwise the failure to report to the caller. */
    private suspend fun ensureReady(profileId: Long?): SceneSwitchResult? =
        if (client.connectionState.value is ConnectionState.Ready) {
            null
        } else {
            connectToProfile(profileId)
        }

    private suspend fun connectToProfile(requestedProfileId: Long?): SceneSwitchResult? {
        val profileId =
            requestedProfileId
                ?: settings.settings.first().lastUsedProfileId
                ?: profiles.lastUsed()?.id
        if (profileId == null) return SceneSwitchResult.NoProfile
        val terminal =
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                sessionHolder.connectAndAwait(profileId)
            }
        return if (terminal is ConnectionState.Ready) null else SceneSwitchResult.NotConnected
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
