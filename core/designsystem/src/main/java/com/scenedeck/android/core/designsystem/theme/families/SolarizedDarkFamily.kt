package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Solarized: canonical ethanschoonover.com/solarized base + accent tones. */
internal object SolarizedDarkFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF268BD2),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCBE6FF),
            onPrimaryContainer = Color(0xFF002B36),
            secondary = Color(0xFF2AA198),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFB5EAE3),
            onSecondaryContainer = Color(0xFF002B36),
            tertiary = Color(0xFF6C71C4),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE0E0FF),
            onTertiaryContainer = Color(0xFF161A4F),
            background = Color(0xFFFDF6E3),
            onBackground = Color(0xFF586E75),
            surface = Color(0xFFFDF6E3),
            onSurface = Color(0xFF586E75),
            surfaceVariant = Color(0xFFEEE8D5),
            onSurfaceVariant = Color(0xFF657B83),
            outline = Color(0xFF93A1A1),
            error = Color(0xFFDC322F),
            onError = Color(0xFFFFFFFF),
            inverseSurface = Color(0xFF073642),
            inverseOnSurface = Color(0xFFEEE8D5),
            inversePrimary = Color(0xFF268BD2),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFF268BD2),
            onPrimary = Color(0xFF002B36),
            primaryContainer = Color(0xFF0F4A6E),
            onPrimaryContainer = Color(0xFFCBE6FF),
            secondary = Color(0xFF2AA198),
            onSecondary = Color(0xFF002B36),
            secondaryContainer = Color(0xFF17514C),
            onSecondaryContainer = Color(0xFFB5EAE3),
            tertiary = Color(0xFF6C71C4),
            onTertiary = Color(0xFF002B36),
            tertiaryContainer = Color(0xFF3A3F7A),
            onTertiaryContainer = Color(0xFFE0E0FF),
            background = Color(0xFF002B36),
            onBackground = Color(0xFF93A1A1),
            surface = Color(0xFF002B36),
            onSurface = Color(0xFF93A1A1),
            surfaceVariant = Color(0xFF073642),
            onSurfaceVariant = Color(0xFF839496),
            outline = Color(0xFF586E75),
            error = Color(0xFFDC322F),
            onError = Color(0xFF002B36),
            inverseSurface = Color(0xFFEEE8D5),
            inverseOnSurface = Color(0xFF073642),
            inversePrimary = Color(0xFF268BD2),
        )
}
