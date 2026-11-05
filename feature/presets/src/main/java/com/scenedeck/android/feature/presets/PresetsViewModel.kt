package com.scenedeck.android.feature.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.MonotonicClock
import com.scenedeck.android.core.data.PresetExecutionSource
import com.scenedeck.android.core.data.PresetPreparation
import com.scenedeck.android.core.data.PresetRepository
import com.scenedeck.android.core.data.PresetRunLauncher
import com.scenedeck.android.core.data.PresetRunState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class PresetsUiState(
    val workspace: PresetWorkspaceUi = PresetWorkspaceUi(),
    val edits: PresetEditUi = PresetEditUi(),
    val review: PresetReviewUi = PresetReviewUi(),
    val run: PresetRunState? = null,
    val running: Boolean = false,
)

@HiltViewModel
class PresetsViewModel
@Inject
constructor(
    private val repository: PresetRepository,
    preparation: PresetPreparation,
    source: PresetExecutionSource,
    private val launcher: PresetRunLauncher,
    clock: MonotonicClock,
    private val workspace: PresetWorkspace,
) : ViewModel() {
    private val editor = PresetEditorController(repository, viewModelScope)
    private val reviews =
        PresetReviewController(repository, preparation, source, launcher, clock, viewModelScope)
    private val projection =
        workspace.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PresetWorkspaceUi(),
        )
    private val execution = combine(launcher.state, launcher.busy) { state, busy -> state to busy }
    internal val uiState =
        combine(projection, editor.state, reviews.state, execution) {
                current,
                edits,
                review,
                running ->
                PresetsUiState(current, edits, review, running.first, running.second)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState())

    init {
        viewModelScope.launch {
            repository.workspaces.state.collect {
                reviews.state.value.review?.let { review ->
                    if (!repository.workspaces.isCurrent(review.origin)) reviews.close()
                }
            }
        }
    }

    internal fun onAction(action: PresetsAction) {
        when (action) {
            is PresetsAction.Editor -> edit(action)
            is PresetsAction.Review -> review(action)
            is PresetsAction.CancelRun -> launcher.cancel(action.id)
            PresetsAction.Retry -> {
                repository.retry()
                workspace.retryTargets()
            }
            PresetsAction.RefreshInputs -> workspace.retryTargets()
            PresetsAction.LeaveScreen -> reviews.close()
        }
    }

    private fun edit(action: PresetsAction.Editor) {
        if (launcher.busy.value) return
        when (action) {
            is PresetsAction.Editor.Open -> {
                reviews.close()
                editor.open(action.origin, action.preset)
            }
            is PresetsAction.Editor.Change -> editor.edit(action.id, action.event)
            is PresetsAction.Editor.Save -> editor.save(action.id)
            is PresetsAction.Editor.Remove -> {
                reviews.close()
                editor.requestDelete(action.origin, action.preset)
            }
            is PresetsAction.Editor.ConfirmDelete -> editor.delete(action.target)
            PresetsAction.Editor.DismissDelete -> editor.dismissDelete()
        }
    }

    private fun review(action: PresetsAction.Review) {
        if (editor.state.value.busy || editor.state.value.editor != null) return
        when (action) {
            is PresetsAction.Review.Open -> {
                editor.dismissDelete()
                reviews.prepare(action.origin, action.preset)
            }
            is PresetsAction.Review.Acknowledge ->
                reviews.acknowledge(action.id, action.index, action.checked)
            is PresetsAction.Review.Run -> reviews.run(action.id)
            is PresetsAction.Review.Retry -> retryReview(action.id)
            PresetsAction.Review.Close -> reviews.close()
        }
    }

    private fun retryReview(id: String) {
        val selected = reviews.state.value.review?.takeIf { it.id == id } ?: return
        val ready = projection.value.ready?.takeIf { it.origin == selected.origin } ?: return
        ready.presets
            .find { it.id == selected.preset.id }
            ?.let { reviews.prepare(ready.origin, it) }
    }
}
