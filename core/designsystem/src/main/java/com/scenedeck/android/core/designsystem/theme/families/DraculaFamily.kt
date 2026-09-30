package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Dracula: canonical draculatheme.com palette (dark); light is a crafted sibling. */
internal object DraculaFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF7C4DBE),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF2A0057),
            secondary = Color(0xFFD6336C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFD9E4),
            onSecondaryContainer = Color(0xFF3E001D),
            tertiary = Color(0xFF006874),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF97F0FF),
            onTertiaryContainer = Color(0xFF001F24),
            background = Color(0xFFF8F8F2),
            onBackground = Color(0xFF282A36),
            surface = Color(0xFFF8F8F2),
            onSurface = Color(0xFF282A36),
            surfaceVariant = Color(0xFFEBE8DF),
            onSurfaceVariant = Color(0xFF4C4A55),
            outline = Color(0xFF7D7A87),
            inverseSurface = Color(0xFF282A36),
            inverseOnSurface = Color(0xFFF8F8F2),
            inversePrimary = Color(0xFFBD93F9),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFFBD93F9),
            onPrimary = Color(0xFF282A36),
            primaryContainer = Color(0xFF5A3E85),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFFF79C6),
            onSecondary = Color(0xFF282A36),
            secondaryContainer = Color(0xFF6E3353),
            onSecondaryContainer = Color(0xFFFFD9E4),
            tertiary = Color(0xFF8BE9FD),
            onTertiary = Color(0xFF282A36),
            tertiaryContainer = Color(0xFF2F5A66),
            onTertiaryContainer = Color(0xFFBEEFF9),
            background = Color(0xFF282A36),
            onBackground = Color(0xFFF8F8F2),
            surface = Color(0xFF282A36),
            onSurface = Color(0xFFF8F8F2),
            surfaceVariant = Color(0xFF44475A),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF6272A4),
            error = Color(0xFFFF5555),
            onError = Color(0xFF282A36),
            inverseSurface = Color(0xFFF8F8F2),
            inverseOnSurface = Color(0xFF282A36),
            inversePrimary = Color(0xFF7C4DBE),
        )
}
