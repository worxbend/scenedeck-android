package com.scenedeck.android.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Presentation only. The app's process-selected dependency graph enforces isolation. */
val LocalSimulation = staticCompositionLocalOf { false }

@Composable
fun SimulationNotice(modifier: Modifier = Modifier) {
    if (LocalSimulation.current) {
        Text(
            "DEMO · Local simulation only",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}
