package com.scenedeck.android.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DoctorChecks
import com.scenedeck.android.core.data.DoctorIssue
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneGraphBuilder
import com.scenedeck.android.core.data.probeBrokenAudioInputs
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestTimeoutException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    /**
     * Sanitized message for the last failed run (no server comments or transport details). When
     * set, [issues] is empty: results from an earlier run are never shown as current.
     */
    val error: String? = null,
)

internal const val DOCTOR_FAILED_MESSAGE =
    "Checks couldn't finish. Make sure OBS is still connected, then re-run."
internal const val DOCTOR_TIMEOUT_MESSAGE = "OBS didn't answer in time. Re-run the checks."

/** Maps a failed run to user copy without exposing exception messages or OBS comments. */
internal fun doctorFailureMessage(failure: Throwable): String =
    if (failure is ObsRequestTimeoutException) DOCTOR_TIMEOUT_MESSAGE else DOCTOR_FAILED_MESSAGE

@HiltViewModel
class DoctorViewModel
@Inject
constructor(
    private val registry: RegistryRepository,
    private val client: ObsClient,
) : ViewModel() {

    private var checkJob: Job? = null
    private val graphBuilder = SceneGraphBuilder(client)

    private val _uiState = MutableStateFlow(DoctorUiState())
    val uiState: StateFlow<DoctorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            client.connectionState.collectLatest { state ->
                if (state is ConnectionState.Ready) {
                    startChecks(state)
                } else {
                    checkJob?.cancel()
                    _uiState.value = DoctorUiState(connection = state)
                }
            }
        }
    }

    fun refresh() {
        val state = client.connectionState.value
        if (state is ConnectionState.Ready) {
            startChecks(state)
        }
    }

    fun removeStaleEntry(sceneName: String) {
        viewModelScope.launch {
            registry.remove(sceneName)
            refresh()
        }
    }

    private fun startChecks(connection: ConnectionState) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            coroutineResult { runChecks(connection) }
                .onFailure { failure ->
                    // Drop earlier results: they would read as the outcome of this failed run.
                    _uiState.value =
                        DoctorUiState(
                            connection = connection,
                            error = doctorFailureMessage(failure),
                        )
                }
        }
    }

    private suspend fun runChecks(connection: ConnectionState) {
        _uiState.value = _uiState.value.copy(connection = connection, running = true, error = null)
        val sceneList = client.getSceneList().scenes
        val obsScenes = sceneList.map { it.name }
        val entries = registry.snapshot()
        // Strict: a failed graph read fails the run instead of finishing as a partial all clear.
        val graph = graphBuilder.buildStrict(sceneList, entries)
        // Only explicitly PRIMARY scenes still present in OBS are reported, so only those are
        // probed; stale entries are a finding of their own and must not abort the scan.
        val primaryScenes = DoctorChecks.primaryScenesToProbe(obsScenes, entries)
        val brokenAudio = probeBrokenAudioInputs(client, primaryScenes)

        val issues = buildList {
            addAll(DoctorChecks.circularReferences(graph))
            addAll(DoctorChecks.hierarchyInversions(graph, entries))
            addAll(DoctorChecks.staleEntries(obsScenes, entries))
            addAll(DoctorChecks.primaryWithBrokenAudio(entries, brokenAudio))
            addAll(DoctorChecks.unassignedRoles(obsScenes, entries))
            addAll(DoctorChecks.unreferencedModules(obsScenes, entries, graph))
        }
            .sortedWith(
                compareBy(
                    { it.severity.ordinal },
                    { it.title },
                    { it.sceneName ?: "" },
                )
            )

        currentCoroutineContext().ensureActive()
        _uiState.value =
            DoctorUiState(
                connection = connection,
                running = false,
                issues = issues,
                ranOnce = true,
            )
    }
}
