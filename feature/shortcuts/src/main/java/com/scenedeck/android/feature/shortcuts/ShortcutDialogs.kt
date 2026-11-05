package com.scenedeck.android.feature.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.ui.actionLabel
import com.scenedeck.android.core.ui.label

@Composable
internal fun ShortcutEditDialogs(state: ShortcutsUiState, onAction: (ShortcutsAction) -> Unit) {
    state.edits.editor
        ?.takeIf { it.discard }
        ?.let { editor ->
            val keep = { onAction(ShortcutsAction.Edit(editor.id, ShortcutEdit.KeepEditing)) }
            AlertDialog(
                onDismissRequest = keep,
                title = { Text("Discard shortcut changes?") },
                text = { Text("Your unsaved changes will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onAction(ShortcutsAction.Edit(editor.id, ShortcutEdit.Discard))
                        }
                    ) {
                        Text("Discard changes")
                    }
                },
                dismissButton = { TextButton(onClick = keep) { Text("Keep editing") } },
            )
        }
    state.edits.deletion?.let { target ->
        val dismiss = { onAction(ShortcutsAction.DismissDelete) }
        AlertDialog(
            onDismissRequest = { if (!state.edits.busy) dismiss() },
            title = { Text("Delete ${target.binding.chord.label()}?") },
            text = {
                Column(
                    Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
                ) {
                    Text(target.binding.action.actionLabel())
                    Text(
                        "Remove this binding from ${state.workspace.title(target.origin)} and free its key."
                    )
                    state.edits.message?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(ShortcutsAction.Delete(target)) },
                    enabled = !state.edits.busy && state.workspace.ready?.origin == target.origin,
                ) {
                    Text("Delete shortcut")
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss, enabled = !state.edits.busy) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun ShortcutHelp(dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Keyboard & foot pedals") },
        text = {
            Column(
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.sectionGap),
            ) {
                Text(
                    "Bindings belong to this connection and OBS collection. No keys are assigned " +
                        "automatically. Disabled bindings reserve their keys; Delete frees them."
                )
                Text(
                    "Use A–Z, 0–9, F1–F12 or numpad digits, with optional Ctrl, Alt, Shift or Meta. Left " +
                        "and right modifiers are equivalent. Caps Lock and Num Lock do not change a binding."
                )
                Text(
                    "A compatible pedal must send Android keyboard events. Configure it to emit a " +
                        "supported key such as F8. Device-specific setup is outside this app."
                )
                Text(
                    "Shortcuts work on Scenes, Mixer and Stats while the app is foreground and focused. " +
                        "Text entry, menus, dialogs and system navigation keep priority. Holding a key runs it " +
                        "once."
                )
                Text(
                    "Scene selection follows Studio mode; Take requires a current preview. Input locks and " +
                        "Output Safety still apply. Output confirmation is single-use and expires after one " +
                        "minute."
                )
                Text(
                    "Shortcuts do not run in the background. Renamed or removed targets must be edited. " +
                        "Unknown results are never retried automatically; check OBS before trying again."
                )
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close help") } },
    )
}
