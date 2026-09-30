package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Obsidian: near-black surfaces, high-contrast white/violet accents (desktop port). */
internal object ObsidianFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF17171C),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF3A3A44),
            onPrimaryContainer = Color(0xFFF2F2F7),
            secondary = Color(0xFF6D4FC4),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE7DEFF),
            onSecondaryContainer = Color(0xFF22005D),
            tertiary = Color(0xFF4F4358),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE2D9EC),
            onTertiaryContainer = Color(0xFF17101F),
            background = Color(0xFFFCFCFE),
            onBackground = Color(0xFF1B1B1F),
            surface = Color(0xFFFCFCFE),
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE8E8EE),
            onSurfaceVariant = Color(0xFF48484F),
            outline = Color(0xFF797981),
            inverseSurface = Color(0xFF17171C),
            inverseOnSurface = Color(0xFFF2F2F7),
            inversePrimary = Color(0xFFB79CFF),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFFF2F2F7),
            onPrimary = Color(0xFF17171C),
            primaryContainer = Color(0xFF3A3A44),
            onPrimaryContainer = Color(0xFFF2F2F7),
            secondary = Color(0xFFB79CFF),
            onSecondary = Color(0xFF2A1660),
            secondaryContainer = Color(0xFF412E77),
            onSecondaryContainer = Color(0xFFE7DEFF),
            tertiary = Color(0xFFCDC3DA),
            onTertiary = Color(0xFF211B28),
            tertiaryContainer = Color(0xFF38323F),
            onTertiaryContainer = Color(0xFFE2D9EC),
            background = Color(0xFF0A0A0C),
            onBackground = Color(0xFFF2F2F7),
            surface = Color(0xFF121214),
            onSurface = Color(0xFFF2F2F7),
            surfaceVariant = Color(0xFF1C1C21),
            onSurfaceVariant = Color(0xFFC9C9D2),
            outline = Color(0xFF3A3A44),
            inverseSurface = Color(0xFFF2F2F7),
            inverseOnSurface = Color(0xFF17171C),
            inversePrimary = Color(0xFF17171C),
        )
}
