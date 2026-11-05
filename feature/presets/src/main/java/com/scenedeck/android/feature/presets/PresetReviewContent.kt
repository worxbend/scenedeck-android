package com.scenedeck.android.feature.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.ui.actionLabel

internal fun LazyListScope.presetReview(
    state: PresetsUiState,
    review: PresetReview,
    onAction: (PresetsAction) -> Unit,
    onNavigate: (PresetsDestination) -> Unit,
) {
    item {
        TextButton(onClick = { onAction(PresetsAction.Review.Close) }) { Text("Back to presets") }
        StudioSectionHeader("Review · ${review.preset.name}")
        Text(
            "Each target is checked again during the run. Completed steps stay applied if the run stops."
        )
        if (review.phase == PresetReviewPhase.CHECKING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Checking targets…")
        }
        review.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    review.preset.steps.forEachIndexed { index, step ->
        item(key = "review-$index") {
            PresetReviewStep(review, step, index, state.workspace, onAction)
        }
    }
    item {
        FlowRow {
            Button(
                onClick = { onAction(PresetsAction.Review.Run(review.id)) },
                enabled = review.canRun && !state.running,
            ) {
                Text("Run ${review.preset.steps.size} steps")
            }
            if (review.phase != PresetReviewPhase.CHECKING)
                OutlinedButton(
                    onClick = { onAction(PresetsAction.Review.Retry(review.id)) },
                    enabled = !state.running,
                ) {
                    Text("Prepare again")
                }
        }
        if (review.phase == PresetReviewPhase.READY)
            Text(
                "This review expires after one minute. A run continues when you change tabs.",
                style = MaterialTheme.typography.bodySmall,
            )
        FlowRow {
            TextButton(onClick = { onNavigate(PresetsDestination.PREFLIGHT) }) {
                Text("Open Preflight")
            }
            TextButton(onClick = { onNavigate(PresetsDestination.SETTINGS) }) {
                Text("Output Safety settings")
            }
            TextButton(
                onClick = { onAction(PresetsAction.Editor.Open(review.origin, review.preset)) },
                enabled = !state.running,
            ) {
                Text("Edit preset")
            }
        }
    }
}

@Composable
private fun PresetReviewStep(
    review: PresetReview,
    step: PresetStep,
    index: Int,
    workspace: PresetWorkspaceUi,
    onAction: (PresetsAction) -> Unit,
) {
    val check = review.plan?.checks?.getOrNull(index)
    val confirm = index in review.plan?.outputConfirmations.orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        ) {
            Text(
                "${index + 1}. ${step.displayLabel(workspace, review.origin)}",
                style = MaterialTheme.typography.titleMedium,
            )
            PresetLiteralSceneName(step, workspace, review.origin)
            Text(
                check?.description() ?: "Not checked",
                color =
                    if (check != null && !check.eligible) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (confirm) {
                val checked = index in review.acknowledged
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            checked,
                            role = Role.Checkbox,
                        ) {
                            onAction(PresetsAction.Review.Acknowledge(review.id, index, it))
                        },
                ) {
                    Checkbox(checked, null)
                    Text("Confirm: ${step.actionLabel()}", Modifier.weight(1f))
                }
            }
        }
    }
}
