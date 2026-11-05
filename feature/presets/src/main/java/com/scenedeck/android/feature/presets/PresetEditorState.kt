package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.PresetDraft
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.WorkspaceSession
import java.util.UUID

internal data class PresetEditorStep(
    val id: String = UUID.randomUUID().toString(),
    val step: PresetStep,
)

internal data class PresetEditorState(
    val id: String = UUID.randomUUID().toString(),
    val origin: WorkspaceSession,
    val original: ActionPreset?,
    val draft: PresetDraft,
    val steps: List<PresetEditorStep>,
    val stepDraft: PresetStepEditor? = null,
    val discard: Boolean = false,
) {
    fun currentDraft() = draft.copy(steps = steps.map { it.step })

    val dirty: Boolean
        get() = currentDraft() != (original?.draft() ?: PresetDraft(draft.id)) || stepDraft != null
}

internal data class PresetDeletion(val origin: WorkspaceSession, val preset: ActionPreset)

internal data class PresetEditUi(
    val editor: PresetEditorState? = null,
    val deletion: PresetDeletion? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

internal sealed interface PresetEdit {
    data class Name(val text: String) : PresetEdit

    data class EditStep(val stepId: String?) : PresetEdit

    data class ChangeStep(val draft: PresetStepEditor) : PresetEdit

    data class Move(val stepId: String, val offset: Int) : PresetEdit

    data class Remove(val stepId: String) : PresetEdit

    data object ApplyStep : PresetEdit

    data object CancelStep : PresetEdit

    data object Close : PresetEdit

    data object Discard : PresetEdit

    data object KeepEditing : PresetEdit
}
