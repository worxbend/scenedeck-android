package com.scenedeck.android.feature.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetRunState
import com.scenedeck.android.core.data.PresetStepResult
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.ui.actionLabel
import com.scenedeck.android.core.ui.explanation
import com.scenedeck.android.core.ui.historyDescription
import com.scenedeck.android.core.ui.resultLabel

@Composable
internal fun PresetRunPanel(
    state: PresetRunState,
    busy: Boolean,
    workspaceName: String,
    onAction: (PresetsAction) -> Unit,
) {
    val run = state.run
    val active = run.outcome == PresetRunOutcome.RUNNING && busy
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        ) {
            Text(
                "${if (active) "Running" else "Last run"} · ${run.presetName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(workspaceName, style = MaterialTheme.typography.bodySmall)
            Text(
                run.outcome.description(),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            run.reason?.let { Text(it.explanation()) }
            PresetRunControls(active, run.id, onAction)
            run.steps.forEachIndexed { index, step ->
                Text(
                    "${index + 1}. ${step.target.actionLabel()} · ${step.result.resultLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (run.steps.any { it.result == PresetStepResult.UNKNOWN })
                Text("An unknown write may have taken effect. Check OBS before running again.")
            Text(
                if (busy && !active) "Saving the final result…"
                else state.history.historyDescription(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PresetRunControls(active: Boolean, runId: String, onAction: (PresetsAction) -> Unit) {
    if (active) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onAction(PresetsAction.CancelRun(runId)) }) {
            Text("Stop remaining steps")
        }
        Text(
            "This run continues across tabs. Return here to stop remaining steps.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun PresetRunOutcome.description(): String =
    when (this) {
        PresetRunOutcome.RUNNING -> "Applying and confirming each target in order."
        PresetRunOutcome.COMPLETED -> "Completed · every target was confirmed or already matched."
        PresetRunOutcome.STOPPED -> "Stopped · completed steps were kept."
        PresetRunOutcome.CANCELLED -> "Cancelled · completed steps were kept."
        PresetRunOutcome.INTERRUPTED -> "App interrupted · this run was not resumed."
    }
