package com.scenedeck.android.feature.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.ui.ObsTargetPicker
import com.scenedeck.android.core.ui.label

internal fun LazyListScope.shortcutEditor(
    state: ShortcutsUiState,
    editor: ShortcutEditorState,
    onAction: (ShortcutsAction) -> Unit,
) {
    val enabled = !state.edits.busy && state.workspace.ready?.origin == editor.origin
    val edit: (ShortcutEdit) -> Unit = { onAction(ShortcutsAction.Edit(editor.id, it)) }
    item {
        StudioSectionHeader(if (editor.original == null) "New shortcut" else "Edit shortcut")
        if (state.workspace.ready?.origin != editor.origin)
            Text(
                "The workspace changed. This draft still belongs to ${state.workspace.title(editor.origin)}; " +
                    "close it before editing another workspace."
            )
        ShortcutKeyField(editor, enabled, edit)
    }
    item {
        StudioSectionHeader("Action")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
            ShortcutKind.entries.forEach { kind ->
                FilterChip(
                    editor.kind == kind,
                    { edit(ShortcutEdit.Kind(kind)) },
                    label = { Text(kind.label) },
                    enabled = enabled,
                )
            }
        }
    }
    if (editor.kind.targetType != null)
        item { ShortcutTargetField(state, editor, enabled, edit, onAction) }
    item { ShortcutEditorFooter(state, editor, enabled, edit, onAction) }
}

@Composable
private fun ShortcutKeyField(
    editor: ShortcutEditorState,
    enabled: Boolean,
    edit: (ShortcutEdit) -> Unit,
) {
    var capture by remember(editor.id) { mutableStateOf(false) }
    Text(editor.draft.chord?.label() ?: "No key assigned")
    OutlinedButton(onClick = { capture = true }, enabled = enabled) {
        Text(if (editor.draft.chord == null) "Record key" else "Change key")
    }
    if (capture && enabled)
        ShortcutCaptureDialog({ capture = false }) { chord ->
            capture = false
            edit(ShortcutEdit.Chord(chord))
        }
}

@Composable
private fun ShortcutTargetField(
    state: ShortcutsUiState,
    editor: ShortcutEditorState,
    enabled: Boolean,
    edit: (ShortcutEdit) -> Unit,
    onAction: (ShortcutsAction) -> Unit,
) {
    var choose by remember(editor.id) { mutableStateOf(false) }
    val scene = editor.kind == ShortcutKind.SCENE
    val title = if (scene) "Choose scene" else "Choose input"
    OutlinedTextField(
        editor.name,
        { edit(ShortcutEdit.Name(it)) },
        label = { Text(if (scene) "Exact OBS scene name" else "Exact OBS input name") },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            if (scene) state.workspace.alias(editor.name)?.let { Text("Label: $it") }
        },
    )
    TextButton(onClick = { choose = true }, enabled = enabled) { Text(title) }
    if (choose && enabled)
        ObsTargetPicker(
            title,
            state.workspace.choices(editor.origin, scene),
            "Names are unavailable or still loading. You can enter an exact OBS name in the editor.",
            dismiss = { choose = false },
            select = {
                choose = false
                edit(ShortcutEdit.Name(it))
            },
            retry = { onAction(ShortcutsAction.RefreshTargets) },
        )
}

@Composable
private fun ShortcutEditorFooter(
    state: ShortcutsUiState,
    editor: ShortcutEditorState,
    enabled: Boolean,
    edit: (ShortcutEdit) -> Unit,
    onAction: (ShortcutsAction) -> Unit,
) {
    Text(
        when (editor.kind) {
            ShortcutKind.SCENE ->
                "Selects preview in Studio mode, otherwise program. OBS names stay unchanged."
            ShortcutKind.TAKE ->
                "Takes the current preview using the active OBS transition. Requires Studio mode."
            ShortcutKind.MUTE,
            ShortcutKind.UNMUTE -> "Respects input locks and the audio allow-list."
            else -> "Uses an explicit start or stop target and your Output Safety confirmations."
        }
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Enabled", Modifier.weight(1f))
        Switch(
            editor.draft.enabled,
            { edit(ShortcutEdit.Enabled(it)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Shortcut enabled" },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
        Button(
            onClick = { onAction(ShortcutsAction.Save(editor.id)) },
            enabled = enabled && editor.valid,
        ) {
            Text(if (state.edits.busy) "Saving…" else "Save shortcut")
        }
        TextButton(onClick = { edit(ShortcutEdit.Close) }, enabled = !state.edits.busy) {
            Text("Close editor")
        }
    }
}
