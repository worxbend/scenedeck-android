package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.MixerState
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.components.VolumeMeter
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Compact mixer row embedded in the Live deck: one meter + mute per discovered
 * (active-scope) input, with an "Open mixer" affordance into the full page.
 */
@Composable
fun EmbeddedMixerRow(
    mixerState: MixerState,
    levelsStore: MeterLevelsStore,
    motionLevel: MotionLevel,
    onToggleMute: (String, Boolean) -> Unit,
    onOpenMixer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Mixer",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenMixer) {
                Text("Open mixer")
            }
        }
        if (mixerState.inputs.isEmpty()) {
            Text(
                text = "No audio sources in this scene",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(mixerState.inputs, key = { it.name }) { input ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(52.dp),
                    ) {
                        Text(
                            text = input.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VolumeMeter(
                                levelsHolder = levelsStore.holder(input.name),
                                faderMul = input.volumeMul,
                                muted = input.muted,
                                motionLevel = motionLevel,
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(56.dp),
                            )
                            IconButton(
                                onClick = { onToggleMute(input.name, !input.muted) },
                                enabled = !input.locked,
                                modifier = Modifier.width(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (input.muted) {
                                        SceneIcon.MIC_OFF.imageVector
                                    } else {
                                        SceneIcon.MIC.imageVector
                                    },
                                    contentDescription =
                                        if (input.muted) "Unmute ${input.name}" else "Mute ${input.name}",
                                    tint = if (input.muted) {
                                        SceneDeckTheme.colors.meterRed
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.width(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
