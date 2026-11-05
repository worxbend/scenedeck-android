package com.scenedeck.android.feature.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.scenedeck.android.core.data.MAX_PRESET_NAME_LENGTH
import com.scenedeck.android.core.data.MAX_PRESET_STEPS
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

internal fun LazyListScope.presetEditor(
    state: PresetsUiState,
    editor: PresetEditorState,
    onAction: (PresetsAction) -> Unit,
) {
    val current = state.workspace.ready?.origin == editor.origin
    val enabled = current && !state.edits.busy && !state.running
    val change: (PresetEdit) -> Unit = { onAction(PresetsAction.Editor.Change(editor.id, it)) }
    item { PresetEditorToolbar(state, editor, enabled, change, onAction) }
    val stepDraft = editor.stepDraft
    if (stepDraft != null) {
        item {
            PresetStepForm(
                stepDraft,
                state.workspace,
                editor.origin,
                enabled,
                change,
                { onAction(PresetsAction.RefreshInputs) },
            )
        }
    } else {
        item { PresetEditorIdentity(editor, enabled, change) }
        itemsIndexed(editor.steps, key = { _, step -> step.id }) { index, step ->
            PresetEditorCard(state.workspace, editor, step, index, enabled, change)
        }
        item {
            OutlinedButton(
                onClick = { change(PresetEdit.EditStep(null)) },
                enabled = enabled && editor.steps.size < MAX_PRESET_STEPS,
            ) {
                Text("Add step")
            }
            if (editor.steps.isEmpty())
                Text(
                    "Add scene, audio, media or output targets in the order you want them applied."
                )
        }
    }
}

@Composable
private fun PresetEditorToolbar(
    state: PresetsUiState,
    editor: PresetEditorState,
    enabled: Boolean,
    change: (PresetEdit) -> Unit,
    onAction: (PresetsAction) -> Unit,
) {

    val exit = if (editor.stepDraft != null) PresetEdit.CancelStep else PresetEdit.Close
    val exitLabel = if (editor.stepDraft != null) "Cancel step" else "Back to presets"
    val saveLabel = if (state.edits.busy) "Saving…" else "Save preset"
    FlowRow {
        TextButton(
            onClick = { change(exit) },
            enabled = !state.edits.busy,
        ) {
            Text(exitLabel)
        }
        if (editor.stepDraft == null)
            Button(
                onClick = { onAction(PresetsAction.Editor.Save(editor.id)) },
                enabled =
                    enabled &&
                        editor.draft.name.isNotBlank() &&
                        editor.steps.size in 1..MAX_PRESET_STEPS,
            ) {
                Text(saveLabel)
            }
    }
    if (state.workspace.ready?.origin != editor.origin)
        Text(
            "The workspace changed. This draft still belongs to ${state.workspace.title(editor.origin)}. " +
                "Close and reopen it to continue.",
            color = MaterialTheme.colorScheme.error,
        )
    state.edits.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun PresetEditorIdentity(
    editor: PresetEditorState,
    enabled: Boolean,
    change: (PresetEdit) -> Unit,
) {

    StudioSectionHeader(if (editor.original == null) "Create preset" else "Edit preset")
    OutlinedTextField(
        editor.draft.name,
        { change(PresetEdit.Name(it)) },
        enabled = enabled,
        label = { Text("Preset name") },
        supportingText = {
            Text("${editor.draft.name.length}/$MAX_PRESET_NAME_LENGTH · unique in this workspace")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Changes are saved on this device. Review checks each target before you run.",
        style = MaterialTheme.typography.bodySmall,
    )
    StudioSectionHeader("Steps · ${editor.steps.size}/$MAX_PRESET_STEPS")
}

@Composable
private fun PresetEditorCard(
    workspace: PresetWorkspaceUi,
    editor: PresetEditorState,
    item: PresetEditorStep,
    index: Int,
    enabled: Boolean,
    change: (PresetEdit) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        ) {
            Text(
                "${index + 1}. ${item.step.displayLabel(workspace, editor.origin)}",
                style = MaterialTheme.typography.titleMedium,
            )
            PresetLiteralSceneName(item.step, workspace, editor.origin)
            workspace.unavailable(item.step, editor.origin)?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            FlowRow {
                TextButton(onClick = { change(PresetEdit.EditStep(item.id)) }, enabled = enabled) {
                    Text("Edit step")
                }
                TextButton(
                    onClick = { change(PresetEdit.Move(item.id, -1)) },
                    enabled = enabled && index > 0,
                    modifier =
                        Modifier.semantics { contentDescription = "Move step ${index + 1} up" },
                ) {
                    Text("Move up")
                }
                TextButton(
                    onClick = { change(PresetEdit.Move(item.id, 1)) },
                    enabled = enabled && index < editor.steps.lastIndex,
                    modifier =
                        Modifier.semantics { contentDescription = "Move step ${index + 1} down" },
                ) {
                    Text("Move down")
                }
                TextButton(
                    onClick = { change(PresetEdit.Remove(item.id)) },
                    enabled = enabled,
                    modifier =
                        Modifier.semantics { contentDescription = "Remove step ${index + 1}" },
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
internal fun PresetLiteralSceneName(
    step: PresetStep,
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
) {
    if (
        step is PresetStep.Scene &&
            workspace.ready?.origin == origin &&
            step.name in workspace.aliases
    )
        Text("OBS: ${step.name}", style = MaterialTheme.typography.bodySmall)
}
