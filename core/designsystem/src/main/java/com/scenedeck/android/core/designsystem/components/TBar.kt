package com.scenedeck.android.core.designsystem.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/** Release at or beyond this share of the travel fires the transition. */
internal const val TBAR_TRIGGER_THRESHOLD = 0.8f

/** A drag must start on the thumb or within this share of the travel past it. */
internal const val TBAR_GRAB_ZONE = 0.25f

private val TBarHeight = 56.dp
private val ThumbWidth = 64.dp
private val ThumbInset = 4.dp

/**
 * Fraction of the T-bar travel for an absolute pointer [x] on a track of [trackWidth] with a thumb
 * of [thumbWidth] (all in px). The thumb centre follows the finger, so the full track length maps
 * to 0…1 without losing distance to touch slop.
 */
internal fun tBarFraction(x: Float, trackWidth: Float, thumbWidth: Float): Float {
    val travel = trackWidth - thumbWidth
    if (travel <= 0f) return 0f
    return ((x - thumbWidth / 2f) / travel).coerceIn(0f, 1f)
}

/** Only presses that start on the resting thumb (or just past it) arm a pull. */
internal fun tBarGrabAllowed(downX: Float, trackWidth: Float, thumbWidth: Float): Boolean =
    downX <= thumbWidth || tBarFraction(downX, trackWidth, thumbWidth) <= TBAR_GRAB_ZONE

/**
 * Full-width T-bar transition lever (FEATURE_SPEC §8): slide the thumb right across the track and
 * release past 80 % of the travel to fire the studio transition; otherwise it springs home. The
 * thumb tracks the absolute pointer position. [motionLevel] OFF snaps back instantly, REDUCED
 * settles without bounce. Screen readers get a custom "fire transition" action.
 */
@Composable
fun TBar(
    motionLevel: MotionLevel,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrigger by rememberUpdatedState(onTrigger)
    val sceneDeckColors = SceneDeckTheme.colors
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val displayed by
        animateFloatAsState(
            targetValue = if (dragging) dragFraction else 0f,
            animationSpec = tBarSpring(dragging, motionLevel),
            label = "tbarThumb",
        )
    val armed = dragging && dragFraction >= TBAR_TRIGGER_THRESHOLD
    val colors =
        TBarColors(
            trackBase = MaterialTheme.colorScheme.surfaceContainerHighest,
            thumbBase = MaterialTheme.colorScheme.primary,
            previewColor = sceneDeckColors.preview,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TBarHeight)
                .clearAndSetSemantics {
                    contentDescription =
                        "T-bar: slide right past ${(TBAR_TRIGGER_THRESHOLD * 100).toInt()}% " +
                            "and release to transition"
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Fire transition") {
                                currentTrigger()
                                true
                            }
                        )
                }
                .pointerInput(Unit) {
                    val thumbPx = ThumbWidth.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        if (!tBarGrabAllowed(down.position.x, width, thumbPx)) {
                            return@awaitEachGesture
                        }
                        down.consume()
                        dragging = true
                        dragFraction = tBarFraction(down.position.x, width, thumbPx)
                        try {
                            val released =
                                horizontalDrag(down.id) { change ->
                                    change.consume()
                                    dragFraction = tBarFraction(change.position.x, width, thumbPx)
                                }
                            if (released && dragFraction >= TBAR_TRIGGER_THRESHOLD) {
                                currentTrigger()
                            }
                        } finally {
                            dragging = false
                        }
                    }
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.matchParentSize()) { drawTBar(armed, displayed, colors) }
        Text(
            text = if (armed) "Release to transition" else "Slide to transition",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(start = ThumbWidth + 12.dp)
                    .alpha(1f - displayed.coerceIn(0f, 0.6f)),
        )
    }
}

private fun tBarSpring(dragging: Boolean, motionLevel: MotionLevel): AnimationSpec<Float> =
    when {
        // While dragging the thumb sits under the finger; only the return is animated.
        dragging || motionLevel == MotionLevel.OFF -> snap()
        motionLevel == MotionLevel.REDUCED ->
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        else -> spring(dampingRatio = 0.6f, stiffness = 400f)
    }

private data class TBarColors(val trackBase: Color, val thumbBase: Color, val previewColor: Color)

private fun DrawScope.drawTBar(armed: Boolean, displayed: Float, colors: TBarColors) {
    val corner = CornerRadius(size.height / 2f)
    drawRoundRect(color = colors.trackBase, size = size, cornerRadius = corner)

    val thumbWidth = ThumbWidth.toPx()
    val travel = (size.width - thumbWidth).coerceAtLeast(0f)
    val thumbLeft = travel * displayed

    // Progress fill behind the thumb while it is pulled.
    if (displayed > 0f) {
        drawRoundRect(
            color = colors.previewColor.copy(alpha = if (armed) 0.45f else 0.22f),
            size = Size(thumbLeft + thumbWidth, size.height),
            cornerRadius = corner,
        )
    }

    // Armed marker at the trigger threshold (thumb centre position): ticks on both edges keep the
    // centre free for the label.
    val thresholdX = thumbWidth / 2f + travel * TBAR_TRIGGER_THRESHOLD
    val tick = Size(2.dp.toPx(), 10.dp.toPx())
    val tickInset = 6.dp.toPx()
    for (top in listOf(tickInset, size.height - tickInset - tick.height)) {
        drawRect(
            color = colors.previewColor,
            topLeft = Offset(thresholdX - tick.width / 2f, top),
            size = tick,
        )
    }

    // Thumb with grip lines.
    val inset = ThumbInset.toPx()
    val thumbColor = if (armed) colors.previewColor else colors.thumbBase
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(thumbLeft + inset, inset),
        size = Size(thumbWidth - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius((size.height - inset * 2) / 2f),
    )
    val gripHeight = size.height * 0.36f
    val gripTop = (size.height - gripHeight) / 2f
    val centre = thumbLeft + thumbWidth / 2f
    for (offset in listOf(-5.dp.toPx(), 0f, 5.dp.toPx())) {
        drawRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(centre + offset - 1.dp.toPx(), gripTop),
            size = Size(2.dp.toPx(), gripHeight),
        )
    }
}

@PreviewLightDark
@Composable
private fun TBarPreview() {
    SceneDeckTheme { TBar(motionLevel = MotionLevel.FULL, onTrigger = {}) }
}
