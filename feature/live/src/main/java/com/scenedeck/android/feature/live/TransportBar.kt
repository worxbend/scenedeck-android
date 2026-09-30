package com.scenedeck.android.feature.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.components.broadcastBreathingHalo
import com.scenedeck.android.core.designsystem.components.broadcastStreamButtonColors
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Stream/record transport (docs/DESIGN_SYSTEM.md §7): semantic broadcast colors, a streaming
 * breathing halo, record tally, and direct virtual-camera control. Continuous animation is disabled
 * when [pulseTally] is false.
 */
@Composable
fun TransportBar(
    streaming: Boolean,
    recording: Boolean,
    enabled: Boolean,
    pulseTally: Boolean,
    onToggleStream: () -> Unit,
    onToggleRecord: () -> Unit,
    modifier: Modifier = Modifier,
    virtualCamActive: Boolean = false,
    onToggleVirtualCam: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StreamButton(streaming, enabled, pulseTally, onToggleStream, Modifier.weight(1f))
        RecordButton(recording, enabled, pulseTally, onToggleRecord, Modifier.weight(1f))
        VirtualCameraButton(virtualCamActive, enabled, onToggleVirtualCam, Modifier.weight(1f))
    }
}

@Composable
private fun StreamButton(
    streaming: Boolean,
    enabled: Boolean,
    pulseTally: Boolean,
    onToggleStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val streamDescription =
        stringResource(if (streaming) R.string.stop_stream else R.string.start_stream)
    Button(
        onClick = onToggleStream,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier =
            modifier
                .broadcastBreathingHalo(streaming && enabled && pulseTally)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = streamDescription },
        colors = broadcastStreamButtonColors(streaming),
    ) {
        Icon(SceneIcon.STREAM.imageVector, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (streaming) R.string.stream_stop_short else R.string.stream_short),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    pulseTally: Boolean,
    onToggleRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordDescription =
        stringResource(if (recording) R.string.stop_record else R.string.start_record)
    OutlinedButton(
        onClick = onToggleRecord,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier =
            modifier.heightIn(min = 48.dp).semantics { contentDescription = recordDescription },
        colors =
            if (recording) {
                ButtonDefaults.outlinedButtonColors(contentColor = SceneDeckTheme.colors.recording)
            } else ButtonDefaults.outlinedButtonColors(),
    ) {
        if (recording) TallyDot(pulseTally)
        else Icon(SceneIcon.RECORD.imageVector, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (recording) R.string.record_stop_short else R.string.record_short),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VirtualCameraButton(
    active: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (active) R.string.stop_vcam_cd else R.string.start_vcam_cd)
    val colors =
        if (active) ButtonDefaults.filledTonalButtonColors()
        else
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
    androidx.compose.material3.FilledTonalButton(
        onClick = onToggle,
        enabled = enabled,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
    ) {
        Icon(SceneIcon.CAMERA.imageVector, null, Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.vcam_chip), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TallyDot(pulse: Boolean) {
    val alpha =
        if (pulse) {
            rememberInfiniteTransition(label = "tallyPulse")
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "tallyPulseAlpha",
                )
                .value
        } else {
            1f
        }
    Box(
        modifier =
            Modifier.alpha(alpha)
                .size(10.dp)
                .background(SceneDeckTheme.colors.recording, CircleShape)
    )
}

@PreviewLightDark
@Composable
private fun TransportBarPreview() {
    SceneDeckTheme {
        TransportBar(
            streaming = true,
            recording = false,
            enabled = true,
            pulseTally = true,
            onToggleStream = {},
            onToggleRecord = {},
        )
    }
}
