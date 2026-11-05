package com.scenedeck.android.feature.preflight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.PreflightConfig
import com.scenedeck.android.core.data.PreflightReport
import com.scenedeck.android.core.data.PreflightRepository
import com.scenedeck.android.core.data.PreflightRunner
import com.scenedeck.android.core.data.PreflightSetupState
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class PreflightRunState(
    val target: PreflightSetupState.Ready? = null,
    val running: Boolean = false,
    val report: PreflightReport? = null,
    val error: String? = null,
)

internal data class PreflightUiState(
    val setup: PreflightSetupState = PreflightSetupState.Unavailable(WorkspaceState.Unavailable),
    val run: PreflightRunState = PreflightRunState(),
    val scenes: List<SceneCardState> = emptyList(),
    val microphones: List<String> = emptyList(),
    val connectionName: String = "Current connection",
)

@HiltViewModel
class PreflightViewModel
@Inject
constructor(
    private val repository: PreflightRepository,
    private val runner: PreflightRunner,
    client: ObsClient,
    obsState: ObsStateRepository,
    mixer: MixerRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    val connection = client.connectionState
    private val setup =
        repository.state.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PreflightSetupState.Unavailable(repository.workspaces.state.value),
        )
    private val runState = MutableStateFlow(PreflightRunState())
    private var runJob: Job? = null
    private var saving = false
    internal val uiState =
        combine(setup, runState, obsState.deckState, mixer.mixerState, profiles.profiles) {
                config,
                run,
                deck,
                audio,
                connections ->
                val target = config as? PreflightSetupState.Ready
                val current = target?.takeIf { repository.workspaces.isCurrent(it.origin) }
                PreflightUiState(
                    setup =
                        if (target != null && current == null)
                            PreflightSetupState.Unavailable(repository.workspaces.state.value)
                        else config,
                    run =
                        run.takeIf { current != null && it.target == current }
                            ?: PreflightRunState(),
                    scenes =
                        deck.allScenes
                            .takeIf { current != null && deck.workspace == current.origin }
                            .orEmpty(),
                    microphones =
                        audio.inputs
                            .takeIf { current != null && audio.workspace == current.origin }
                            .orEmpty()
                            .map { it.name },
                    connectionName =
                        connections.find { it.id == current?.origin?.key?.profileId }?.name
                            ?: "Current connection",
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreflightUiState())

    init {
        viewModelScope.launch {
            setup.collect { current ->
                if (runState.value.target != null && runState.value.target != current) {
                    runJob?.cancel()
                    runState.value = PreflightRunState()
                }
            }
        }
    }

    fun retry() = repository.retry()

    internal fun run(target: PreflightSetupState.Ready, signal: Boolean) {
        if (!isCurrent(target) || saving || runJob?.isActive == true) return
        runState.value = PreflightRunState(target, running = true)
        runJob = viewModelScope.launch {
            val result = coroutineResult { runner.run(target.origin, target.config, signal) }
            if (isCurrent(target))
                runState.value =
                    PreflightRunState(
                        target,
                        report = result.getOrNull(),
                        error =
                            if (result.isFailure)
                                "Checks couldn't finish. Re-run when OBS is available."
                            else null,
                    )
        }
    }

    internal fun cancel() {
        runJob?.cancel()
        runState.value =
            PreflightRunState(runState.value.target, error = "Checks canceled. Re-run when ready.")
    }

    internal suspend fun save(target: PreflightSetupState.Ready, draft: PreflightConfig): Boolean {
        if (saving || !isCurrent(target)) return false
        saving = true
        runJob?.cancel()
        runState.value = PreflightRunState()
        return try {
            coroutineResult { repository.save(target.origin, target.config, draft) }.isSuccess
        } finally {
            saving = false
        }
    }

    internal fun completeManual(target: PreflightSetupState.Ready, id: String, checked: Boolean) {
        viewModelScope.launch {
            val draft =
                target.config.copy(
                    manualChecks =
                        target.config.manualChecks.map {
                            if (it.id == id) it.copy(complete = checked) else it
                        }
                )
            if (!save(target, draft) && isCurrent(target))
                runState.value =
                    PreflightRunState(
                        target,
                        error = "Couldn't save the checklist. Try again.",
                    )
        }
    }

    private fun isCurrent(target: PreflightSetupState.Ready) =
        setup.value == target && repository.workspaces.isCurrent(target.origin)
}
