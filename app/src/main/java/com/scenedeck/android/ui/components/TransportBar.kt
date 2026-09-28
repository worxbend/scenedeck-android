package com.scenedeck.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

// TODO(M0): move to :core:ui alongside StatusStrip once that module is open.
// TODO(M3): wire to ObsClient (StartStream/StartRecord) with Output Safety
// confirmation toggles, pulsing record tally and live elapsed counters.

/**
 * Stream/record transport controls: stadium buttons (M3 default button shape is
 * full/stadium) plus a monospace elapsed-time counter. Disabled until OBS wiring
 * lands in M3.
 */
@Composable
fun TransportBar(
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    recording: Boolean = false,
    elapsedTime: String = "00:00:00",
    enabled: Boolean = false,
    onToggleStream: () -> Unit = {},
    onToggleRecord: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onToggleStream, enabled = enabled) {
            Text(if (streaming) "Stop Stream" else "Start Stream")
        }
        OutlinedButton(onClick = onToggleRecord, enabled = enabled) {
            Text(if (recording) "Stop Record" else "Start Record")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = elapsedTime,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun TransportBarPreview() {
    SceneDeckTheme {
        TransportBar()
    }
}
