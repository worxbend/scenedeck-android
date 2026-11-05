package com.scenedeck.android.feature.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.scenedeck.android.core.data.MAX_SHORTCUT_BINDINGS
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.ui.actionLabel
import com.scenedeck.android.core.ui.label

internal fun LazyListScope.shortcutList(
    state: ShortcutsUiState,
    onAction: (ShortcutsAction) -> Unit,
    onConnections: () -> Unit,
) {
    when (val saved = state.workspace.saved) {
        is ShortcutState.Ready -> {
            item {
                StudioSectionHeader(
                    "Saved shortcuts · ${saved.bindings.size}/$MAX_SHORTCUT_BINDINGS"
                )
                Text("Active on Scenes, Mixer and Stats while this app is in the foreground.")
                Button(
                    onClick = { onAction(ShortcutsAction.Open(saved.origin)) },
                    enabled = !state.edits.busy && saved.bindings.size < MAX_SHORTCUT_BINDINGS,
                ) {
                    Text("Add shortcut")
                }
                if (saved.bindings.isEmpty())
                    Text("No keys assigned. Add a shortcut, then press its key on a main tab.")
            }
            items(saved.bindings, key = { it.id }) { ShortcutCard(it, state, onAction) }
        }
        is ShortcutState.Loading ->
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading shortcuts…")
            }
        is ShortcutState.Failed -> item { ShortcutLoadFailure(onAction) }
        is ShortcutState.Unavailable ->
            item {
                if (saved.workspace == WorkspaceState.Failed) ShortcutLoadFailure(onAction)
                else {
                    Text("Connect to OBS and select a collection to manage its shortcuts.")
                    OutlinedButton(onClick = onConnections) { Text("Connections") }
                    TextButton(onClick = { onAction(ShortcutsAction.Retry) }) {
                        Text("Retry workspace")
                    }
                }
            }
    }
}

@Composable
private fun ShortcutCard(
    binding: ShortcutBinding,
    state: ShortcutsUiState,
    onAction: (ShortcutsAction) -> Unit,
) {
    val origin = state.workspace.ready?.origin ?: return
    val label = binding.action.actionLabel()
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    binding.chord.label(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                Switch(
                    binding.enabled,
                    { onAction(ShortcutsAction.Toggle(origin, binding)) },
                    enabled = !state.edits.busy,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Enable ${binding.chord.label()}: $label"
                        },
                )
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            (binding.action as? ShortcutAction.Scene)?.let { scene ->
                state.workspace.alias(scene.name)?.let { Text("Label: $it") }
            }
            if (!binding.enabled)
                Text("Disabled · key remains reserved", style = MaterialTheme.typography.bodySmall)
            if (state.workspace.missing(binding.action))
                Text(
                    "Target unavailable · edit to select a current OBS name",
                    color = MaterialTheme.colorScheme.error,
                )
            FlowRow {
                TextButton(
                    onClick = { onAction(ShortcutsAction.Open(origin, binding)) },
                    enabled = !state.edits.busy,
                ) {
                    Text("Edit")
                }
                TextButton(
                    onClick = { onAction(ShortcutsAction.Remove(origin, binding)) },
                    enabled = !state.edits.busy,
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ShortcutLoadFailure(onAction: (ShortcutsAction) -> Unit) {
    Text("Shortcuts are unavailable. Check the connection and try loading again.")
    OutlinedButton(onClick = { onAction(ShortcutsAction.Retry) }) { Text("Retry loading") }
}
