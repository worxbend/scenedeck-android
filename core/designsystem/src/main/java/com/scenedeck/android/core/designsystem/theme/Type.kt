package com.scenedeck.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scenedeck.android.core.designsystem.R

/** Bundled Inter 4.1: typography works offline without a Google Play services provider. */
val InterFontFamily =
    FontFamily(
        Font(R.font.inter_light, FontWeight.Light),
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** Bundled JetBrains Mono 2.304 for dB readouts, counters, and timecodes. */
val JetBrainsMonoFontFamily =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )

private val baseline = Typography()

/** M3 Expressive-flavored type scale on Inter. */
val SceneDeckTypography: Typography =
    Typography(
        displayLarge =
            baseline.displayLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
            ),
        displayMedium =
            baseline.displayMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
            ),
        displaySmall =
            baseline.displaySmall.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
            ),
        headlineLarge =
            baseline.headlineLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
        headlineMedium =
            baseline.headlineMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
        headlineSmall =
            baseline.headlineSmall.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
        titleLarge =
            baseline.titleLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            baseline.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
            ),
        titleSmall =
            baseline.titleSmall.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
            ),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge =
            baseline.labelLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
        labelMedium =
            baseline.labelMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
            ),
        labelSmall =
            baseline.labelSmall.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
            ),
    )

/**
 * Monospace readout style with tabular figures for dB values, counters and timecodes
 * (docs/DESIGN_SYSTEM.md §4). Example: `Text("-12.4 dB", style = MaterialTheme.typography.mono)`.
 */
val Typography.mono: TextStyle
    get() =
        bodyMedium.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            letterSpacing = 0.sp,
        )
