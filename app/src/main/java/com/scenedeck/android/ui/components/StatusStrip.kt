package com.scenedeck.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState

/**
 * Persistent bottom strip (sits above the navigation bar): tappable connection
 * indicator (real [ConnectionState]), FPS, dropped frames, CPU and bitrate.
 * Long-pressing the connection indicator opens the app-level Background sheet.
 */
@Composable
fun StatusStrip(
    state: StatusStripState,
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    onConnectionLongClick: () -> Unit = {},
    animateConnection: Boolean = true,
) {
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
            ConnectionIndicator(
                state = state.connection,
                animate = animateConnection,
                onClick = onConnectionClick,
                onLongClick = onConnectionLongClick,
            )
            MonoStat(value = "%.1f".format(state.fps), label = "FPS")
            MonoStat(value = state.droppedFrames.toString(), label = "DROP")
            MonoStat(value = "%.1f%%".format(state.cpuPercent), label = "CPU")
            MonoStat(value = formatBitrate(state.bitrateKbps), label = "")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionIndicator(
    state: ConnectionState,
    animate: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = SceneDeckTheme.colors
    val (dotColor, label, pulse) = when (state) {
        is ConnectionState.Ready -> Triple(colors.program, "Live", false)
        is ConnectionState.Connecting -> Triple(colors.warning, "Connecting", true)
        is ConnectionState.Identifying -> Triple(colors.warning, "Connecting", true)
        is ConnectionState.Reconnecting -> Triple(colors.warning, "Retry ${state.attempt}", true)
        is ConnectionState.Failed -> Triple(colors.recording, "Failed", false)
        ConnectionState.Disconnected -> Triple(colors.idle, "Offline", false)
    }

    val alpha = if (pulse && animate) {
        rememberInfiniteTransition(label = "connectionPulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "connectionPulseAlpha",
        ).value
    } else {
        1f
    }

    Row(
        modifier = Modifier
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open connections",
                onClick = onClick,
                onLongClickLabel = "Background settings",
                onLongClick = onLongClick,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .alpha(alpha)
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
