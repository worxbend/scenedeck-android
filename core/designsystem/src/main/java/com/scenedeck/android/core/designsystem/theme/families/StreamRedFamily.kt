package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Stream Red: broadcast red tally accents on a deep neutral base (desktop port). */
internal object StreamRedFamily {
    val light = lightColorScheme(
        primary = Color(0xFFC8102E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDAD8),
        onPrimaryContainer = Color(0xFF410008),
        secondary = Color(0xFF775655),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDAD8),
        onSecondaryContainer = Color(0xFF2C1514),
        tertiary = Color(0xFF7A5730),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDCB8),
        onTertiaryContainer = Color(0xFF2A1700),
        background = Color(0xFFFFF8F7),
        onBackground = Color(0xFF221918),
        surface = Color(0xFFFFF8F7),
        onSurface = Color(0xFF221918),
        surfaceVariant = Color(0xFFF4DDDB),
        onSurfaceVariant = Color(0xFF534342),
        outline = Color(0xFF857372),
        inverseSurface = Color(0xFF382E2D),
        inverseOnSurface = Color(0xFFFFEDE9),
        inversePrimary = Color(0xFFFF4D55),
    )

    val dark = darkColorScheme(
        primary = Color(0xFFFF4D55),
        onPrimary = Color(0xFF40060A),
        primaryContainer = Color(0xFF5C151A),
        onPrimaryContainer = Color(0xFFFFDAD8),
        secondary = Color(0xFFE5BDC0),
        onSecondary = Color(0xFF43292B),
        secondaryContainer = Color(0xFF5C3F41),
        onSecondaryContainer = Color(0xFFFFDAD8),
        tertiary = Color(0xFFEBBE94),
        onTertiary = Color(0xFF452A08),
        tertiaryContainer = Color(0xFF5F401C),
        onTertiaryContainer = Color(0xFFFFDCB8),
        background = Color(0xFF161214),
        onBackground = Color(0xFFF0DEE0),
        surface = Color(0xFF1E191B),
        onSurface = Color(0xFFF0DEE0),
        surfaceVariant = Color(0xFF2B2124),
        onSurfaceVariant = Color(0xFFD7C1C3),
        outline = Color(0xFF6E565A),
        inverseSurface = Color(0xFFF0DEE0),
        inverseOnSurface = Color(0xFF382E2D),
        inversePrimary = Color(0xFFC8102E),
    )
}
