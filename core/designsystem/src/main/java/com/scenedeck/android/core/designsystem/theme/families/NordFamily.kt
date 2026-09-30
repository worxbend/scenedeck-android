package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Nord: canonical arctic blue-grey palette (nordtheme.com). */
internal object NordFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF5E81AC),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD8E2F0),
            onPrimaryContainer = Color(0xFF2E3440),
            secondary = Color(0xFF81A1C1),
            onSecondary = Color(0xFF2E3440),
            secondaryContainer = Color(0xFFDCE5F0),
            onSecondaryContainer = Color(0xFF2E3440),
            tertiary = Color(0xFFB48EAD),
            onTertiary = Color(0xFF2E3440),
            tertiaryContainer = Color(0xFFEAD8E6),
            onTertiaryContainer = Color(0xFF3B2E38),
            background = Color(0xFFECEFF4),
            onBackground = Color(0xFF2E3440),
            surface = Color(0xFFECEFF4),
            onSurface = Color(0xFF2E3440),
            surfaceVariant = Color(0xFFE5E9F0),
            onSurfaceVariant = Color(0xFF434C5E),
            outline = Color(0xFF7B88A1),
            inverseSurface = Color(0xFF2E3440),
            inverseOnSurface = Color(0xFFECEFF4),
            inversePrimary = Color(0xFF88C0D0),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFF88C0D0),
            onPrimary = Color(0xFF2E3440),
            primaryContainer = Color(0xFF4C566A),
            onPrimaryContainer = Color(0xFFECEFF4),
            secondary = Color(0xFF81A1C1),
            onSecondary = Color(0xFF2E3440),
            secondaryContainer = Color(0xFF434C5E),
            onSecondaryContainer = Color(0xFFDCE5F0),
            tertiary = Color(0xFFB48EAD),
            onTertiary = Color(0xFF2E3440),
            tertiaryContainer = Color(0xFF4E3B49),
            onTertiaryContainer = Color(0xFFEAD8E6),
            background = Color(0xFF2E3440),
            onBackground = Color(0xFFECEFF4),
            surface = Color(0xFF2E3440),
            onSurface = Color(0xFFECEFF4),
            surfaceVariant = Color(0xFF3B4252),
            onSurfaceVariant = Color(0xFFD8DEE9),
            outline = Color(0xFF616E88),
            inverseSurface = Color(0xFFECEFF4),
            inverseOnSurface = Color(0xFF2E3440),
            inversePrimary = Color(0xFF5E81AC),
        )
}
