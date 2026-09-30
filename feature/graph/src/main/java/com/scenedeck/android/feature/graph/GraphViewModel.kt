package com.scenedeck.android.feature.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneGraph
import com.scenedeck.android.core.data.SceneGraphBuilder
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

data class GraphUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val graph: SceneGraph? = null,
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

    val uiState: StateFlow<GraphUiState> =
        combine(
                client.connectionState,
                registry.entries,
            ) { connection, entries ->
                connection to entries
            }
            .transformLatest { (connection, entries) ->
                if (connection is ConnectionState.Ready) {
                    emit(GraphUiState(connection = connection, graph = builder.build(entries)))
                } else {
                    emit(GraphUiState(connection = connection, graph = null))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphUiState())

    /** Selected node for the detail sheet. */
    private val _selectedNode = MutableStateFlow<String?>(null)
    val selectedNode: StateFlow<String?> = _selectedNode.asStateFlow()

    fun selectNode(name: String?) {
        _selectedNode.value = name
    }

    fun parentsOf(name: String): List<String> =
        uiState.value.graph?.edges?.filter { it.to == name }?.map { it.from }.orEmpty()

    fun childrenOf(name: String): List<String> =
        uiState.value.graph?.edges?.filter { it.from == name }?.map { it.to }.orEmpty()
}
