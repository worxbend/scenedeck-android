package com.scenedeck.android.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

@Composable
fun broadcastStreamButtonColors(streaming: Boolean) =
    if (streaming) {
        ButtonDefaults.buttonColors(
            containerColor = SceneDeckTheme.colors.program,
            contentColor = SceneDeckTheme.colors.onProgram,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

@Composable
fun Modifier.broadcastBreathingHalo(pulse: Boolean): Modifier {
    if (!pulse) return this
    val strength =
        rememberInfiniteTransition(label = "streamBreathing")
            .animateFloat(
                initialValue = 0.15f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                label = "streamBreathingStrength",
            )
    val color = SceneDeckTheme.colors.program
    return this.drawBehind {
        val outset = 2.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = strength.value),
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + outset * 2, size.height + outset * 2),
            cornerRadius = CornerRadius(size.height / 2 + outset),
            style = Stroke(2.dp.toPx()),
        )
    }
}

@Composable
fun BroadcastTimer(
    streaming: Boolean,
    recording: Boolean,
    elapsedTime: String,
    modifier: Modifier = Modifier,
) {
    val tint =
        when {
            recording -> broadcastWarningTextColor()
            streaming -> SceneDeckTheme.colors.preview
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.1f),
        contentColor = tint,
    ) {
        Text(
            text = elapsedTime,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/** Amber text retains readable contrast on light timer containers. */
@Composable
private fun broadcastWarningTextColor() =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        lerp(SceneDeckTheme.colors.warning, MaterialTheme.colorScheme.onSurface, 0.55f)
    } else {
        SceneDeckTheme.colors.warning
    }

/** Output status stays separate from the OBS socket connection indicator. */
@Composable
fun BroadcastStatus(
    streaming: Boolean,
    recording: Boolean,
    streamElapsed: String,
    recordElapsed: String,
    pulse: Boolean,
    modifier: Modifier = Modifier,
    streamReconnecting: Boolean = false,
    recordPaused: Boolean = false,
) {
    val streamLabel =
        if (streamReconnecting) {
            com.scenedeck.android.core.designsystem.R.string.broadcast_reconnecting
        } else {
            com.scenedeck.android.core.designsystem.R.string.broadcast_live
        }
    val recordLabel =
        if (recordPaused) {
            com.scenedeck.android.core.designsystem.R.string.broadcast_recording_paused
        } else {
            com.scenedeck.android.core.designsystem.R.string.broadcast_recording
        }
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.End,
    ) {
        if (streaming)
            BroadcastStatusRow(
                label = androidx.compose.ui.res.stringResource(streamLabel),
                elapsed = streamElapsed,
                recording = false,
                pulse = pulse,
            )
        if (recording)
            BroadcastStatusRow(
                label = androidx.compose.ui.res.stringResource(recordLabel),
                elapsed = recordElapsed,
                recording = true,
                pulse = pulse,
            )
        if (!streaming && !recording) {
            Text(
                text =
                    androidx.compose.ui.res.stringResource(
                        com.scenedeck.android.core.designsystem.R.string.broadcast_standby
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BroadcastStatusRow(label: String, elapsed: String, recording: Boolean, pulse: Boolean) {
    val description =
        androidx.compose.ui.res.stringResource(
            com.scenedeck.android.core.designsystem.R.string.broadcast_status_description,
            label,
            elapsed,
        )
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        BroadcastStatusDot(pulse)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = SceneDeckTheme.colors.program,
            maxLines = 1,
        )
        BroadcastTimer(streaming = !recording, recording = recording, elapsedTime = elapsed)
    }
}

@Composable
private fun BroadcastStatusDot(pulse: Boolean) {
    val strength =
        if (pulse) {
            rememberInfiniteTransition(label = "outputStatus")
                .animateFloat(
                    0.35f,
                    1f,
                    infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                    label = "outputStatusStrength",
                )
        } else null
    val tint = SceneDeckTheme.colors.program
    androidx.compose.foundation.Canvas(Modifier.size(6.dp)) {
        drawCircle(tint.copy(alpha = strength?.value ?: 1f))
    }
}
