package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Sunset Coral: warm coral/amber tones; designed light-first (mobile exclusive). */
internal object SunsetCoralFamily {
    val light = lightColorScheme(
        primary = Color(0xFFA1402F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDAD2),
        onPrimaryContainer = Color(0xFF3D0600),
        secondary = Color(0xFF77574F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDAD2),
        onSecondaryContainer = Color(0xFF2C1510),
        tertiary = Color(0xFF8F6A00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDF9E),
        onTertiaryContainer = Color(0xFF2A1F00),
        background = Color(0xFFFFF6F2),
        onBackground = Color(0xFF231917),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF231917),
        surfaceVariant = Color(0xFFF5DDD8),
        onSurfaceVariant = Color(0xFF53433F),
        outline = Color(0xFF85736E),
        inverseSurface = Color(0xFF382E2B),
        inverseOnSurface = Color(0xFFFFEDE8),
        inversePrimary = Color(0xFFFFB4A4),
    )

    val dark = darkColorScheme(
        primary = Color(0xFFFFB4A4),
        onPrimary = Color(0xFF60150A),
        primaryContainer = Color(0xFF7E2A1E),
        onPrimaryContainer = Color(0xFFFFDAD2),
        secondary = Color(0xFFE7BDB2),
        onSecondary = Color(0xFF442A23),
        secondaryContainer = Color(0xFF5D4038),
        onSecondaryContainer = Color(0xFFFFDAD2),
        tertiary = Color(0xFFF4BF6B),
        onTertiary = Color(0xFF4B3700),
        tertiaryContainer = Color(0xFF6B4F00),
        onTertiaryContainer = Color(0xFFFFDF9E),
        background = Color(0xFF1E110E),
        onBackground = Color(0xFFF1DFDA),
        surface = Color(0xFF271511),
        onSurface = Color(0xFFF1DFDA),
        surfaceVariant = Color(0xFF35201B),
        onSurfaceVariant = Color(0xFFD8C2BC),
        outline = Color(0xFF775048),
        inverseSurface = Color(0xFFF1DFDA),
        inverseOnSurface = Color(0xFF382E2B),
        inversePrimary = Color(0xFFA1402F),
    )
}
