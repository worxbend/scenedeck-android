package com.scenedeck.android.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DoctorChecks
import com.scenedeck.android.core.data.DoctorIssue
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneGraphBuilder
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.data.probeBrokenAudioInputs
import com.scenedeck.android.core.data.requireCurrent
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestTimeoutException
import com.scenedeck.android.core.obs.withSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    val workspace: WorkspaceSession? = null,
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
            combine(client.connectionState, registry.workspaces.state) { connection, workspace ->
                    connection to workspace
                }
                .collectLatest { (connection, workspace) ->
                    val origin =
                        (workspace as? WorkspaceState.Ready)
                            ?.session
                            ?.takeIf(registry.workspaces::isCurrent)
                    if (origin != null) startChecks(connection, origin)
                    else {
                        checkJob?.cancel()
                        _uiState.value =
                            DoctorUiState(
                                connection = connection,
                                error =
                                    if (workspace == WorkspaceState.Failed) DOCTOR_FAILED_MESSAGE
                                    else null,
                            )
                    }
                }
        }
    }

    fun refresh() {
        registry.retry()
        val origin = registry.workspaces.currentSession() ?: return
        startChecks(client.connectionState.value, origin)
    }

    fun removeStaleEntry(origin: WorkspaceSession, sceneName: String) {
        viewModelScope.launch {
            coroutineResult { registry.remove(origin, sceneName) }
                .onSuccess { if (registry.workspaces.isCurrent(origin)) refresh() }
                .onFailure {
                    if (registry.workspaces.isCurrent(origin)) {
                        _uiState.value =
                            DoctorUiState(
                                connection = client.connectionState.value,
                                error = DOCTOR_FAILED_MESSAGE,
                            )
                    }
                }
        }
    }

    private fun startChecks(connection: ConnectionState, origin: WorkspaceSession) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            coroutineResult { client.withSession(origin.token) { runChecks(connection, origin) } }
                .onFailure { failure ->
                    if (registry.workspaces.isCurrent(origin)) {
                        _uiState.value =
                            DoctorUiState(
                                connection = connection,
                                error = doctorFailureMessage(failure),
                            )
                    }
                }
        }
    }

    private suspend fun runChecks(connection: ConnectionState, origin: WorkspaceSession) {
        _uiState.value = DoctorUiState(connection = connection, running = true, workspace = origin)
        val sceneList = client.getSceneList().scenes
        val obsScenes = sceneList.map { it.name }
        val entries = registry.snapshot(origin)
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
        registry.workspaces.requireCurrent(origin)
        _uiState.value =
            DoctorUiState(
                connection = connection,
                running = false,
                issues = issues,
                ranOnce = true,
                workspace = origin,
            )
    }
}
