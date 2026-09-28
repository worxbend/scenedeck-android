package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** OBS Studio look: grey-blue panels with a teal accent (desktop port). */
internal object ObsFamily {
    val light = lightColorScheme(
        primary = Color(0xFF0E8C7A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB2EBE0),
        onPrimaryContainer = Color(0xFF00201B),
        secondary = Color(0xFF54677A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD7E5F2),
        onSecondaryContainer = Color(0xFF121F2A),
        tertiary = Color(0xFF44607D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC6DDF8),
        onTertiaryContainer = Color(0xFF021323),
        background = Color(0xFFF2F4F7),
        onBackground = Color(0xFF191C1F),
        surface = Color(0xFFFBFCFE),
        onSurface = Color(0xFF191C1F),
        surfaceVariant = Color(0xFFDDE3EA),
        onSurfaceVariant = Color(0xFF41484F),
        outline = Color(0xFF71787F),
        inverseSurface = Color(0xFF2E3134),
        inverseOnSurface = Color(0xFFEFF1F4),
        inversePrimary = Color(0xFF4EC9B0),
    )

    val dark = darkColorScheme(
        primary = Color(0xFF4EC9B0),
        onPrimary = Color(0xFF0B2B25),
        primaryContainer = Color(0xFF1F4A42),
        onPrimaryContainer = Color(0xFFB2EBE0),
        secondary = Color(0xFF8FA3B8),
        onSecondary = Color(0xFF1B2A38),
        secondaryContainer = Color(0xFF32414F),
        onSecondaryContainer = Color(0xFFD7E5F2),
        tertiary = Color(0xFFA9C4E4),
        onTertiary = Color(0xFF13293F),
        tertiaryContainer = Color(0xFF2A4057),
        onTertiaryContainer = Color(0xFFC6DDF8),
        background = Color(0xFF18191B),
        onBackground = Color(0xFFE3E8EF),
        surface = Color(0xFF22252A),
        onSurface = Color(0xFFE3E8EF),
        surfaceVariant = Color(0xFF2C3138),
        onSurfaceVariant = Color(0xFFC1C9D4),
        outline = Color(0xFF46505C),
        inverseSurface = Color(0xFFE3E8EF),
        inverseOnSurface = Color(0xFF2E3134),
        inversePrimary = Color(0xFF0E8C7A),
    )
}
