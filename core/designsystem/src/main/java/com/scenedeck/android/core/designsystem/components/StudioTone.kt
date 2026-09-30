package com.scenedeck.android.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

enum class StudioTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SUCCESS,
    WARNING,
}

@Composable
fun StudioTone.color(): Color =
    when (this) {
        StudioTone.PRIMARY -> MaterialTheme.colorScheme.primary
        StudioTone.SECONDARY -> MaterialTheme.colorScheme.secondary
        StudioTone.TERTIARY -> MaterialTheme.colorScheme.tertiary
        StudioTone.SUCCESS -> SceneDeckTheme.colors.preview
        StudioTone.WARNING -> SceneDeckTheme.colors.warning
    }
