package com.scenedeck.android.feature.preflight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.PreflightSetupState
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.model.ConnectionState

@Composable
fun PreflightScreen(
    modifier: Modifier = Modifier,
    viewModel: PreflightViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    if (connection !is ConnectionState.Ready) {
        DisconnectedPlaceholder(connectionState = connection, onConnect = onNavigateToConnections)
    } else {
        val target = state.setup as? PreflightSetupState.Ready
        if (target == null)
            WorkspaceStatus(
                state.setup is PreflightSetupState.Failed ||
                    state.setup == PreflightSetupState.Unavailable(WorkspaceState.Failed),
                viewModel::retry,
                modifier.statusBarsPadding(),
            )
        else
            key(target.origin) {
                var editor by remember { mutableStateOf<PreflightSetupState.Ready?>(null) }
                PreflightContent(
                    state,
                    target,
                    { viewModel.run(target, it) },
                    viewModel::cancel,
                    { editor = target },
                    { id, complete -> viewModel.completeManual(target, id, complete) },
                    modifier,
                )
                editor?.let { original ->
                    PreflightSetupSheet(
                        original.config,
                        state.scenes,
                        state.microphones,
                        onDismiss = { editor = null },
                        onSave = { viewModel.save(original, it) },
                    )
                }
            }
    }
}

@Composable
internal fun PreflightContent(
    state: PreflightUiState,
    target: PreflightSetupState.Ready,
    onRun: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSetup: () -> Unit,
    onManual: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var signal by remember(target.origin) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        StudioPageHeader(
            "Preflight",
            "${state.connectionName} · ${target.origin.key.collectionName}",
            SceneDeckIcons.Preflight,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !state.run.running,
                        onClick = {
                            onRun(signal)
                            signal = false
                        },
                    ) {
                        Text("Run checks")
                    }
                    OutlinedButton(onClick = onSetup, enabled = !state.run.running) {
                        Text("Setup")
                    }
                    if (state.run.running) TextButton(onClick = onCancel) { Text("Cancel") }
                }
                ListItem(
                    headlineContent = { Text("Check microphone signal this time") },
                    supportingContent = {
                        Text(
                            "Speak during the four-second check. Missing meter data stays unknown."
                        )
                    },
                    leadingContent = { Checkbox(signal, null, enabled = !state.run.running) },
                    modifier =
                        Modifier.toggleable(
                            signal,
                            enabled = !state.run.running,
                            role = Role.Checkbox,
                        ) {
                            signal = it
                        },
                )
            }
            runResults(state.run)
            item { StudioSectionHeader("Manual checklist") }
            if (target.config.manualChecks.isEmpty()) item { Text("Add your own checks in Setup.") }
            items(target.config.manualChecks, key = { "manual:${it.id}" }) { check ->
                ListItem(
                    headlineContent = { Text(check.label) },
                    leadingContent = {
                        Checkbox(check.complete, null, enabled = !state.run.running)
                    },
                    modifier =
                        Modifier.toggleable(
                            check.complete,
                            enabled = !state.run.running,
                            role = Role.Checkbox,
                        ) {
                            onManual(check.id, it)
                        },
                )
            }
            item {
                Text(
                    "Manual confirmations are saved for this workspace. Clear them when preparing a new session.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.runResults(run: PreflightRunState) {
    if (run.running)
        item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Checking OBS… Speak now if you enabled the signal check.")
        }
    run.error?.let { message ->
        item { Text(message, color = MaterialTheme.colorScheme.error) }
    }
    run.report?.let { report ->
        item { PreflightReportHeader(report) }
        items(report.findings, key = { it.id }) { PreflightResultRow(it) }
    }
    if (!run.running && run.report == null && run.error == null)
        item {
            Text(
                "Run checks before going live. Results are a snapshot and never start an output.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
}
