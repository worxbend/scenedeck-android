package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** SceneDeck brand: charcoal studio surfaces, azure controls and cyan highlights. */
internal object SceneDeckFamily {
    val light =
        lightColorScheme(
            primary = Color(0xFF1761B8),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD9E9FF),
            onPrimaryContainer = Color(0xFF071D39),
            secondary = Color(0xFF53657A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDEE7F2),
            onSecondaryContainer = Color(0xFF182331),
            tertiary = Color(0xFF00696B),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF9CF1F3),
            onTertiaryContainer = Color(0xFF002020),
            background = Color(0xFFF7F9FC),
            onBackground = Color(0xFF18212D),
            surface = Color(0xFFF7F9FC),
            onSurface = Color(0xFF18212D),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF0F4F9),
            surfaceContainer = Color(0xFFEAF0F6),
            surfaceContainerHigh = Color(0xFFE2E9F1),
            surfaceContainerHighest = Color(0xFFD9E2ED),
            outlineVariant = Color(0xFFCAD5E2),
            outline = Color(0xFF758498),
            inverseSurface = Color(0xFF283341),
            inverseOnSurface = Color(0xFFEEF3FA),
            inversePrimary = Color(0xFF8AC4FF),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFF8AC4FF),
            onPrimary = Color(0xFF082C52),
            primaryContainer = Color(0xFF173F68),
            onPrimaryContainer = Color(0xFFD9E9FF),
            secondary = Color(0xFFBBC9D9),
            onSecondary = Color(0xFF253141),
            secondaryContainer = Color(0xFF344255),
            onSecondaryContainer = Color(0xFFDEE7F2),
            tertiary = Color(0xFF52D6DC),
            onTertiary = Color(0xFF003736),
            tertiaryContainer = Color(0xFF00504E),
            onTertiaryContainer = Color(0xFF9CF1F3),
            background = Color(0xFF101317),
            onBackground = Color(0xFFE7EDF5),
            surface = Color(0xFF101317),
            onSurface = Color(0xFFE7EDF5),
            surfaceVariant = Color(0xFF252E39),
            onSurfaceVariant = Color(0xFFAEBCCC),
            surfaceContainerLowest = Color(0xFF0B0E12),
            surfaceContainerLow = Color(0xFF171C23),
            surfaceContainer = Color(0xFF1E252E),
            surfaceContainerHigh = Color(0xFF28323E),
            surfaceContainerHighest = Color(0xFF34404F),
            outlineVariant = Color(0xFF3B4859),
            outline = Color(0xFF788A9F),
            inverseSurface = Color(0xFFE7EDF5),
            inverseOnSurface = Color(0xFF283341),
            inversePrimary = Color(0xFF1761B8),
        )
}
