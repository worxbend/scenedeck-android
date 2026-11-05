package com.scenedeck.android.feature.presets

import androidx.compose.runtime.Composable
import com.scenedeck.android.core.data.PresetTargets
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.ui.ObsTargetChoice
import com.scenedeck.android.core.ui.ObsTargetPicker

@Composable
internal fun PresetTargetPicker(
    draft: PresetStepEditor,
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
    dismiss: () -> Unit,
    select: (String) -> Unit,
    retry: () -> Unit,
) {
    ObsTargetPicker(
        title = if (draft.kind.scene) "Choose scene" else "Choose input",
        choices = targetChoices(draft, workspace, origin),
        unavailableText =
            "Names are unavailable or still loading. You can enter an exact OBS name in the step.",
        dismiss = dismiss,
        select = select,
        retry = retry.takeIf { draft.kind.input },
    )
}

private fun targetChoices(
    draft: PresetStepEditor,
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
): List<ObsTargetChoice>? {
    if (workspace.ready?.origin != origin) return null
    return if (draft.kind.scene)
        workspace.scenes?.map { ObsTargetChoice(it.name, it.displayName, "OBS: ${it.name}") }
    else
        (workspace.targets as? PresetTargets.Ready)
            ?.takeIf { it.origin == origin }
            ?.inputs
            ?.map { ObsTargetChoice(it.name, it.name, null) }
}
