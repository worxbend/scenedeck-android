package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsInputSummary
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface PresetTargets {
    data object Unavailable : PresetTargets

    data class Loading(val origin: WorkspaceSession) : PresetTargets

    data class Failed(val origin: WorkspaceSession) : PresetTargets

    data class Ready(val origin: WorkspaceSession, val inputs: List<ObsInputSummary>) :
        PresetTargets
}

/** Subscribed, event-driven directory for the editor. No polling or control writes. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PresetTargetsRepository
@Inject
constructor(private val client: ObsClient, private val workspaces: WorkspaceContext) {
    private val retries = MutableStateFlow(0)
    val state: Flow<PresetTargets> =
        combine(workspaces.state, retries) { state, _ -> state }
            .flatMapLatest { workspace ->
                val origin = (workspace as? WorkspaceState.Ready)?.session
                if (origin == null || !workspaces.isCurrent(origin))
                    flowOf(PresetTargets.Unavailable)
                else observe(origin)
            }

    fun retry() {
        retries.update { it + 1 }
    }

    private fun observe(origin: WorkspaceSession): Flow<PresetTargets> = channelFlow {
        val reloads = Channel<Unit>(Channel.CONFLATED)
        val events =
            launch(start = CoroutineStart.UNDISPATCHED) {
                client.eventsWithContext.collect { received ->
                    if (received.token == origin.token && received.event.changesInputDirectory())
                        reloads.trySend(Unit)
                }
                error("Input directory events ended")
            }
        reloads.trySend(Unit)
        try {
            reloads
                .receiveAsFlow()
                .mapLatest {
                    send(PresetTargets.Loading(origin))
                    read(origin)
                }
                .collect { send(it) }
        } finally {
            events.cancel()
            reloads.close()
        }
    }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(
                if (workspaces.isCurrent(origin)) PresetTargets.Failed(origin)
                else PresetTargets.Unavailable
            )
        }

    private suspend fun read(origin: WorkspaceSession): PresetTargets {
        val inputs = requestResult {
            workspaces.requireCurrent(origin)
            val found =
                withTimeoutOrNull(12_000) { client.withSession(origin.token) { getInputList() } }
                    ?: error("Input directory unavailable")
            check(found.size <= 2_000)
            check(
                found.all {
                    it.name.isNotBlank() && it.name.length <= 1_024 && it.kind.length <= 256
                }
            )
            workspaces.requireCurrent(origin)
            found
                .distinctBy { it.name }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
            .getOrNull()
        return when {
            !workspaces.isCurrent(origin) -> PresetTargets.Unavailable
            inputs == null -> PresetTargets.Failed(origin)
            else -> PresetTargets.Ready(origin, inputs)
        }
    }
}

private fun ObsEvent.changesInputDirectory(): Boolean =
    when (this) {
        is ObsEvent.InputCreated,
        is ObsEvent.InputRemoved,
        is ObsEvent.InputNameChanged -> true
        else -> false
    }
