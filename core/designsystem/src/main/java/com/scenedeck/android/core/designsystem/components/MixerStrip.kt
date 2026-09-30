package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.MixerScope

/**
 * Full mixer channel strip v2 (docs/DESIGN_SYSTEM.md §7): tonal scope chip, name, mono dB readout,
 * meter pair (left) next to a REAL fader (right) — 6dp rounded track with dB ticks, accent fill
 * below the thumb, pill thumb with grip line, and a floating dB bubble while dragging. Footer:
 * tonal mute, lock, optional extras.
 *
 * Fader interaction: [onVolumePreview] fires continuously while dragging (local UI only —
 * coalesce/debounce OBS writes above), [onVolumeCommit] fires once on drag end (send the final
 * value). All targets ≥ 48dp.
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
    /** Optional extras affordance (audio extras sheet); hidden when null. */
    onExtrasClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = SceneDeckTheme.colors
    val db = mulToDb(volumeMul)

    Surface(
        modifier = modifier.width(120.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScopeBadge(scope = scope, scopePath = scopePath)
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                minLines = 2,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDb(db),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.height(220.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                VolumeMeter(
                    levelsHolder = meterHolder,
                    faderMul = volumeMul,
                    muted = muted,
                    motionLevel = motionLevel,
                    modifier = Modifier.width(18.dp).fillMaxHeight(),
                )
                FaderTrack(
                    volumeMul = volumeMul,
                    enabled = !locked,
                    contentDescription = "Volume fader for $name, ${formatDb(db)}",
                    onPreview = onVolumePreview,
                    onCommit = { value ->
                        if (hapticsEnabled)
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        onVolumeCommit(value)
                    },
                    modifier = Modifier.width(48.dp).fillMaxHeight(),
                )
            }

            MixerFooter(name, muted, locked, onToggleMute, onToggleLock, onExtrasClick)
        }
    }
}

@Composable
private fun ScopeBadge(scope: MixerScope, scopePath: String?) {
    val (icon, label) =
        when (scope) {
            MixerScope.GLOBAL -> SceneIcon.GLOBE to "Global"
            MixerScope.SCENE -> SceneIcon.SCENES to "Scene"
            MixerScope.NESTED -> SceneIcon.INVENTORY to "Nested"
            MixerScope.GROUP -> SceneIcon.USERS to "Group"
        }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
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

@Composable
private fun MixerFooter(
    name: String,
    muted: Boolean,
    locked: Boolean,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onExtrasClick: (() -> Unit)?,
) {
    val colors = SceneDeckTheme.colors
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MixerMuteButton(name, muted, locked, onToggleMute)
        MixerOptions(name, locked, onToggleLock, onExtrasClick)
    }
}

@Composable
private fun MixerMuteButton(
    name: String,
    muted: Boolean,
    locked: Boolean,
    onToggleMute: () -> Unit,
) {
    val colors = SceneDeckTheme.colors
    val muteTint = if (muted) colors.meterRed else MaterialTheme.colorScheme.onSurfaceVariant
    val muteIcon = if (muted) SceneIcon.MIC_OFF.imageVector else SceneIcon.MIC.imageVector
    val muteDescription = if (muted) "Unmute $name" else "Mute $name"
    IconButton(
        onClick = onToggleMute,
        enabled = !locked,
        modifier = Modifier.size(48.dp),
    ) {
        val muteBg =
            if (muted) {
                colors.meterRed.copy(alpha = 0.22f)
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            }
        Box(
            modifier = Modifier.size(32.dp).background(muteBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = muteIcon,
                contentDescription = muteDescription,
                tint = muteTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MixerOptions(
    name: String,
    locked: Boolean,
    onToggleLock: () -> Unit,
    onExtrasClick: (() -> Unit)?,
) {
    val colors = SceneDeckTheme.colors
    val optionsIcon =
        when {
            locked -> SceneIcon.LOCK.imageVector
            onExtrasClick != null -> SceneIcon.SETTINGS.imageVector
            else -> SceneIcon.LOCK_OPEN.imageVector
        }
    val optionsDescription =
        when {
            onExtrasClick != null -> "Channel options for $name"
            locked -> "Unlock $name controls"
            else -> "Lock $name controls"
        }
    val optionsTint = if (locked) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
    var optionsOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = {
                if (onExtrasClick == null) onToggleLock() else optionsOpen = true
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = optionsIcon,
                contentDescription = optionsDescription,
                tint = optionsTint,
                modifier = Modifier.size(18.dp),
            )
        }
        MixerOptionsMenu(optionsOpen, { optionsOpen = false }, locked, onToggleLock, onExtrasClick)
    }
}

@Composable
private fun MixerOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    locked: Boolean,
    onToggleLock: () -> Unit,
    onExtrasClick: (() -> Unit)?,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (locked) "Unlock controls" else "Lock controls") },
            onClick = {
                onDismiss()
                onToggleLock()
            },
            leadingIcon = {
                Icon(
                    if (locked) SceneIcon.LOCK_OPEN.imageVector else SceneIcon.LOCK.imageVector,
                    contentDescription = null,
                )
            },
        )
        if (onExtrasClick != null) {
            DropdownMenuItem(
                text = { Text("Audio settings") },
                onClick = {
                    onDismiss()
                    onExtrasClick()
                },
                enabled = !locked,
                leadingIcon = { Icon(SceneIcon.SETTINGS.imageVector, contentDescription = null) },
            )
        }
    }
}
