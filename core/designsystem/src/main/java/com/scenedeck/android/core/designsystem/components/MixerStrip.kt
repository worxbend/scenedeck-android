package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Full mixer channel strip (docs/DESIGN_SYSTEM.md §7): scope badge, name, mono dB
 * readout, vertical dB-taper fader, mute, local lock and a live [VolumeMeter].
 *
 * Fader interaction: [onVolumePreview] fires continuously while dragging (local UI
 * only — coalesce/debounce OBS writes above), [onVolumeCommit] fires once on drag
 * end (send the final value). All targets ≥ 48dp.
 */
@Composable
fun MixerStrip(
    name: String,
    scope: MixerScope,
    volumeMul: Double,
    muted: Boolean,
    locked: Boolean,
    meterHolder: MeterLevelsHolder,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    onVolumePreview: (Double) -> Unit,
    onVolumeCommit: (Double) -> Unit,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    scopePath: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = SceneDeckTheme.colors

    Surface(
        modifier = modifier.width(112.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScopeBadge(scope = scope, scopePath = scopePath)
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDb(mulToDb(volumeMul)),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FaderTrack(
                    volumeMul = volumeMul,
                    enabled = !locked,
                    contentDescription =
                        "Volume fader for $name, ${formatDb(mulToDb(volumeMul))}",
                    onPreview = onVolumePreview,
                    onCommit = { value ->
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        onVolumeCommit(value)
                    },
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                )
                VolumeMeter(
                    levelsHolder = meterHolder,
                    faderMul = volumeMul,
                    muted = muted,
                    motionLevel = motionLevel,
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight(),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggleMute,
                    enabled = !locked,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (muted) SceneIcon.MIC_OFF.imageVector else SceneIcon.MIC.imageVector,
                        contentDescription = if (muted) "Unmute $name" else "Mute $name",
                        tint = if (muted) colors.meterRed else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (locked) {
                            SceneIcon.LOCK.imageVector
                        } else {
                            SceneIcon.LOCK_OPEN.imageVector
                        },
                        contentDescription = if (locked) "Unlock $name controls" else "Lock $name controls",
                        tint = if (locked) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (locked) 1f else 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeBadge(scope: MixerScope, scopePath: String?) {
    val (icon, label) = when (scope) {
        MixerScope.GLOBAL -> SceneIcon.GLOBE to "Global"
        MixerScope.SCENE -> SceneIcon.SCENES to "Scene"
        MixerScope.NESTED -> SceneIcon.INVENTORY to "Nested"
        MixerScope.GROUP -> SceneIcon.USERS to "Group"
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = scopePath?.let { "$label · $it" } ?: label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Vertical fader with dB taper: track position ⇄ dB fraction (linear in dB over
 * the −60…0 scale), drag updates preview continuously and commits once on end.
 */
@Composable
private fun FaderTrack(
    volumeMul: Double,
    enabled: Boolean,
    contentDescription: String,
    onPreview: (Double) -> Unit,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SceneDeckTheme.colors
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    val displayFraction = if (dragFraction.isNaN()) dbToFraction(mulToDb(volumeMul)) else dragFraction

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
                // Screen-reader adjustable: progress is exposed in dB (−60…0).
                progressBarRangeInfo = ProgressBarRangeInfo(
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
            .alpha(if (enabled) 1f else 0.5f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var startFraction = displayFraction
                detectVerticalDragGestures(
                    onDragStart = { startFraction = displayFraction },
                    onDragEnd = {
                        val final = dragFraction
                        if (!final.isNaN()) {
                            onCommit(dbToMul(fractionToDb(final)).toDouble())
                            dragFraction = Float.NaN
                        }
                    },
                    onDragCancel = { dragFraction = Float.NaN },
                ) { change, dragAmount ->
                    change.consume()
                    val base = if (dragFraction.isNaN()) startFraction else dragFraction
                    val next = (base - dragAmount / size.height).coerceIn(0f, 1f)
                    dragFraction = next
                    onPreview(dbToMul(fractionToDb(next)).toDouble())
                }
            },
    ) {
        val corner = CornerRadius(6.dp.toPx())
        drawRoundRect(color = trackColor, size = size, cornerRadius = corner)
        val fillTop = size.height * (1f - displayFraction)
        drawRoundRect(
            color = fillColor.copy(alpha = 0.5f),
            topLeft = Offset(0f, fillTop),
            size = Size(size.width, size.height - fillTop),
            cornerRadius = corner,
        )
        // Thumb.
        val thumbHeight = 4.dp.toPx()
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(0f, fillTop - thumbHeight / 2),
            size = Size(size.width, thumbHeight),
            cornerRadius = corner,
        )
        // Zone ticks at −20/−9 dB.
        listOf(YELLOW_TICK to colors.meterYellow, RED_TICK to colors.meterRed).forEach { (db, color) ->
            val y = size.height * (1f - dbToFraction(db))
            drawRect(
                color = color.copy(alpha = 0.6f),
                topLeft = Offset(0f, y),
                size = Size(size.width, 1.dp.toPx()),
            )
        }
    }
}

private const val YELLOW_TICK = -20f
private const val RED_TICK = -9f

private fun fractionToDb(fraction: Float): Float = METER_DB_FLOOR + fraction * -METER_DB_FLOOR
