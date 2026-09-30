package com.scenedeck.android.feature.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.designsystem.components.TBar
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Studio-mode command bar (FEATURE_SPEC §2/§8): a prominent TRANSITION button (preview → program
 * with the current transition), a CUT button (instant swap), and the current-transition chip
 * opening the picker sheet.
 */
@Composable
fun StudioModeBar(
    deckState: DeckState,
    onTransitionClick: () -> Unit,
    onCutClick: () -> Unit,
    onTransitionSelect: (String) -> Unit,
    onTransitionDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.FULL,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    val colors = SceneDeckTheme.colors

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && motionLevel == MotionLevel.FULL) 0.96f else 1f,
            label = "transitionPress",
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onTransitionClick,
            interactionSource = interactionSource,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).scale(scale),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.transition_button),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                val current = deckState.currentTransition
                if (current != null) {
                    Text(
                        text = "${current.name} · ${current.durationMs ?: "—"} ms",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onCutClick,
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.cut_button))
        }
        IconButton(
            onClick = {
                advancedOpen = !advancedOpen
                pickerOpen = true
            }
        ) {
            Icon(SceneDeckIcons.Settings, stringResource(R.string.transition_options))
        }
    }

    if (advancedOpen) {
        TBar(
            motionLevel = motionLevel,
            onTrigger = onTransitionClick,
            modifier = Modifier.height(56.dp),
        )
    }

    if (pickerOpen) {
        TransitionPickerSheet(
            current = deckState.currentTransition,
            transitions = deckState.transitions,
            onSelect = onTransitionSelect,
            onDurationChange = onTransitionDurationChange,
            onDismiss = { pickerOpen = false },
        )
    }
}
