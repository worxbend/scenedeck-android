package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Mint Control: fresh green-teal; designed light-first (mobile exclusive). */
internal object MintControlFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF006D5B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF9FF2DE),
            onPrimaryContainer = Color(0xFF00201A),
            secondary = Color(0xFF4A635C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCCE9DF),
            onSecondaryContainer = Color(0xFF05201A),
            tertiary = Color(0xFF3E6374),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFC1E8FC),
            onTertiaryContainer = Color(0xFF001F2A),
            background = Color(0xFFF6FBF8),
            onBackground = Color(0xFF171D1A),
            surface = Color(0xFFF6FBF8),
            onSurface = Color(0xFF171D1A),
            surfaceVariant = Color(0xFFDBE5E0),
            onSurfaceVariant = Color(0xFF3F4945),
            outline = Color(0xFF6F7975),
            inverseSurface = Color(0xFF2B322F),
            inverseOnSurface = Color(0xFFECF2EE),
            inversePrimary = Color(0xFF83D5C2),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFF83D5C2),
            onPrimary = Color(0xFF00382E),
            primaryContainer = Color(0xFF005144),
            onPrimaryContainer = Color(0xFF9FF2DE),
            secondary = Color(0xFFB0CCC3),
            onSecondary = Color(0xFF1C352E),
            secondaryContainer = Color(0xFF324B44),
            onSecondaryContainer = Color(0xFFCCE9DF),
            tertiary = Color(0xFFA5CCDF),
            onTertiary = Color(0xFF073544),
            tertiaryContainer = Color(0xFF254B5B),
            onTertiaryContainer = Color(0xFFC1E8FC),
            background = Color(0xFF0E1513),
            onBackground = Color(0xFFDDE4E0),
            surface = Color(0xFF141B19),
            onSurface = Color(0xFFDDE4E0),
            surfaceVariant = Color(0xFF1C2522),
            onSurfaceVariant = Color(0xFFBFC9C4),
            outline = Color(0xFF5A6B64),
            inverseSurface = Color(0xFFDDE4E0),
            inverseOnSurface = Color(0xFF2B322F),
            inversePrimary = Color(0xFF006D5B),
        )
}
