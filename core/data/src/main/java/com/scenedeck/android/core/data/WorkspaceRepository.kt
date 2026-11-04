package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Persistent namespace for local scene metadata. OBS names are never modified. */
data class WorkspaceKey(val profileId: Long, val collectionName: String)

/** A workspace resolved on one connection attempt and collection revision. Never persisted. */
data class WorkspaceSession(
    val key: WorkspaceKey,
    val targetGeneration: Long,
    val token: ObsSessionToken,
)

sealed interface WorkspaceState {
    data object Unavailable : WorkspaceState

    data object Resolving : WorkspaceState

    data class Ready(val session: WorkspaceSession) : WorkspaceState

    /** Collection discovery failed; this is unknown identity, not an empty workspace. */
    data object Failed : WorkspaceState
}

/** Resolves collection identity only after the selected profile's connection is confirmed. */
@Singleton
class WorkspaceRepository
@Inject
constructor(
    private val client: ObsClient,
    private val activeConnection: ActiveConnection,
    @ApplicationScope appScope: CoroutineScope,
) {
    private val current = MutableStateFlow<WorkspaceState>(WorkspaceState.Unavailable)
    val state = current.asStateFlow()
    private val retryRevision = MutableStateFlow(0L)

    init {
        appScope.launch {
            combine(activeConnection.target, client.connectionState, retryRevision) {
                    target,
                    connection,
                    _ ->
                    target to connection
                }
                .collectLatest { (target, connection) -> resolve(target, connection) }
        }
    }

    /** A synchronous guard for mutations/results, even before the flow collector catches up. */
    fun isCurrent(session: WorkspaceSession): Boolean =
        current.value == WorkspaceState.Ready(session) && isOriginCurrent(session)

    private fun isOriginCurrent(session: WorkspaceSession): Boolean {
        val target = activeConnection.target.value
        val ready = client.connectionState.value as? ConnectionState.Ready ?: return false
        return target.confirmed &&
            target.profileId == session.key.profileId &&
            target.generation == session.targetGeneration &&
            !ready.collectionChanging &&
            ready.token == session.token
    }

    fun currentSession(): WorkspaceSession? =
        (state.value as? WorkspaceState.Ready)?.session?.takeIf(::isCurrent)

    /** Retries only failed discovery. A pending lookup is never duplicated. */
    fun retry() {
        if (current.compareAndSet(WorkspaceState.Failed, WorkspaceState.Resolving)) {
            retryRevision.update { it + 1 }
        }
    }

    private suspend fun resolve(target: ConnectionTarget, connection: ConnectionState) {
        val profileId = target.profileId
        val ready = (connection as? ConnectionState.Ready)?.takeUnless { it.collectionChanging }
        if (!target.confirmed || profileId == null || ready == null) {
            current.value = WorkspaceState.Unavailable
            return
        }
        val origin = WorkspaceSession(WorkspaceKey(profileId, ""), target.generation, ready.token)
        current.value = WorkspaceState.Resolving
        val collection = requestResult { client.getSceneCollectionList().currentCollection }
        currentCoroutineContext().ensureActive()
        if (!isOriginCurrent(origin)) return
        current.value =
            collection
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { name ->
                    WorkspaceState.Ready(origin.copy(key = WorkspaceKey(profileId, name)))
                } ?: WorkspaceState.Failed
    }
}
