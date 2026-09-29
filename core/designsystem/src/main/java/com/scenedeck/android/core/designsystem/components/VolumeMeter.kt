package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.VolumeMeterReading
import kotlin.math.max
import kotlin.math.min

/** Zone thresholds in dB (OBS semantics). */
private const val YELLOW_DB = -20f
private const val RED_DB = -9f

private const val DECAY_DB_PER_MS = 60f / 300f // ~300 ms full-scale fall-off
private const val NOTCH_DECAY_DB_PER_MS = 60f / 800f
private const val PEAK_HOLD_MS = 20_000L
private const val BASE_SQUARE_DP = 6f
private const val LINE_THICKNESS_DP = 1.5f

/**
 * Per-channel render state carried across draw calls WITHOUT Compose snapshots —
 * mutating it in the draw phase never invalidates composition.
 */
private class ChannelRenderState {
    var displayDb = METER_DB_FLOOR
    var notchDb = METER_DB_FLOOR
    var peakHoldDb = METER_DB_FLOOR
    var peakHoldSinceMs = 0L
    var lastFrameMs = 0L

    fun update(magnitudeDb: Float, peakDb: Float, nowMs: Long, animate: Boolean) {
        val dtMs = if (lastFrameMs == 0L) 0L else (nowMs - lastFrameMs).coerceAtMost(100L)
        lastFrameMs = nowMs
        if (!animate) {
            displayDb = magnitudeDb
            notchDb = magnitudeDb
        } else {
            displayDb = max(magnitudeDb, displayDb - DECAY_DB_PER_MS * dtMs)
            notchDb = max(magnitudeDb, notchDb - NOTCH_DECAY_DB_PER_MS * dtMs)
        }
        if (peakDb >= peakHoldDb || nowMs - peakHoldSinceMs > PEAK_HOLD_MS) {
            peakHoldDb = peakDb
            peakHoldSinceMs = nowMs
        }
    }
}

private class MeterRenderState {
    val channels = mutableListOf<ChannelRenderState>()

    fun channel(index: Int): ChannelRenderState {
        while (channels.size <= index) channels += ChannelRenderState()
        return channels[index]
    }
}

/**
 * OBS-accurate volume meter (docs/DESIGN_SYSTEM.md §7): −60…0 dB scale,
 * green/yellow/red zones at −20/−9 dB, one bar per channel, magnitude fill with
 * fall-off, slow loudness notch, 20 s peak-hold line, pre-fader base square.
 *
 * Reads [levelsHolder] in the DRAW PHASE ONLY: 50 ms meter updates redraw the
 * canvas without any recomposition. Empty channel arrays render the base square
 * only (protocol quirk guard).
 *
 * @param faderMul current fader position (pre-fader base square), linear multiplier.
 * @param muted draws the meter dimmed (base square stays visible).
 * @param motionLevel OFF renders levels instantly (no fall-off animation).
 */
@Composable
fun VolumeMeter(
    levelsHolder: MeterLevelsHolder,
    faderMul: Double,
    muted: Boolean,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val colors = SceneDeckTheme.colors
    val renderState = remember { MeterRenderState() }
    val zoneGreen = colors.meterGreen
    val zoneYellow = colors.meterYellow
    val zoneRed = colors.meterRed

    fun zoneColorForDb(db: Float): Color = when {
        db > RED_DB -> zoneRed
        db > YELLOW_DB -> zoneYellow
        else -> zoneGreen
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val reading: VolumeMeterReading? = levelsHolder.reading.value
        val nowMs = System.currentTimeMillis()
        val animate = motionLevel != MotionLevel.OFF

        val channelCount = reading?.channels?.size ?: 0
        val gapPx = 2.dp.toPx()
        val barWidth =
            if (channelCount == 0) size.width else (size.width - gapPx * (channelCount - 1)) / channelCount

        fun yFor(db: Float): Float = size.height * (1f - dbToFraction(db))

        // Zone background segments (full width, subtle).
        val zoneAlpha = 0.14f
        drawRect(
            color = zoneGreen.copy(alpha = zoneAlpha),
            size = Size(size.width, yFor(YELLOW_DB)),
        )
        drawRect(
            color = zoneYellow.copy(alpha = zoneAlpha),
            topLeft = Offset(0f, yFor(RED_DB)),
            size = Size(size.width, yFor(YELLOW_DB) - yFor(RED_DB)),
        )
        drawRect(
            color = zoneRed.copy(alpha = zoneAlpha),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, yFor(RED_DB)),
        )

        val meterAlpha = if (muted) 0.45f else 1f

        reading?.channels?.forEachIndexed { index, channel ->
            val state = renderState.channel(index)
            state.update(
                magnitudeDb = mulToDb(channel.magnitudeMul),
                peakDb = mulToDb(channel.peakMul),
                nowMs = nowMs,
                animate = animate,
            )
            val x = index * (barWidth + gapPx)
            val barColor = zoneColorForDb(state.displayDb)

            // Magnitude fill (bottom → level).
            val top = yFor(state.displayDb)
            if (top < size.height) {
                drawRoundRect(
                    color = barColor.copy(alpha = meterAlpha),
                    topLeft = Offset(x, top),
                    size = Size(barWidth, size.height - top),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            // Loudness notch (~slow decay marker).
            val notchY = yFor(state.notchDb)
            drawRect(
                color = Color.White.copy(alpha = 0.6f * meterAlpha),
                topLeft = Offset(x, notchY - LINE_THICKNESS_DP.dp.toPx() / 2),
                size = Size(barWidth, LINE_THICKNESS_DP.dp.toPx()),
            )
            // Peak-hold line (20 s).
            val peakY = yFor(state.peakHoldDb)
            drawRect(
                color = zoneColorForDb(state.peakHoldDb).copy(alpha = meterAlpha),
                topLeft = Offset(x, peakY - LINE_THICKNESS_DP.dp.toPx() / 2),
                size = Size(barWidth, LINE_THICKNESS_DP.dp.toPx()),
            )
        }

        // Pre-fader base square — visible even when muted or with no meter data.
        val baseY = yFor(mulToDb(faderMul.toFloat()))
        val square = BASE_SQUARE_DP.dp.toPx()
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(
                (size.width - square) / 2,
                (baseY - square / 2).coerceIn(0f, size.height - square),
            ),
            size = Size(square, square),
        )
    }
}
