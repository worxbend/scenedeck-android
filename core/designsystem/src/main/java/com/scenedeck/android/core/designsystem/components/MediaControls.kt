package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.MediaStateKind

// Lucide-style media glyphs (24dp viewport, 2dp rounded stroke), kept private
// here because the icon catalogue file is owned by another agent.
private fun mediaIcon(name: String, vararg elements: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        elements.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private val PlayIcon = mediaIcon("Play", "M6,3 L20,12 L6,21 Z")
private val PauseIcon = mediaIcon("Pause", "M6,4 h4 v16 h-4 Z", "M14,4 h4 v16 h-4 Z")
private val RestartIcon = mediaIcon(
    "Restart",
    "M3,12 a9,9 0 1 0 9,-9 a9.75,9.75 0 0 0 -6.74,2.74 L3,8",
    "M3,3 v5 h5",
)

/**
 * Media playback controls for mixer strips with media-kind inputs
 * (FEATURE_SPEC §8): state chip + play/pause + restart, ≥ 48dp targets.
 */
@Composable
fun MediaControls(
    state: MediaStateKind?,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MediaStateChip(state)
        IconButton(
            onClick = onPlayPause,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (state == MediaStateKind.PLAYING) PauseIcon else PlayIcon,
                contentDescription = if (state == MediaStateKind.PLAYING) "Pause media" else "Play media",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onRestart,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = RestartIcon,
                contentDescription = "Restart media",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MediaStateChip(state: MediaStateKind?) {
    val colors = SceneDeckTheme.colors
    val (label, color) = when (state) {
        MediaStateKind.PLAYING -> "Playing" to colors.ready
        MediaStateKind.PAUSED -> "Paused" to colors.warning
        MediaStateKind.ENDED, MediaStateKind.STOPPED -> "Ended" to colors.idle
        MediaStateKind.ERROR -> "Error" to colors.meterRed
        MediaStateKind.OPENING, MediaStateKind.BUFFERING -> "Loading" to colors.idle
        MediaStateKind.NONE, null -> "No media" to colors.idle
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
