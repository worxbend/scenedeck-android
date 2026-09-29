package com.scenedeck.android.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.DoctorChecks
import com.scenedeck.android.core.data.DoctorIssue
import com.scenedeck.android.core.data.DoctorSeverity
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneGraphBuilder
import com.scenedeck.android.core.data.probeBrokenAudioInputs
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DoctorUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val running: Boolean = false,
    val issues: List<DoctorIssue> = emptyList(),
    val ranOnce: Boolean = false,
) {
    val errorCount: Int get() = issues.count { it.severity == DoctorSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == DoctorSeverity.WARNING }
    val infoCount: Int get() = issues.count { it.severity == DoctorSeverity.INFO }
}

@HiltViewModel
class DoctorViewModel @Inject constructor(
    private val registry: RegistryRepository,
    private val client: ObsClient,
) : ViewModel() {

    private val graphBuilder = SceneGraphBuilder(client)

    private val _uiState = MutableStateFlow(DoctorUiState())
    val uiState: StateFlow<DoctorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            client.connectionState.collectLatest { state ->
                if (state is ConnectionState.Ready) {
                    runChecks(state)
                } else {
                    _uiState.value = DoctorUiState(connection = state)
                }
            }
        }
    }

    fun refresh() {
        val state = client.connectionState.value
        if (state is ConnectionState.Ready) {
            viewModelScope.launch { runChecks(state) }
        }
    }

    fun removeStaleEntry(sceneName: String) {
        viewModelScope.launch {
            registry.remove(sceneName)
            refresh()
        }
    }

    private suspend fun runChecks(connection: ConnectionState) {
        _uiState.value = _uiState.value.copy(connection = connection, running = true)
        val obsScenes = runCatching { client.getSceneList().scenes.map { it.name } }
            .getOrDefault(emptyList())
        val entries = registry.snapshot()
        val graph = graphBuilder.build(entries)
        val primaryScenes = entries
            .filter { it.role == SceneRole.PRIMARY }
            .map { it.sceneName }
            .ifEmpty { obsScenes } // default rule: everything is PRIMARY
        val brokenAudio = probeBrokenAudioInputs(client, primaryScenes)

        val issues = buildList {
            addAll(DoctorChecks.circularReferences(graph))
            addAll(DoctorChecks.hierarchyInversions(graph))
            addAll(DoctorChecks.staleEntries(obsScenes, entries))
            addAll(DoctorChecks.primaryWithBrokenAudio(entries, brokenAudio))
            addAll(DoctorChecks.unassignedRoles(obsScenes, entries))
            addAll(DoctorChecks.unreferencedModules(obsScenes, entries, graph))
        }.sortedWith(
            compareBy(
                { it.severity.ordinal },
                { it.title },
                { it.sceneName ?: "" },
            ),
        )

        _uiState.value = DoctorUiState(
            connection = connection,
            running = false,
            issues = issues,
            ranOnce = true,
        )
    }
}
