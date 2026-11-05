package com.scenedeck.android.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.scenedeck.android.core.data.LiveAlertKind
import com.scenedeck.android.core.data.LiveAlertsState
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LiveAlertsHost(
    onOpen: () -> Unit,
    hapticsAllowed: Boolean,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlertFeedbackEffect(viewModel.uiState, hapticsAllowed)
    LiveAlertsBar(state, onOpen)
}

@Composable
internal fun LiveAlertsBar(
    state: LiveAlertsState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.alerts.isEmpty() && state.armedSpeech == null) return
    val first = state.alerts.firstOrNull()
    val title = first?.title ?: "Speech monitoring: ${state.armedSpeech?.inputName}"
    val detail =
        if (first != null) {
            if (state.alerts.size == 1) "1 active warning"
            else "${state.alerts.size} active warnings"
        } else
            state.readings
                .firstOrNull { it.kind == LiveAlertKind.MICROPHONE }
                ?.description
                .orEmpty()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StudioIconWell(
                SceneDeckIcons.Alerts,
                tone = if (first == null) StudioTone.SECONDARY else StudioTone.WARNING,
            )
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpen) { Text("Details") }
        }
    }
}

/** Baseline is reset on each foreground entry; old/background warnings never vibrate on return. */
@Composable
internal fun AlertFeedbackEffect(states: StateFlow<LiveAlertsState>, allowed: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val feedback = LocalHapticFeedback.current
    LaunchedEffect(lifecycle, states, allowed, feedback) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var seen = states.value.alerts.map { it.occurrence }.toSet()
            states.collect { state ->
                val fresh = state.alerts.any { it.occurrence !in seen && !it.uncertain }
                if (allowed && state.hapticsEnabled && fresh)
                    feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                seen = state.alerts.map { it.occurrence }.toSet()
            }
        }
    }
}
