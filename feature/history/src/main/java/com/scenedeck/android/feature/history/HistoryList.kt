package com.scenedeck.android.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.HistoryCapturePhase
import com.scenedeck.android.core.data.HistoryCaptureState
import com.scenedeck.android.core.data.HistoryPreferences
import com.scenedeck.android.core.data.HistorySession
import com.scenedeck.android.core.data.HistoryState
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

internal fun LazyListScope.historyList(state: HistoryUiState, actions: HistoryActions) {
    item { HistoryCaptureStatus(state.capture, actions.retry) }
    when (val saved = state.saved) {
        HistoryState.Loading -> item { Text("Loading saved history…") }
        HistoryState.Failed -> item { HistoryReadFailure(actions.retry) }
        is HistoryState.Ready -> {
            item { HistorySettings(saved.preferences, state.busy, actions.configure) }
            item {
                FlowRow {
                    OutlinedButton(onClick = { actions.export(null) }, enabled = !state.busy) {
                        Text("Export history & bookmarks")
                    }
                    TextButton(
                        onClick = { actions.delete(null) },
                        enabled = saved.sessions.isNotEmpty() && !state.busy,
                    ) {
                        Text("Clear history")
                    }
                    TextButton(onClick = actions.bookmarks) { Text("Bookmarks") }
                }
                Text(
                    "Exports include scene and collection names. Choose where to save; " +
                        "nothing is uploaded automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item { StudioSectionHeader("Observations") }
            if (saved.sessions.isEmpty())
                item {
                    Text(
                        "No observations yet. History begins when capture is enabled and an OBS workspace is connected."
                    )
                }
            items(saved.sessions, key = { it.id }) { session ->
                HistorySessionCard(session, state, actions)
            }
        }
    }
}

@Composable
internal fun HistoryCaptureStatus(capture: HistoryCaptureState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SceneDeckTheme.spacing.rowPadding)) {
            Text(
                when (capture.phase) {
                    HistoryCapturePhase.STARTING -> "Starting local history…"
                    HistoryCapturePhase.WAITING -> "Waiting for a connected OBS workspace"
                    HistoryCapturePhase.CAPTURING ->
                        "Capturing local history · ${capture.workspace?.collectionName.orEmpty()}"
                    HistoryCapturePhase.DISABLED -> "History capture is off"
                    HistoryCapturePhase.FAILED -> "History capture paused"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            if (capture.phase == HistoryCapturePhase.FAILED) {
                Text(
                    "Some changes were not saved. Retry starts a new observation.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRetry) { Text("Retry capture") }
            } else if (capture.missedEvents > 0)
                Text(
                    "Some history entries were missed; gaps are marked in the observation.",
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    }
}

@Composable
private fun HistorySettings(
    preferences: HistoryPreferences,
    busy: Boolean,
    onChange: (Boolean, Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .toggleable(
                        preferences.enabled,
                        enabled = !busy,
                        role = Role.Switch,
                        onValueChange = { onChange(it, preferences.sessionLimit) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Capture history", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "On this device while SceneDeck is running.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(preferences.enabled, onCheckedChange = null, enabled = !busy)
            }
            FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Keep newest", Modifier.align(Alignment.CenterVertically).padding(end = 12.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !busy) {
                        Text("${preferences.sessionLimit} observations")
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        listOf(10, 30, 100).forEach { limit ->
                            DropdownMenuItem(
                                text = { Text("$limit observations") },
                                onClick = {
                                    expanded = false
                                    onChange(preferences.enabled, limit)
                                },
                            )
                        }
                    }
                }
            }
            Text(
                "Up to 500 entries per observation. Saved bookmarks have their own retention.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HistorySessionCard(
    session: HistorySession,
    state: HistoryUiState,
    actions: HistoryActions,
) {
    Card(onClick = { actions.open(session.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(SceneDeckTheme.spacing.rowPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(session.workspace.collectionName, style = MaterialTheme.typography.titleMedium)
            Text(
                state.connectionNames[session.workspace.profileId]
                    ?: "Removed connection #${session.workspace.profileId}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(bookmarkDate(session.startedAtMs), style = MaterialTheme.typography.bodyMedium)
            Text(session.endText(state.capture), style = MaterialTheme.typography.bodySmall)
            Text(
                "${session.lastOrdinal - session.missingEvents - session.omittedEvents} retained entries",
                style = MaterialTheme.typography.bodySmall,
            )
            HistoryOmissions(session)
            FlowRow {
                TextButton(onClick = { actions.open(session.id) }) { Text("View observation") }
                TextButton(
                    onClick = { actions.delete(session) },
                    enabled = !state.busy,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Delete observation ${session.workspace.collectionName}"
                        },
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
internal fun HistoryOmissions(session: HistorySession) {
    if (session.omittedEvents > 0)
        Text(
            "${session.omittedEvents} older entries removed by retention.",
            style = MaterialTheme.typography.bodySmall,
        )
    if (session.missingEvents > 0)
        Text(
            "${session.missingEvents} history entries missed.",
            style = MaterialTheme.typography.bodySmall,
        )
}

@Composable
internal fun HistoryReadFailure(onRetry: () -> Unit) {
    Text("Couldn't load saved history. Its contents are unknown.")
    TextButton(onClick = onRetry) { Text("Retry loading") }
}
