package com.scenedeck.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.scenedeck.android.core.designsystem.R

private val googleFontProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.scenedeck_google_fonts_certs,
    )

private val Inter = GoogleFont("Inter")
private val JetBrainsMono = GoogleFont("JetBrains Mono")

/** UI/body typeface: Inter via downloadable Google Fonts (falls back to system sans). */
val InterFontFamily =
    FontFamily(
        Font(googleFont = Inter, fontProvider = googleFontProvider, weight = FontWeight.Light),
        Font(googleFont = Inter, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = Inter, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = Inter, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = Inter, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    )

/** Readout typeface: JetBrains Mono (dB meters, counters, timecodes). */
val JetBrainsMonoFontFamily =
    FontFamily(
        Font(
            googleFont = JetBrainsMono,
            fontProvider = googleFontProvider,
            weight = FontWeight.Normal,
        ),
        Font(
            googleFont = JetBrainsMono,
            fontProvider = googleFontProvider,
            weight = FontWeight.Medium,
        ),
        Font(
            googleFont = JetBrainsMono,
            fontProvider = googleFontProvider,
            weight = FontWeight.Bold,
        ),
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
