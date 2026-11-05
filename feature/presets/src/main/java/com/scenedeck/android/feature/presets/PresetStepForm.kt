package com.scenedeck.android.feature.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.scenedeck.android.core.data.PresetMediaState
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

@Composable
internal fun PresetStepForm(
    draft: PresetStepEditor,
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
    enabled: Boolean,
    onChange: (PresetEdit) -> Unit,
    retryInputs: () -> Unit,
) {
    val change: (PresetStepEditor) -> Unit = { onChange(PresetEdit.ChangeStep(it)) }
    var picker by remember(draft.id, draft.kind) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
        StudioSectionHeader(if (draft.existingId == null) "Add step" else "Edit step")
        Text("Action", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
            PresetStepKind.entries.forEach { kind ->
                FilterChip(
                    draft.kind == kind,
                    { change(draft.changeKind(kind)) },
                    label = { Text(kind.label) },
                    enabled = enabled,
                )
            }
        }
        PresetTargetField(draft, enabled, change, { picker = true })
        PresetStepValue(draft, enabled, change)
        draft.step()?.let { step ->
            workspace.unavailable(step, origin)?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            "Target availability and support are checked again before running.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { onChange(PresetEdit.ApplyStep) },
            enabled = enabled && draft.step() != null,
        ) {
            Text(if (draft.existingId == null) "Add this step" else "Apply step")
        }
    }
    if (picker)
        PresetTargetPicker(
            draft,
            workspace,
            origin,
            { picker = false },
            { target ->
                change(draft.copy(target = target))
                picker = false
            },
            retryInputs,
        )
}

@Composable
private fun PresetTargetField(
    draft: PresetStepEditor,
    enabled: Boolean,
    change: (PresetStepEditor) -> Unit,
    choose: () -> Unit,
) {
    if (draft.kind.scene || draft.kind.input) {
        OutlinedTextField(
            draft.target,
            { change(draft.copy(target = it.take(1_024))) },
            enabled = enabled,
            label = {
                Text(if (draft.kind.scene) "Exact OBS scene name" else "Exact OBS input name")
            },
            supportingText = {
                Text("Keep spaces and capitalization exactly as OBS shows them.")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = choose, enabled = enabled) {
            Text(if (draft.kind.scene) "Choose scene" else "Choose input")
        }
    }
}

@Composable
private fun PresetStepValue(
    draft: PresetStepEditor,
    enabled: Boolean,
    change: (PresetStepEditor) -> Unit,
) {
    when (draft.kind) {
        PresetStepKind.VOLUME ->
            OutlinedTextField(
                draft.volumePercent,
                { change(draft.copy(volumePercent = it.take(16))) },
                label = { Text("Volume percent") },
                supportingText = { Text("0–100% · 0 is silence") },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        PresetStepKind.MEDIA ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
                PresetMediaState.entries.forEach { state ->
                    FilterChip(
                        draft.media == state,
                        { change(draft.copy(media = state)) },
                        enabled = enabled,
                        label = {
                            Text(
                                when (state) {
                                    PresetMediaState.PLAYING -> "Play"
                                    PresetMediaState.PAUSED -> "Pause"
                                    PresetMediaState.STOPPED -> "Stop"
                                }
                            )
                        },
                    )
                }
            }
        PresetStepKind.MUTE,
        PresetStepKind.STREAM,
        PresetStepKind.RECORDING -> PresetBooleanTarget(draft, enabled, change)
        else -> Unit
    }
}

@Composable
private fun PresetBooleanTarget(
    draft: PresetStepEditor,
    enabled: Boolean,
    change: (PresetStepEditor) -> Unit,
) {
    val on =
        when (draft.kind) {
            PresetStepKind.MUTE -> "Mute"
            PresetStepKind.STREAM -> "Start stream"
            else -> "Start recording"
        }
    val off =
        when (draft.kind) {
            PresetStepKind.MUTE -> "Unmute"
            PresetStepKind.STREAM -> "Stop stream"
            else -> "Stop recording"
        }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
        FilterChip(
            draft.enabled,
            { change(draft.copy(enabled = true)) },
            label = { Text(on) },
            enabled = enabled,
        )
        FilterChip(
            !draft.enabled,
            { change(draft.copy(enabled = false)) },
            label = { Text(off) },
            enabled = enabled,
        )
    }
    if (draft.kind == PresetStepKind.RECORDING)
        Text(
            "Start keeps an already active recording as it is, including its paused state.",
            style = MaterialTheme.typography.bodySmall,
        )
}
