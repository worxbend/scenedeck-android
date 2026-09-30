package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.TransitionInfo

/** Transition picker sheet: available transitions + duration slider (FEATURE_SPEC §8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionPickerSheet(
    current: CurrentTransition?,
    transitions: List<TransitionInfo>,
    onSelect: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var durationMs by
        rememberSaveable(current?.durationMs) {
            mutableIntStateOf(current?.durationMs ?: 300)
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.transition_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            LazyColumn(
                modifier = Modifier.height(220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(transitions, key = { it.name }) { transition ->
                    val selected = transition.name == current?.name
                    Surface(
                        onClick = { onSelect(transition.name) },
                        shape = MaterialTheme.shapes.medium,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = transition.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            transition.durationMs?.let {
                                Text(
                                    text = "$it ms",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Duration is settable whenever the transition isn't fixed (OBS reports
            // `configurable` for custom settings schemas, a different concept).
            if (current != null && !current.fixed) {
                Column {
                    Text(
                        text = stringResource(R.string.transition_duration_ms, durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    val sliderState =
                        rememberSliderState(
                            value = durationMs.toFloat(),
                            steps = 19,
                            trackRange = 0f..2_000f,
                        )
                    sliderState.value = durationMs.toFloat()
                    Slider(
                        state = sliderState,
                        onValueChange = {
                            durationMs = it.toInt()
                            onDurationChange(durationMs)
                        },
                    )
                }
            }
        }
    }
}
