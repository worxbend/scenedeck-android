package com.scenedeck.android.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A read-only, nested theme sample; previewing never changes application settings. */
@Composable
fun ThemePreview(
    family: ThemeFamily,
    dark: Boolean,
    wallpaper: Boolean,
    accent: ThemeAccent,
    trueBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    SceneDeckTheme(
        family = family,
        darkTheme = dark,
        dynamicColor = wallpaper,
        accent = accent,
        trueBlack = trueBlack,
        motionLevel = MotionLevel.OFF,
    ) {
        Surface(
            modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your studio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Appearance preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PreviewLabel(
                        "Selected",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    PreviewLabel(
                        "PROGRAM",
                        SceneDeckTheme.colors.program,
                        SceneDeckTheme.colors.onProgram,
                    )
                    PreviewLabel(
                        "PREVIEW",
                        SceneDeckTheme.colors.preview,
                        SceneDeckTheme.colors.onPreview,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "Scenes  ·  Mixer  ·  Stats",
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewLabel(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
) {
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
