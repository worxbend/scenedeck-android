package com.scenedeck.android.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.AlertReading
import com.scenedeck.android.core.data.LiveAlert
import com.scenedeck.android.core.data.LiveAlertKind
import com.scenedeck.android.core.data.LiveAlertsState
import com.scenedeck.android.core.data.SpeechTarget
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons

@Composable
fun AlertsScreen(
    onNavigate: (AlertDestination) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlertsContent(
        state,
        onNavigate,
        viewModel::dismiss,
        viewModel::armSpeech,
        viewModel::stopSpeech,
        viewModel::setHaptics,
    )
}

@Composable
internal fun AlertsContent(
    state: LiveAlertsState,
    onNavigate: (AlertDestination) -> Unit,
    onDismiss: (LiveAlert) -> Unit,
    onArm: (SpeechTarget) -> Boolean,
    onStop: () -> Unit,
    onHaptics: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("alerts"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StudioPageHeader(
                    "Live alerts",
                    "Sustained warnings while you operate",
                    SceneDeckIcons.Alerts,
                )
            }
            if (!state.connected)
                item {
                    Text(
                        "Connect to OBS for live readings. A lost connection leaves output state unknown."
                    )
                    TextButton(onClick = { onNavigate(AlertDestination.CONNECTIONS) }) {
                        Text("Connections")
                    }
                }
            items(state.alerts, key = { "alert_${it.occurrence}" }) { alert ->
                AlertCard(
                    alert,
                    reading = state.readings.firstOrNull { it.kind == alert.kind },
                    onDismiss = { onDismiss(alert) },
                    onNavigate = { onNavigate(alert.kind.destination) },
                )
            }
            item { StudioSectionHeader("Speech monitoring") }
            item { SpeechControls(state, onArm, onStop, onNavigate) }
            item { StudioSectionHeader("Current readings") }
            items(
                state.readings.filter { it.kind != LiveAlertKind.MICROPHONE },
                key = { it.kind },
            ) { reading ->
                ListItem(
                    headlineContent = { Text(reading.kind.label) },
                    supportingContent = { Text(reading.description) },
                    modifier =
                        Modifier.clearAndSetSemantics {
                            contentDescription = "${reading.kind.label}. ${reading.description}"
                        },
                )
            }
            item {
                Text(
                    "Frame-drop and render warnings need 5 seconds of fresh samples. Recovery also " +
                        "needs 5 seconds. Render thresholds follow the OBS canvas rate (60 fps while " +
                        "unknown).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Recording space uses your Preflight minimum (2,048 MB by default). An " +
                        "unavailable setup is unknown.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onNavigate(AlertDestination.PREFLIGHT) }) {
                    Text("Open Preflight")
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Vibrate for new alerts") },
                    supportingContent = {
                        Text(
                            "Off by default. Requires Haptics in Settings. Applies to this app session in " +
                                "the foreground."
                        )
                    },
                    trailingContent = {
                        Switch(
                            state.hapticsEnabled,
                            onCheckedChange = onHaptics,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Vibrate for new alerts"
                                },
                        )
                    },
                )
            }
            item {
                Text(
                    "No audible alerts or system alert notifications. Background operation and " +
                        "delivery depend on Android; check OBS directly when the app cannot receive " +
                        "readings. Dismiss hides a warning for 60 seconds and keeps monitoring.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: LiveAlert,
    reading: AlertReading?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(alert.title, style = MaterialTheme.typography.titleMedium)
            reading?.let { Text(it.description, style = MaterialTheme.typography.bodySmall) }
            if (alert.uncertain) Text("The last warning has not been confirmed as recovered.")
            TextButton(onClick = onNavigate) { Text(alert.kind.destination.actionLabel) }
            TextButton(onClick = onDismiss) { Text("Dismiss for 60 seconds") }
        }
    }
}
