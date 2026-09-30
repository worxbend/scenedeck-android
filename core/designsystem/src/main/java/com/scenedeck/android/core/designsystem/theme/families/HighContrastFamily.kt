package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.scenedeck.android.core.designsystem.theme.SceneDeckColors

/** High Contrast: accessibility-max. Pure black/white surfaces, AAA state colors. */
internal object HighContrastFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF000000),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF000000),
            onPrimaryContainer = Color(0xFFFFFFFF),
            secondary = Color(0xFF0033CC),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD6E2FF),
            onSecondaryContainer = Color(0xFF001A66),
            tertiary = Color(0xFF4B3A00),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFE500),
            onTertiaryContainer = Color(0xFF1A1300),
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF000000),
            outline = Color(0xFF000000),
            error = Color(0xFFB00020),
            onError = Color(0xFFFFFFFF),
            inverseSurface = Color(0xFF000000),
            inverseOnSurface = Color(0xFFFFFFFF),
            inversePrimary = Color(0xFFFFFFFF),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF000000),
            secondary = Color(0xFF00E5FF),
            onSecondary = Color(0xFF000000),
            secondaryContainer = Color(0xFF00363D),
            onSecondaryContainer = Color(0xFF97F0FF),
            tertiary = Color(0xFFFFE500),
            onTertiary = Color(0xFF000000),
            tertiaryContainer = Color(0xFF4B3A00),
            onTertiaryContainer = Color(0xFFFFE500),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFFFFFFFF),
            outline = Color(0xFFFFFFFF),
            error = Color(0xFFFF3333),
            onError = Color(0xFF000000),
            inverseSurface = Color(0xFFFFFFFF),
            inverseOnSurface = Color(0xFF000000),
            inversePrimary = Color(0xFF000000),
        )

    /** AAA-state semantic colors: unambiguous tally/preview/warning hues. */
    val lightSemantic =
        SceneDeckColors.Light.copy(
            program = Color(0xFFB00020),
            preview = Color(0xFF006400),
            recording = Color(0xFFB00020),
            warning = Color(0xFF7A4D00),
            meterYellow = Color(0xFF7A4D00),
        )

    val darkSemantic =
        SceneDeckColors.Dark.copy(
            program = Color(0xFFFF3333),
            preview = Color(0xFF00E676),
            recording = Color(0xFFFF3333),
            warning = Color(0xFFFFE500),
            meterYellow = Color(0xFFFFE500),
        )
}
