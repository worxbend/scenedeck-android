package com.scenedeck.android.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

private const val TRIGGER_THRESHOLD = 0.8f

/**
 * T-bar transition lever (FEATURE_SPEC §8, M6 deferral): pull down to arm, release past ~80 % to
 * fire the studio transition; otherwise the thumb springs home. [motionLevel] OFF disables the
 * spring-back animation (instant snap).
 */
@Composable
fun TBar(
    motionLevel: MotionLevel,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrigger by rememberUpdatedState(onTrigger)
    val colors = SceneDeckTheme.colors
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val displayed by
        animateFloatAsState(
            targetValue = if (dragging) dragFraction else 0f,
            animationSpec =
                if (motionLevel == MotionLevel.OFF) snap()
                else spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "tbarThumb",
        )
    val armed = displayed >= TRIGGER_THRESHOLD
    val trackBase = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumbBase = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            modifier
                .width(36.dp)
                .height(56.dp)
                .semantics {
                    contentDescription =
                        "T-bar: drag down to arm transition, release to fire (${(TRIGGER_THRESHOLD * 100).toInt()}%)"
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragFraction = 0f
                        },
                        onDragEnd = {
                            if (dragFraction >= TRIGGER_THRESHOLD) currentTrigger()
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        dragFraction = (dragFraction + dragAmount / size.height).coerceIn(0f, 1f)
                    }
                }
    ) {
        drawTBar(armed, displayed, TBarColors(trackBase, thumbBase, colors.preview))
    }
}

private data class TBarColors(
    val trackBase: androidx.compose.ui.graphics.Color,
    val thumbBase: androidx.compose.ui.graphics.Color,
    val previewColor: androidx.compose.ui.graphics.Color,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTBar(
    armed: Boolean,
    displayed: Float,
    colors: TBarColors,
) {
    val corner = CornerRadius(8.dp.toPx())
    val trackColor = if (armed) colors.previewColor.copy(alpha = 0.5f) else colors.trackBase
    drawRoundRect(color = trackColor, size = size, cornerRadius = corner)

    // Armed marker line at the trigger threshold.
    val thresholdY = size.height * TRIGGER_THRESHOLD
    drawRect(
        color = colors.previewColor,
        topLeft = Offset(0f, thresholdY - 1.dp.toPx()),
        size = Size(size.width, 2.dp.toPx()),
    )

    // Thumb.
    val thumbHeight = 14.dp.toPx()
    val thumbY = size.height * displayed - thumbHeight / 2
    drawRoundRect(
        color = if (armed) colors.previewColor else colors.thumbBase,
        topLeft =
            Offset(
                3.dp.toPx(),
                thumbY.coerceIn(0f, size.height - thumbHeight),
            ),
        size = Size(size.width - 6.dp.toPx(), thumbHeight),
        cornerRadius = corner,
    )
}
