package com.scenedeck.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

// TODO(M0): move to :core:ui (or :core:designsystem as a signature component) once
// that module is open for changes; DESIGN_SYSTEM.md §7 defines this as the
// persistent StatusStrip.

/** Connection health shown at the leading edge of the strip. */
enum class ConnectionIndicator {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

/** Telemetry snapshot rendered by [StatusStrip]; numerals are always monospace. */
data class StatusStripState(
    val connection: ConnectionIndicator = ConnectionIndicator.DISCONNECTED,
    val fps: Double = 0.0,
    val droppedFrames: Int = 0,
    val cpuPercent: Double = 0.0,
    val bitrateKbps: Int = 0,
)

// TODO(M3): replace with the live StatsRepository / ObsStateRepository feed.
val mockStatusStripState = StatusStripState(
    connection = ConnectionIndicator.DISCONNECTED,
    fps = 60.0,
    droppedFrames = 3,
    cpuPercent = 11.8,
    bitrateKbps = 6000,
)

/**
 * Persistent bottom strip (sits above the navigation bar): connection dot, FPS,
 * dropped frames, CPU and bitrate at a glance.
 */
@Composable
fun StatusStrip(state: StatusStripState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionDot(state.connection)
            MonoStat(value = "%.1f".format(state.fps), label = "FPS")
            MonoStat(value = state.droppedFrames.toString(), label = "DROP")
            MonoStat(value = "%.1f%%".format(state.cpuPercent), label = "CPU")
            MonoStat(value = formatBitrate(state.bitrateKbps), label = "")
        }
    }
}

@Composable
private fun ConnectionDot(indicator: ConnectionIndicator) {
    val dotColor = when (indicator) {
        ConnectionIndicator.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionIndicator.CONNECTING, ConnectionIndicator.RECONNECTING ->
            MaterialTheme.colorScheme.tertiary

        ConnectionIndicator.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
    val label = when (indicator) {
        ConnectionIndicator.CONNECTED -> "Connected"
        ConnectionIndicator.CONNECTING -> "Connecting"
        ConnectionIndicator.RECONNECTING -> "Reconnecting"
        ConnectionIndicator.DISCONNECTED -> "Disconnected"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MonoStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
        )
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBitrate(kbps: Int): String = if (kbps >= 1000) {
    "%.1f Mb/s".format(kbps / 1000.0)
} else {
    "$kbps kb/s"
}

@PreviewLightDark
@Composable
private fun StatusStripPreview() {
    SceneDeckTheme {
        StatusStrip(state = mockStatusStripState)
    }
}
