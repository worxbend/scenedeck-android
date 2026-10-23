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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.stateDescription
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
    virtualCamPending: VirtualCamPending? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StreamButton(streaming, enabled, pulseTally, onToggleStream, Modifier.weight(1f))
        RecordButton(recording, enabled, pulseTally, onToggleRecord, Modifier.weight(1f))
        VirtualCameraButton(
            virtualCamActive,
            virtualCamPending,
            enabled,
            onToggleVirtualCam,
            Modifier.weight(1f),
        )
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

/**
 * VCam: neutral tonal when idle, a strong tertiary "Stop VCam" fill while active, and a spinner
 * with Starting…/Stopping… while OBS brings the camera up or down (taps ignored until confirmed).
 */
@Composable
private fun VirtualCameraButton(
    active: Boolean,
    pending: VirtualCamPending?,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = virtualCamLabel(active, pending)
    val description = stringResource(virtualCamDescription(active, pending))
    val scheme = MaterialTheme.colorScheme
    val colors =
        when {
            pending != null ->
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                )
            active ->
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.tertiary,
                    contentColor = scheme.onTertiary,
                )
            else ->
                ButtonDefaults.filledTonalButtonColors(containerColor = scheme.surfaceContainerHigh)
        }
    FilledTonalButton(
        onClick = { if (pending == null) onToggle() },
        enabled = enabled,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier =
            modifier.heightIn(min = 48.dp).semantics {
                contentDescription = description
                if (pending != null) stateDescription = description
            },
    ) {
        if (pending != null) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Icon(SceneIcon.CAMERA.imageVector, null, Modifier.size(16.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun virtualCamLabel(active: Boolean, pending: VirtualCamPending?): Int =
    when (pending) {
        VirtualCamPending.STARTING -> R.string.vcam_starting
        VirtualCamPending.STOPPING -> R.string.vcam_stopping
        null -> if (active) R.string.vcam_stop_short else R.string.vcam_chip
    }

internal fun virtualCamDescription(active: Boolean, pending: VirtualCamPending?): Int =
    when (pending) {
        VirtualCamPending.STARTING -> R.string.vcam_starting_cd
        VirtualCamPending.STOPPING -> R.string.vcam_stopping_cd
        null -> if (active) R.string.stop_vcam_cd else R.string.start_vcam_cd
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
            virtualCamActive = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun TransportBarVirtualCamStartingPreview() {
    SceneDeckTheme {
        TransportBar(
            streaming = false,
            recording = false,
            enabled = true,
            pulseTally = false,
            onToggleStream = {},
            onToggleRecord = {},
            virtualCamPending = VirtualCamPending.STARTING,
        )
    }
}
