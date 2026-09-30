package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private val FADER_TICKS = listOf(0f, -12f, -24f, -36f, -48f, -60f)
private val FADER_LABELS = listOf(0f, -20f, -40f)

/**
 * Vertical fader v2 with dB taper: 6dp rounded track + tick ladder, accent fill below the thumb,
 * 32×8dp pill thumb with a grip line, floating dB bubble while dragging. Drag updates preview
 * continuously and commits once on end.
 */
@Composable
internal fun FaderTrack(
    volumeMul: Double,
    enabled: Boolean,
    contentDescription: String,
    onPreview: (Double) -> Unit,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    val displayFraction =
        if (dragFraction.isNaN()) dbToFraction(mulToDb(volumeMul)) else dragFraction
    val dragging = !dragFraction.isNaN()
    val currentFraction by rememberUpdatedState(displayFraction)
    val currentVolume by rememberUpdatedState(volumeMul)
    val currentPreview by rememberUpdatedState(onPreview)
    val currentCommit by rememberUpdatedState(onCommit)

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gripColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            color = labelColor,
        )
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        Canvas(
            modifier =
                Modifier.matchParentSize()
                    .onSizeChanged { heightPx = it.height }
                    .semantics {
                        this.contentDescription = contentDescription
                        // Screen-reader adjustable: progress is exposed in dB (−60…0).
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                current = mulToDb(volumeMul),
                                range = METER_DB_FLOOR..0f,
                            )
                        if (enabled) {
                            setProgress("Set $contentDescription") { targetDb ->
                                onCommit(dbToMul(targetDb.coerceIn(METER_DB_FLOOR, 0f)).toDouble())
                                true
                            }
                        }
                    }
                    .alpha(if (enabled) 1f else 0.45f)
                    .faderDrag(
                        enabled,
                        FaderGestureCallbacks(
                            { currentFraction },
                            { currentVolume },
                            { dragFraction },
                            { dragFraction = it },
                            { currentPreview(it) },
                            { currentCommit(it) },
                        ),
                    )
        ) {
            drawFader(
                FaderPalette(trackColor, fillColor, tickColor, gripColor),
                textMeasurer,
                labelStyle,
                displayFraction,
            )
        }

        // Floating dB bubble above the thumb while dragging.
        if (dragging && heightPx > 0) {
            val bubbleHeight = with(density) { 22.dp.toPx() }
            val y =
                (heightPx * (1f - displayFraction) - bubbleHeight - with(density) { 10.dp.toPx() })
                    .coerceIn(0f, heightPx - bubbleHeight)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, y.toInt()) },
            ) {
                Text(
                    text = formatDb(fractionToDb(displayFraction)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun fractionToDb(fraction: Float): Float = METER_DB_FLOOR + fraction * -METER_DB_FLOOR

private data class FaderPalette(
    val trackColor: androidx.compose.ui.graphics.Color,
    val fillColor: androidx.compose.ui.graphics.Color,
    val tickColor: androidx.compose.ui.graphics.Color,
    val gripColor: androidx.compose.ui.graphics.Color,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFader(
    palette: FaderPalette,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: androidx.compose.ui.text.TextStyle,
    displayFraction: Float,
) {
    drawFaderTicks(palette.tickColor)
    drawFaderLabels(textMeasurer, labelStyle)
    drawFaderTrack(palette, displayFraction)
    drawFaderThumb(palette, displayFraction)
}

// Tick ladder (0/−12/…/−60) left of the track.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaderTicks(
    tickColor: androidx.compose.ui.graphics.Color
) {
    val trackWidth = 6.dp.toPx()
    val trackX = size.width * 0.62f - trackWidth / 2
    FADER_TICKS.forEach { tickDb ->
        val y = size.height * (1f - dbToFraction(tickDb))
        drawRect(
            color = tickColor,
            topLeft = Offset(trackX - 7.dp.toPx(), y - 0.5.dp.toPx()),
            size = Size(4.dp.toPx(), 1.dp.toPx()),
        )
    }
}

// Track + accent fill from the thumb down, with the 0 dB top marker.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaderTrack(
    palette: FaderPalette,
    displayFraction: Float,
) {
    val trackWidth = 6.dp.toPx()
    val trackX = size.width * 0.62f - trackWidth / 2
    val corner = CornerRadius(3.dp.toPx())
    val thumbY = size.height * (1f - displayFraction)
    drawRoundRect(
        color = palette.trackColor,
        topLeft = Offset(trackX, 0f),
        size = Size(trackWidth, size.height),
        cornerRadius = corner,
    )
    if (displayFraction > 0f) {
        drawRoundRect(
            color = palette.fillColor.copy(alpha = 0.55f),
            topLeft = Offset(trackX, thumbY),
            size = Size(trackWidth, size.height - thumbY),
            cornerRadius = corner,
        )
    }
    drawRect(
        color = palette.fillColor,
        topLeft = Offset(trackX - 2.dp.toPx(), 0f),
        size = Size(trackWidth + 4.dp.toPx(), 1.5.dp.toPx()),
    )
}

// Thumb pill with grip line.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaderThumb(
    palette: FaderPalette,
    displayFraction: Float,
) {
    val thumbY = size.height * (1f - displayFraction)
    val thumbWidth = 32.dp.toPx()
    val thumbHeight = 8.dp.toPx()
    val thumbX = size.width * 0.62f - thumbWidth / 2
    drawRoundRect(
        color = palette.fillColor,
        topLeft =
            Offset(thumbX, (thumbY - thumbHeight / 2).coerceIn(0f, size.height - thumbHeight)),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawRect(
        color = palette.gripColor.copy(alpha = 0.7f),
        topLeft =
            Offset(
                thumbX + thumbWidth / 2 - 6.dp.toPx(),
                (thumbY - 1.dp.toPx() / 2).coerceIn(0f, size.height),
            ),
        size = Size(12.dp.toPx(), 1.dp.toPx()),
    )
}

private class FaderGestureCallbacks(
    val currentFraction: () -> Float,
    val currentVolume: () -> Double,
    val dragFraction: () -> Float,
    val setDragFraction: (Float) -> Unit,
    val currentPreview: (Double) -> Unit,
    val currentCommit: (Double) -> Unit,
)

private fun Modifier.faderDrag(enabled: Boolean, callbacks: FaderGestureCallbacks): Modifier =
    this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var startFraction = callbacks.currentFraction()
        var startVolume = callbacks.currentVolume()
        detectVerticalDragGestures(
            onDragStart = {
                startFraction = callbacks.currentFraction()
                startVolume = callbacks.currentVolume()
            },
            onDragEnd = {
                val final = callbacks.dragFraction()
                if (!final.isNaN()) {
                    callbacks.currentCommit(dbToMul(fractionToDb(final)).toDouble())
                    callbacks.setDragFraction(Float.NaN)
                }
            },
            onDragCancel = {
                if (!callbacks.dragFraction().isNaN()) callbacks.currentCommit(startVolume)
                callbacks.setDragFraction(Float.NaN)
            },
        ) { change, dragAmount ->
            change.consume()
            val base =
                if (callbacks.dragFraction().isNaN()) startFraction else callbacks.dragFraction()
            val next = (base - dragAmount / size.height).coerceIn(0f, 1f)
            callbacks.setDragFraction(next)
            callbacks.currentPreview(dbToMul(fractionToDb(next)).toDouble())
        }
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaderLabels(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: androidx.compose.ui.text.TextStyle,
) {
    FADER_LABELS.forEach { labelDb ->
        val y = size.height * (1f - dbToFraction(labelDb))
        val text = if (labelDb == 0f) "0" else labelDb.toInt().toString()
        val layout = textMeasurer.measure(text, labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            style = labelStyle,
            topLeft = Offset(0f, y - layout.size.height / 2f),
        )
    }
}
