package com.scenedeck.android.feature.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.MAX_ACTION_PRESETS
import com.scenedeck.android.core.data.PresetState
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

internal fun LazyListScope.presetList(
    state: PresetsUiState,
    onAction: (PresetsAction) -> Unit,
    onNavigate: (PresetsDestination) -> Unit,
) {
    item {
        Text("Run a saved set of scene, audio, media and output controls in order.")
        FlowRow {
            TextButton(onClick = { onNavigate(PresetsDestination.PREFLIGHT) }) { Text("Preflight") }
            TextButton(onClick = { onNavigate(PresetsDestination.HISTORY) }) { Text("History") }
        }
        val notice = state.review.notice ?: state.edits.message
        if (notice != null && state.edits.deletion == null)
            Text(notice, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    state.run?.let { run ->
        item { PresetRunPanel(run, state.running, state.workspace.title(run.origin), onAction) }
    }
    when (val saved = state.workspace.saved) {
        is PresetState.Ready -> {
            item {
                StudioSectionHeader("Saved presets · ${saved.presets.size}/$MAX_ACTION_PRESETS")
                Button(
                    onClick = { onAction(PresetsAction.Editor.Open(saved.origin)) },
                    enabled =
                        !state.running &&
                            !state.edits.busy &&
                            saved.presets.size < MAX_ACTION_PRESETS,
                ) {
                    Text("Create preset")
                }
                if (saved.presets.isEmpty())
                    Text("Create a preset, add its steps, then review the targets before running.")
            }
            items(saved.presets, key = { it.id }) { preset ->
                PresetListCard(preset, state, !state.running && !state.edits.busy, onAction)
            }
        }
        is PresetState.Loading ->
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading presets…")
            }
        is PresetState.Failed -> item { PresetLoadFailure(onAction) }
        is PresetState.Unavailable ->
            item {
                if (saved.workspace == WorkspaceState.Failed) PresetLoadFailure(onAction)
                else {
                    Text("Connect to OBS and select a collection to manage its presets.")
                    OutlinedButton(onClick = { onNavigate(PresetsDestination.CONNECTIONS) }) {
                        Text("Connections")
                    }
                    TextButton(onClick = { onAction(PresetsAction.Retry) }) {
                        Text("Retry workspace")
                    }
                }
            }
    }
}

@Composable
private fun PresetListCard(
    preset: ActionPreset,
    state: PresetsUiState,
    enabled: Boolean,
    onAction: (PresetsAction) -> Unit,
) {
    val origin = state.workspace.ready?.origin ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        ) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            Text("${preset.steps.size} ordered steps", style = MaterialTheme.typography.bodySmall)
            val missing = preset.steps.count { state.workspace.unavailable(it, origin) != null }
            if (missing > 0)
                Text(
                    "$missing unavailable references · edit or review",
                    color = MaterialTheme.colorScheme.error,
                )
            FlowRow {
                Button(
                    onClick = { onAction(PresetsAction.Review.Open(origin, preset)) },
                    enabled = enabled,
                ) {
                    Text("Review & run")
                }
                TextButton(
                    onClick = { onAction(PresetsAction.Editor.Open(origin, preset)) },
                    enabled = enabled,
                ) {
                    Text("Edit")
                }
                TextButton(
                    onClick = { onAction(PresetsAction.Editor.Remove(origin, preset)) },
                    enabled = enabled,
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PresetLoadFailure(onAction: (PresetsAction) -> Unit) {
    Text("Presets are unavailable. Check the connection and try loading again.")
    OutlinedButton(onClick = { onAction(PresetsAction.Retry) }) { Text("Retry loading") }
}
