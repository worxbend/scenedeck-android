package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.WorkspaceSession

internal sealed interface PresetsAction {
    sealed interface Editor : PresetsAction {
        data class Open(val origin: WorkspaceSession, val preset: ActionPreset? = null) : Editor

        data class Change(val id: String, val event: PresetEdit) : Editor

        data class Save(val id: String) : Editor

        data class Remove(val origin: WorkspaceSession, val preset: ActionPreset) : Editor

        data class ConfirmDelete(val target: PresetDeletion) : Editor

        data object DismissDelete : Editor
    }

    sealed interface Review : PresetsAction {
        data class Open(val origin: WorkspaceSession, val preset: ActionPreset) : Review

        data class Retry(val id: String) : Review

        data class Acknowledge(val id: String, val index: Int, val checked: Boolean) : Review

        data class Run(val id: String) : Review

        data object Close : Review
    }

    data class CancelRun(val id: String) : PresetsAction

    data object Retry : PresetsAction

    data object RefreshInputs : PresetsAction

    data object LeaveScreen : PresetsAction
}
