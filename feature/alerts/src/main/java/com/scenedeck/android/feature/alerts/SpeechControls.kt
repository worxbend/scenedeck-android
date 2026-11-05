package com.scenedeck.android.feature.alerts

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.scenedeck.android.core.data.LiveAlertKind
import com.scenedeck.android.core.data.LiveAlertsState
import com.scenedeck.android.core.data.SpeechTarget

@Composable
internal fun SpeechControls(
    state: LiveAlertsState,
    onArm: (SpeechTarget) -> Boolean,
    onStop: () -> Unit,
    onNavigate: (AlertDestination) -> Unit,
) {
    key(state.speechTarget) {
        var consent by remember { mutableStateOf<SpeechTarget?>(null) }
        var rejected by remember { mutableStateOf(false) }
        Column {
            val armed = state.armedSpeech
            if (armed != null) {
                Text(
                    "Expecting speech from ${armed.inputName}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    state.readings.firstOrNull { it.kind == LiveAlertKind.MICROPHONE }?.description
                        ?: "Waiting for meter samples"
                )
                OutlinedButton(onClick = onStop) { Text("Stop monitoring") }
                TextButton(onClick = { onNavigate(AlertDestination.MIXER) }) { Text("Open Mixer") }
            } else {
                Text(
                    state.speechTarget?.let { "Selected microphone: ${it.inputName}" }
                        ?: "Choose a microphone in Preflight Setup."
                )
                Text(
                    "Arm only while speech is expected. Quiet speech, a muted channel or silence " +
                        "can trigger a warning; missing meters remain unknown.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        consent = state.speechTarget
                        rejected = false
                    },
                    enabled = state.speechTarget != null,
                ) {
                    Text("Monitor microphone")
                }
                TextButton(onClick = { onNavigate(AlertDestination.PREFLIGHT) }) {
                    Text("Choose microphone")
                }
            }
            if (rejected)
                Text(
                    "The connection or microphone changed. Review the selection and try again.",
                    color = MaterialTheme.colorScheme.error,
                )
        }
        consent?.let { target ->
            AlertDialog(
                onDismissRequest = { consent = null },
                title = { Text("Expecting speech?") },
                text = {
                    Text(
                        "Monitor ${target.inputName} for quiet periods of 10 seconds at or below -50 dB. This " +
                            "does not record audio. Monitoring stops when the connection or workspace " +
                            "changes; arm it again when ready."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            rejected = !onArm(target)
                            consent = null
                        }
                    ) {
                        Text("Speech expected — arm")
                    }
                },
                dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } },
            )
        }
    }
}
