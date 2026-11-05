package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.MAX_PRESET_NAME_LENGTH
import com.scenedeck.android.core.data.MAX_PRESET_STEPS
import com.scenedeck.android.core.data.PresetDraft
import com.scenedeck.android.core.data.PresetEditException
import com.scenedeck.android.core.data.PresetEditFailure
import com.scenedeck.android.core.data.PresetRepository
import com.scenedeck.android.core.data.WorkspaceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Draft identities and optimistic originals survive failed writes and configuration changes. */
internal class PresetEditorController(
    private val repository: PresetRepository,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow(PresetEditUi())
    val state = current.asStateFlow()

    fun open(origin: WorkspaceSession, preset: ActionPreset?) {
        if (
            current.value.busy ||
                current.value.editor != null ||
                !repository.workspaces.isCurrent(origin)
        )
            return
        val draft = preset?.draft() ?: PresetDraft()
        current.value =
            PresetEditUi(
                editor =
                    PresetEditorState(
                        origin = origin,
                        original = preset,
                        draft = draft,
                        steps = draft.steps.map { PresetEditorStep(step = it) },
                    )
            )
    }

    fun edit(id: String, action: PresetEdit) {
        val editor = current.value.editor?.takeIf { it.id == id && !current.value.busy } ?: return
        current.value = current.value.copy(editor = editor.reduce(action), message = null)
    }

    fun save(id: String) {
        val editor = current.value.editor?.takeIf { it.id == id && !current.value.busy } ?: return
        if (editor.stepDraft != null) return
        current.value = current.value.copy(busy = true, message = null)
        scope.launch {
            try {
                val result = coroutineResult {
                    repository.save(editor.origin, editor.original, editor.currentDraft())
                }
                current.value =
                    if (result.isSuccess) PresetEditUi(message = "Preset saved.")
                    else current.value.copy(message = result.exceptionOrNull().editFailure())
            } finally {
                current.value = current.value.copy(busy = false)
            }
        }
    }

    fun requestDelete(origin: WorkspaceSession, preset: ActionPreset) {
        if (
            current.value.busy ||
                current.value.editor != null ||
                !repository.workspaces.isCurrent(origin)
        )
            return
        current.value = PresetEditUi(deletion = PresetDeletion(origin, preset))
    }

    fun dismissDelete() {
        if (!current.value.busy && current.value.editor == null) current.value = PresetEditUi()
    }

    fun delete(target: PresetDeletion) {
        if (current.value.deletion != target || current.value.busy) return
        current.value = current.value.copy(busy = true, message = null)
        scope.launch {
            try {
                val result = coroutineResult { repository.delete(target.origin, target.preset) }
                current.value =
                    if (result.isSuccess) PresetEditUi(message = "Preset deleted.")
                    else
                        current.value.copy(
                            message =
                                "Couldn't delete this preset. It may have changed; reload before trying again."
                        )
            } finally {
                current.value = current.value.copy(busy = false)
            }
        }
    }
}

private fun PresetEditorState.reduce(action: PresetEdit): PresetEditorState? =
    when (action) {
        is PresetEdit.Name ->
            copy(draft = draft.copy(name = action.text.take(MAX_PRESET_NAME_LENGTH)))
        is PresetEdit.EditStep -> editStep(action.stepId)
        is PresetEdit.ChangeStep -> changeStep(action.draft)
        is PresetEdit.Move -> move(action.stepId, action.offset)
        is PresetEdit.Remove -> copy(steps = steps.filterNot { it.id == action.stepId })
        PresetEdit.ApplyStep -> applyStep()
        PresetEdit.CancelStep -> copy(stepDraft = null)
        PresetEdit.Close -> if (dirty) copy(discard = true) else null
        PresetEdit.Discard -> null
        PresetEdit.KeepEditing -> copy(discard = false)
    }

private fun PresetEditorState.editStep(id: String?): PresetEditorState =
    if (id == null) {
        if (steps.size < MAX_PRESET_STEPS) copy(stepDraft = PresetStepEditor()) else this
    } else steps.find { it.id == id }?.let { copy(stepDraft = it.editor()) } ?: this

private fun PresetEditorState.changeStep(draft: PresetStepEditor): PresetEditorState =
    if (draft.id == stepDraft?.id && draft.existingId == stepDraft.existingId)
        copy(stepDraft = draft)
    else this

private fun PresetEditorState.move(id: String, offset: Int): PresetEditorState {
    val from = steps.indexOfFirst { it.id == id }
    val to = from + offset
    if (from < 0 || to !in steps.indices || offset !in setOf(-1, 1)) return this
    val moved = steps.toMutableList().apply { add(to, removeAt(from)) }
    return copy(steps = moved)
}

private fun PresetEditorState.applyStep(): PresetEditorState {
    val step = stepDraft?.step() ?: return this
    val editing = checkNotNull(stepDraft)
    val updated =
        when {
            editing.existingId != null ->
                steps.map { if (it.id == editing.existingId) it.copy(step = step) else it }
            steps.size < MAX_PRESET_STEPS -> steps + PresetEditorStep(step = step)
            else -> steps
        }
    return copy(steps = updated, stepDraft = null)
}

private fun Throwable?.editFailure(): String =
    when ((this as? PresetEditException)?.reason) {
        PresetEditFailure.DUPLICATE_NAME ->
            "That preset name is already used in this workspace. Choose another name."
        PresetEditFailure.LIMIT ->
            "This workspace already has 32 presets. Remove one before saving another."
        PresetEditFailure.INVALID -> "Enter a name and 1–12 valid steps. Your draft is still here."
        PresetEditFailure.CHANGED ->
            "The saved preset changed. Your draft is still here; close and reopen to use the latest version."
        else ->
            "Couldn't save this preset. Your draft is still here. Check the connection and workspace, then try again."
    }
