@file:Suppress("MatchingDeclarationName") // TrendSamplesHolder + TrendChart share one file

package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.MotionLevel

/**
 * Holder for one chart's rolling sample window. Read in the Canvas DRAW PHASE only —
 * pushing a new 1 Hz sample must never trigger recomposition (MeterLevelsHolder
 * pattern; docs/DESIGN_SYSTEM.md §7).
 */
class TrendSamplesHolder {
    val samples: MutableState<List<Float>> = mutableStateOf(emptyList())
}

/**
 * Auto-scaled Y range for [samples]: anchored at [floor] (charts like FPS/render time
 * never go negative), [headroom] fraction above the peak, at least [minSpan] tall.
 * Empty input yields the default (floor, floor + minSpan) window.
 */
internal fun trendYScale(
    samples: List<Float>,
    floor: Float = 0f,
    minSpan: Float = 1f,
    headroom: Float = 0.15f,
): Pair<Float, Float> {
    val peak = samples.maxOrNull() ?: floor
    val rawMax = peak + (peak - floor).coerceAtLeast(0f) * headroom
    val max = maxOf(rawMax, floor + minSpan)
    return floor to max
}

private const val GRID_LINES = 4
// Redraws arrive at the 1 Hz telemetry cadence, so the scale ease converges in
// ~2 redraws rather than over a 60 fps animation curve.
private const val SCALE_LERP_PER_FRAME = 0.5f

/** Carries the displayed Y scale across draw calls without Compose snapshots. */
private class TrendScaleRenderState {
    var minY = 0f
    var maxY = 1f
    var initialized = false

    fun update(targetMin: Float, targetMax: Float, animate: Boolean) {
        if (!initialized || !animate) {
            minY = targetMin
            maxY = targetMax
            initialized = true
        } else {
            minY += (targetMin - minY) * SCALE_LERP_PER_FRAME
            maxY += (targetMax - maxY) * SCALE_LERP_PER_FRAME
        }
    }
}

/**
 * 2-minute rolling line/area chart (docs/DESIGN_SYSTEM.md §7): theme-aware grid,
 * auto-scaled Y anchored at [floor], area fill, last-value dot. Samples are
 * right-aligned in a [windowSize]-sample window, so a sparse series hugs the right
 * edge and an empty series renders the grid only.
 *
 * Reads [samplesHolder] in the DRAW PHASE ONLY — 1 Hz telemetry updates redraw
 * without recomposition. [motionLevel] OFF snaps Y-scale changes (no animation).
 */
@Composable
fun TrendChart(
    samplesHolder: TrendSamplesHolder,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
    windowSize: Int = 120,
    floor: Float = 0f,
    lineColor: Color = Color.Unspecified,
) {
    val color = if (lineColor == Color.Unspecified) MaterialTheme.colorScheme.primary else lineColor
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scaleState = remember { TrendScaleRenderState() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val samples = samplesHolder.samples.value
        val (targetMin, targetMax) = trendYScale(samples, floor)
        scaleState.update(targetMin, targetMax, animate = motionLevel != MotionLevel.OFF)
        val minY = scaleState.minY
        val maxY = scaleState.maxY
        val span = (maxY - minY).takeIf { it > 0f } ?: 1f

        fun yFor(value: Float): Float =
            size.height * (1f - ((value - minY) / span).coerceIn(0f, 1f))

        // Theme-aware horizontal grid (GRID_LINES interior lines + baseline).
        for (i in 0..GRID_LINES) {
            val y = size.height * i / GRID_LINES
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (i == GRID_LINES) 1.5f.dp.toPx() else 1f.dp.toPx(),
            )
        }
        if (samples.isEmpty()) return@Canvas

        // Right-align the n samples inside the rolling window.
        val window = windowSize.coerceAtLeast(2)
        val offset = (window - samples.size).coerceAtLeast(0)
        fun xFor(index: Int): Float =
            size.width * (offset + index).toFloat() / (window - 1)

        val linePath = Path()
        val areaPath = Path()
        samples.forEachIndexed { index, sample ->
            val x = xFor(index)
            val y = yFor(sample)
            if (index == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, size.height)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(xFor(samples.size - 1), size.height)
        areaPath.close()

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.02f)),
            ),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        // Last-value dot.
        val lastX = xFor(samples.size - 1)
        val lastY = yFor(samples.last())
        drawCircle(color = color, radius = 3.5f.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = gridColor, radius = 1.5f.dp.toPx(), center = Offset(lastX, lastY))
    }
}
