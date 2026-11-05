package com.scenedeck.android.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.HistoryCapturePhase
import com.scenedeck.android.core.data.HistoryDetailState
import com.scenedeck.android.core.data.HistorySession
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.mono

internal fun LazyListScope.historyDetail(state: HistoryUiState, actions: HistoryActions) {
    item { TextButton(onClick = { actions.open(null) }) { Text("All history") } }
    if (state.capture.phase == HistoryCapturePhase.FAILED)
        item { HistoryCaptureStatus(state.capture, actions.retry) }
    when (val detail = state.selection.detail) {
        HistoryDetailState.Loading,
        null -> item { Text("Loading observation…") }
        HistoryDetailState.Failed -> item { HistoryReadFailure(actions.retry) }
        HistoryDetailState.Missing ->
            item {
                Text(
                    "This observation was deleted or removed by retention. Saved bookmarks remain in Bookmarks."
                )
            }
        is HistoryDetailState.Ready -> {
            val observation = detail.detail
            item { HistoryObservationHeader(observation.session, state, actions) }
            item {
                Text(
                    "Elapsed time starts with this observation, separately from OBS output timers. " +
                        "Initial readings describe what SceneDeck saw on arrival.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Bookmark labels reflect current saved bookmarks. Edit or delete them in Bookmarks.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = actions.bookmarks) { Text("Open bookmarks") }
            }
            item { StudioSectionHeader("Timeline") }
            val timeline = observation.timeline()
            if (timeline.isEmpty()) item { Text("No entries were saved in this observation.") }
            items(timeline, key = { it.key }) { HistoryTimelineRow(it) }
        }
    }
}

@Composable
private fun HistoryObservationHeader(
    session: HistorySession,
    state: HistoryUiState,
    actions: HistoryActions,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(session.workspace.collectionName, style = MaterialTheme.typography.titleLarge)
            Text(
                state.connectionNames[session.workspace.profileId]
                    ?: "Removed connection #${session.workspace.profileId}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Started · ${bookmarkDate(session.startedAtMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Last observed · ${bookmarkDate(session.lastObservedAtMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Observed elapsed · ${historyElapsed(session.elapsedMs)}",
                style = MaterialTheme.typography.mono,
            )
            Text(session.endText(state.capture), style = MaterialTheme.typography.bodySmall)
            HistoryOmissions(session)
            FlowRow {
                OutlinedButton(onClick = { actions.export(session.id) }, enabled = !state.busy) {
                    Text("Export observation")
                }
                TextButton(onClick = { actions.delete(session) }, enabled = !state.busy) {
                    Text("Delete observation")
                }
            }
        }
    }
}

@Composable
private fun HistoryTimelineRow(item: HistoryTimelineItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("+${historyElapsed(item.elapsedMs)}", style = MaterialTheme.typography.mono)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(bookmarkDate(item.atMs), style = MaterialTheme.typography.bodySmall)
            item.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
