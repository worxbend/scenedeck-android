package com.scenedeck.android.feature.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Stream/record transport (docs/DESIGN_SYSTEM.md §7): stadium buttons, pulsing
 * record tally (1 Hz, disabled when [pulseTally] is false) and a monospace
 * elapsed-time counter fed by OBS timecodes.
 */
@Composable
fun TransportBar(
    streaming: Boolean,
    recording: Boolean,
    elapsedTime: String,
    enabled: Boolean,
    pulseTally: Boolean,
    onToggleStream: () -> Unit,
    onToggleRecord: () -> Unit,
    modifier: Modifier = Modifier,
    virtualCamActive: Boolean = false,
    replayBufferActive: Boolean = false,
    onToggleVirtualCam: () -> Unit = {},
    onToggleReplayBuffer: () -> Unit = {},
    onSaveReplay: () -> Unit = {},
) {
    // Virtual cam / replay are non-destructive toggles: Output Safety does not
    // apply (nothing streams or writes until an explicit Save Replay tap).
    // FlowRow: the vcam/replay chips wrap under the transport buttons on
    // narrow widths instead of clipping off-screen.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onToggleStream, enabled = enabled) {
            Text(stringResource(if (streaming) R.string.stop_stream else R.string.start_stream))
        }
        OutlinedButton(
            onClick = onToggleRecord,
            enabled = enabled,
            colors = if (recording) {
                ButtonDefaults.outlinedButtonColors(
                    contentColor = SceneDeckTheme.colors.recording,
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        ) {
            if (recording) {
                TallyDot(pulse = pulseTally)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (recording) R.string.stop_record else R.string.start_record))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = elapsedTime,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = virtualCamActive,
            onClick = onToggleVirtualCam,
            enabled = enabled,
            label = { Text(stringResource(R.string.vcam_chip)) },
            leadingIcon = {
                Icon(
                    imageVector = SceneIcon.VIDEO.imageVector,
                    contentDescription = stringResource(
                        if (virtualCamActive) R.string.stop_vcam_cd else R.string.start_vcam_cd,
                    ),
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        FilterChip(
            selected = replayBufferActive,
            onClick = onToggleReplayBuffer,
            enabled = enabled,
            label = { Text(stringResource(R.string.replay_chip)) },
        )
        if (replayBufferActive) {
            IconButton(
                onClick = onSaveReplay,
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = SceneIcon.STAR.imageVector,
                    contentDescription = stringResource(R.string.save_replay_cd),
                    tint = SceneDeckTheme.colors.warning,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TallyDot(pulse: Boolean) {
    val alpha = if (pulse) {
        rememberInfiniteTransition(label = "tallyPulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "tallyPulseAlpha",
        ).value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .alpha(alpha)
            .size(10.dp)
            .background(SceneDeckTheme.colors.recording, CircleShape),
    )
}

@PreviewLightDark
@Composable
private fun TransportBarPreview() {
    SceneDeckTheme {
        TransportBar(
            streaming = true,
            recording = false,
            elapsedTime = "00:12:34",
            enabled = true,
            pulseTally = true,
            onToggleStream = {},
            onToggleRecord = {},
        )
    }
}
