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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.R
import com.scenedeck.android.core.designsystem.components.BroadcastStatus
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus

/**
 * Persistent bottom strip (sits above the navigation bar): tappable connection indicator (real
 * [ConnectionState]) and output status, with FPS, dropped frames, CPU and bitrate only while
 * streaming. Long-pressing the connection indicator opens the app-level Background sheet.
 *
 * [compact] (compact-height windows such as phone landscape) keeps everything on one 48dp row.
 * [windowInsets] pads the content inside the surface when the strip is the bottom-most chrome.
 */
@Composable
fun StatusStrip(
    state: StatusStripState,
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    onConnectionLongClick: () -> Unit = {},
    animateConnection: Boolean = true,
    compact: Boolean = false,
    windowInsets: WindowInsets = WindowInsets(0),
) {
    val showMetrics = state.connection is ConnectionState.Ready && state.stream?.active == true
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.windowInsetsPadding(windowInsets).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ConnectionIndicator(
                    state = state.connection,
                    animate = animateConnection,
                    onClick = onConnectionClick,
                    onLongClick = onConnectionLongClick,
                )
                if (compact && showMetrics) {
                    StatusMetrics(state, Modifier.weight(1f).padding(horizontal = 16.dp))
                }
                OutputIndicator(state, animateConnection, compact)
            }
            if (!compact && showMetrics) StatusMetrics(state)
        }
    }
}

@Composable
private fun OutputIndicator(state: StatusStripState, pulse: Boolean, compact: Boolean) {
    if (state.connection !is ConnectionState.Ready) {
        Text(
            stringResource(R.string.output_status_unknown),
            style = MaterialTheme.typography.labelSmall,
        )
    } else if (state.stream?.active == true || state.record?.active == true) {
        if (compact) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActiveOutputs(state, pulse)
            }
        } else {
            Column(horizontalAlignment = Alignment.End) { ActiveOutputs(state, pulse) }
        }
    } else {
        val label =
            if (state.stream != null && state.record != null) {
                R.string.output_status_off_air
            } else {
                R.string.output_status_checking
            }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActiveOutputs(state: StatusStripState, pulse: Boolean) {
    if (state.stream?.active == false) {
        Text(
            stringResource(R.string.output_status_off_air),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    BroadcastStatus(
        streaming = state.stream?.active == true,
        recording = state.record?.active == true,
        streamElapsed = state.stream?.timecode?.take(8) ?: "00:00:00",
        recordElapsed = state.record?.timecode?.take(8) ?: "00:00:00",
        streamReconnecting = state.stream?.reconnecting == true,
        recordPaused = state.record?.paused == true,
        pulse = pulse,
    )
}

@Composable
private fun StatusMetrics(state: StatusStripState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoStat(value = "%.1f".format(state.fps), label = "FPS")
            MonoStat(value = state.droppedFrames.toString(), label = "DROP")
            if (availableWidth >= 320.dp)
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
    val (dotColor, label, pulse) =
        when (state) {
            is ConnectionState.Ready -> Triple(colors.preview, "OBS", false)
            is ConnectionState.Connecting -> Triple(colors.warning, "Connecting", true)
            is ConnectionState.Identifying -> Triple(colors.warning, "Connecting", true)
            is ConnectionState.Reconnecting ->
                Triple(colors.warning, "Retry ${state.attempt}", true)
            is ConnectionState.Failed -> Triple(colors.recording, "Failed", false)
            ConnectionState.Disconnected -> Triple(colors.idle, "Offline", false)
        }

    val alpha =
        if (pulse && animate) {
            rememberInfiniteTransition(label = "connectionPulse")
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "connectionPulseAlpha",
                )
                .value
        } else {
            1f
        }

    Row(
        modifier =
            Modifier.combinedClickable(
                    role = Role.Button,
                    onClickLabel = "Open connections",
                    onClick = onClick,
                    onLongClickLabel = "Background settings",
                    onLongClick = onLongClick,
                )
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.alpha(alpha).size(10.dp).background(dotColor, CircleShape))
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

private fun formatBitrate(kbps: Int): String =
    if (kbps >= 1000) {
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

@Preview(widthDp = 800)
@Composable
private fun StatusStripCompactPreview() {
    SceneDeckTheme {
        StatusStrip(
            state =
                mockStatusStripState.copy(
                    connection = ConnectionState.Ready(ObsVersionInfo("32.0", "5.6", 1, "preview")),
                    stream = StreamStatus(true, false, "00:12:34.000", 754000, 0, 0.0, 0, 0),
                    record = RecordStatus(true, false, "00:04:18.000", 258000, 0),
                ),
            compact = true,
            animateConnection = false,
        )
    }
}
