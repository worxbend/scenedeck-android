package com.scenedeck.android.feature.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.RegistryState
import com.scenedeck.android.core.data.SceneGraph
import com.scenedeck.android.core.data.SceneGraphBuilder
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.data.requireCurrent
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val graph: SceneGraph? = null,
    val workspace: WorkspaceSession? = null,
    val failed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GraphViewModel
@Inject
constructor(
    private val registry: RegistryRepository,
    private val client: ObsClient,
) : ViewModel() {

    private val builder = SceneGraphBuilder(client)

    private val changes = MutableStateFlow(0L)

    val uiState: StateFlow<GraphUiState> =
        combine(client.connectionState, registry.state, changes) { connection, metadata, _ ->
                connection to metadata
            }
            .transformLatest { (connection, metadata) ->
                val ready = metadata as? RegistryState.Ready
                val origin = ready?.session?.takeIf(registry.workspaces::isCurrent)
                emit(
                    GraphUiState(
                        connection = connection,
                        failed =
                            metadata is RegistryState.Failed ||
                                registry.workspaces.state.value == WorkspaceState.Failed,
                    )
                )
                if (origin != null) {
                    coroutineResult {
                        val graph =
                            client.withSession(origin.token) {
                                builder.build(ready.entries).withDistinctEdges()
                            }
                        registry.workspaces.requireCurrent(origin)
                        emit(GraphUiState(connection, graph, origin))
                    }
                        .onFailure {
                            if (registry.workspaces.isCurrent(origin))
                                emit(GraphUiState(connection = connection, failed = true))
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphUiState())

    /** Selected node for the detail sheet. */
    private val _selectedNode = MutableStateFlow<String?>(null)
    val selectedNode: StateFlow<String?> = _selectedNode.asStateFlow()

    init {
        // The subscription exists before any caller can collect uiState and trigger its read.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect { received ->
                val origin = registry.workspaces.currentSession() ?: return@collect
                if (received.token != origin.token) return@collect
                val event = received.event
                renamedNode(event)?.let { (old, new) ->
                    _selectedNode.update { if (it == old) new else it }
                }
                if (affectsSceneGraph(event)) changes.update { it + 1 }
            }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            registry.workspaces.state.collect { _selectedNode.value = null }
        }
    }

    fun retryWorkspace() {
        registry.retry()
        changes.update { it + 1 }
    }

    fun selectNode(name: String?) {
        _selectedNode.value = name
    }

    fun parentsOf(name: String): List<String> =
        uiState.value.graph?.edges?.filter { it.to == name }?.map { it.from }.orEmpty()

    fun childrenOf(name: String): List<String> =
        uiState.value.graph?.edges?.filter { it.from == name }?.map { it.to }.orEmpty()
}

private fun renamedNode(event: ObsEvent): Pair<String, String>? =
    when (event) {
        is ObsEvent.SceneNameChanged -> event.oldSceneName to event.sceneName
        is ObsEvent.InputNameChanged -> event.oldInputName to event.inputName
        else -> null
    }

/**
 * One edge per parent→child pair. A scene can hold the same nested scene several times (duplicate
 * scene items); the canvas draws one line for them, so the a11y count and detail lists match it.
 */
internal fun SceneGraph.withDistinctEdges(): SceneGraph =
    copy(edges = edges.distinctBy { it.from to it.to })

private fun affectsSceneGraph(event: ObsEvent): Boolean =
    when (event) {
        is ObsEvent.SceneCreated,
        is ObsEvent.SceneRemoved,
        is ObsEvent.SceneNameChanged,
        is ObsEvent.SceneListChanged,
        is ObsEvent.SceneItemEnableStateChanged,
        is ObsEvent.CurrentSceneCollectionChanged,
        is ObsEvent.InputCreated,
        is ObsEvent.InputRemoved,
        is ObsEvent.InputNameChanged -> true
        else -> false
    }
