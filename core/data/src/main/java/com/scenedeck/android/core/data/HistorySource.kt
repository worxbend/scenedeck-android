package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

data class HistoryProgram(val origin: WorkspaceSession, val name: String?)

data class HistoryInputs(
    val workspace: WorkspaceSession?,
    val telemetry: Telemetry = Telemetry(),
    val program: HistoryProgram? = null,
    val alerts: LiveAlertsState = LiveAlertsState(),
    val connected: Boolean = false,
)

/** Shared inputs only; this boundary never makes an OBS request. */
interface HistorySource {
    val events: Flow<ObsSessionEvent>
    val changes: Flow<Unit>

    fun current(): HistoryInputs

    fun isCurrent(origin: WorkspaceSession): Boolean
}

@Singleton
class SharedHistorySource
@Inject
constructor(
    private val client: ObsClient,
    private val workspaces: WorkspaceContext,
    private val stats: StatsRepository,
    private val scenes: ObsStateRepository,
    private val alerts: LiveAlertsRepository,
) : HistorySource {
    override val events = client.eventsWithContext
    override val changes =
        merge(
            workspaces.state.map {},
            client.connectionState.map {},
            stats.telemetry.map {},
            scenes.deckState.map {},
            alerts.state.map {},
        )

    override fun isCurrent(origin: WorkspaceSession) = workspaces.isCurrent(origin)

    override fun current(): HistoryInputs {
        val origin = workspaces.currentSession()
        val deck = scenes.deckState.value
        val snapshot =
            HistoryInputs(
                origin,
                stats.telemetry.value,
                deck.workspace?.let { HistoryProgram(it, deck.currentProgramScene) },
                alerts.state.value,
                client.connectionState.value is ConnectionState.Ready,
            )
        return if (origin == null || isCurrent(origin)) snapshot
        else snapshot.copy(workspace = null, program = null)
    }
}
