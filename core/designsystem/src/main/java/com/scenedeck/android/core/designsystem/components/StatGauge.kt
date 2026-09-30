package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.mono

/** Health zone of a gauge reading (docs/DESIGN_SYSTEM.md §3 — amber warn, red crit). */
enum class GaugeZone {
    NORMAL,
    WARNING,
    CRITICAL,
}

/**
 * Whether rising or falling values are worse. RISING: dropped %, congestion, render time (warn/crit
 * arcs sit at the TOP of the range). FALLING: FPS (arcs at the BOTTOM).
 */
enum class GaugeDirection {
    RISING,
    FALLING,
}

/**
 * Classify [value] against the warn/crit thresholds (docs/FEATURE_SPEC.md §5). Boundary values
 * count as the worse zone: exactly-warn is WARNING, exactly-crit is CRITICAL. For
 * [GaugeDirection.FALLING] the comparison inverts (low FPS is bad).
 */
fun classifyGaugeZone(
    value: Float,
    warnThreshold: Float,
    critThreshold: Float,
    direction: GaugeDirection = GaugeDirection.RISING,
): GaugeZone =
    when (direction) {
        GaugeDirection.RISING ->
            when {
                value >= critThreshold -> GaugeZone.CRITICAL
                value >= warnThreshold -> GaugeZone.WARNING
                else -> GaugeZone.NORMAL
            }

        GaugeDirection.FALLING ->
            when {
                value <= critThreshold -> GaugeZone.CRITICAL
                value <= warnThreshold -> GaugeZone.WARNING
                else -> GaugeZone.NORMAL
            }
    }

/** Normalized position of [value] in [minValue]..[maxValue], clamped to 0..1. */
internal fun gaugeFraction(value: Float, minValue: Float, maxValue: Float): Float {
    if (maxValue <= minValue) return 0f
    return ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
}

// Arc geometry: 270° sweep with the gap at the bottom (starts at 135°).
private const val GAUGE_START_ANGLE = 135f
private const val GAUGE_SWEEP = 270f
private const val THRESHOLD_ALPHA = 0.45f

/**
 * Arc gauge with amber/red threshold arcs (docs/DESIGN_SYSTEM.md §7): track, zone segments from
 * [warnThreshold]/[critThreshold], zone-colored fill arc and a mono-font center readout
 * ([valueText] + [unit]). Used for FPS, render time, dropped-frame % and network congestion on the
 * stats page.
 *
 * @param value current reading; clamped into [minValue]..[maxValue] for drawing.
 * @param warnThreshold amber zone boundary (top of range for RISING, bottom for FALLING).
 * @param critThreshold red zone boundary.
 * @param valueText pre-formatted readout (e.g. "59.9"); defaults to one decimal.
 */
@Composable
fun StatGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    warnThreshold: Float,
    critThreshold: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    direction: GaugeDirection = GaugeDirection.RISING,
    valueText: String? = null,
) {
    val colors = SceneDeckTheme.colors
    val zone = classifyGaugeZone(value, warnThreshold, critThreshold, direction)
    val zoneColor =
        when (zone) {
            GaugeZone.NORMAL -> MaterialTheme.colorScheme.primary
            GaugeZone.WARNING -> colors.warning
            GaugeZone.CRITICAL -> colors.meterRed
        }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val zoneText =
        when (zone) {
            GaugeZone.NORMAL -> "normal"
            GaugeZone.WARNING -> "warning"
            GaugeZone.CRITICAL -> "critical"
        }
    val readout = (valueText ?: "%.1f".format(value)) + if (unit.isNotEmpty()) " $unit" else ""

    Column(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = "$label: $readout, $zoneText"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            GaugeArc(
                value,
                minValue,
                maxValue,
                warnThreshold,
                critThreshold,
                direction,
                zoneColor,
                trackColor,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText ?: "%.1f".format(value),
                    style = MaterialTheme.typography.mono,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    textAlign = TextAlign.Center,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GaugeArc(
    value: Float,
    minValue: Float,
    maxValue: Float,
    warnThreshold: Float,
    critThreshold: Float,
    direction: GaugeDirection,
    zoneColor: Color,
    trackColor: Color,
) {
    val colors = SceneDeckTheme.colors
    Canvas(Modifier.fillMaxSize()) {
        val strokeWidth = 8.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        fun angleFor(fraction: Float) = GAUGE_START_ANGLE + GAUGE_SWEEP * fraction

        fun drawArcSegment(
            fromFraction: Float,
            toFraction: Float,
            color: Color,
            alpha: Float = 1f,
        ) {
            if (toFraction <= fromFraction) return
            drawArc(
                color = color,
                startAngle = angleFor(fromFraction),
                sweepAngle = GAUGE_SWEEP * (toFraction - fromFraction),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = alpha,
                style = style,
            )
        }

        val warnF = gaugeFraction(warnThreshold, minValue, maxValue)
        val critF = gaugeFraction(critThreshold, minValue, maxValue)
        val valueF = gaugeFraction(value, minValue, maxValue)

        // Track.
        drawArcSegment(0f, 1f, trackColor)

        // Threshold arcs: RISING puts amber/red at the top of the range,
        // FALLING at the bottom.
        when (direction) {
            GaugeDirection.RISING -> {
                drawArcSegment(warnF, critF, colors.warning, THRESHOLD_ALPHA)
                drawArcSegment(critF, 1f, colors.meterRed, THRESHOLD_ALPHA)
            }

            GaugeDirection.FALLING -> {
                drawArcSegment(0f, critF, colors.meterRed, THRESHOLD_ALPHA)
                drawArcSegment(critF, warnF, colors.warning, THRESHOLD_ALPHA)
            }
        }

        // The arc conveys progress without drawing across the numeric readout.
        drawArcSegment(0f, valueF, zoneColor)
    }
}
