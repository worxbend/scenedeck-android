package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** SceneDeck brand: signature deep-space indigo + electric violet accent. */
internal object SceneDeckFamily {
    val light = lightColorScheme(
        primary = Color(0xFF6247A6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF1D0050),
        secondary = Color(0xFF625B70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1E192B),
        tertiary = Color(0xFF00696B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF9CF1F3),
        onTertiaryContainer = Color(0xFF002020),
        background = Color(0xFFFDF9FF),
        onBackground = Color(0xFF1B1826),
        surface = Color(0xFFFDF9FF),
        onSurface = Color(0xFF1B1826),
        surfaceVariant = Color(0xFFE9E4F3),
        onSurfaceVariant = Color(0xFF494553),
        outline = Color(0xFF7A7488),
        inverseSurface = Color(0xFF322F3B),
        inverseOnSurface = Color(0xFFF5EFFA),
        inversePrimary = Color(0xFFCBB8FF),
    )

    val dark = darkColorScheme(
        primary = Color(0xFFCBB8FF),
        onPrimary = Color(0xFF331069),
        primaryContainer = Color(0xFF4A2F81),
        onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFCBC2DB),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFF7EE0D2),
        onTertiary = Color(0xFF003736),
        tertiaryContainer = Color(0xFF00504E),
        onTertiaryContainer = Color(0xFF9CF1F3),
        background = Color(0xFF100D1E),
        onBackground = Color(0xFFE6E1F2),
        surface = Color(0xFF100D1E),
        onSurface = Color(0xFFE6E1F2),
        surfaceVariant = Color(0xFF201C38),
        onSurfaceVariant = Color(0xFFC9C3DA),
        outline = Color(0xFF6E6885),
        inverseSurface = Color(0xFFE6E1F2),
        inverseOnSurface = Color(0xFF322F3B),
        inversePrimary = Color(0xFF6247A6),
    )
}
