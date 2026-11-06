package com.scenedeck.android.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Overrides decorative primary roles only; broadcast/meter semantics are independent. */
enum class ThemeAccent(val label: String) {
    DEFAULT("Palette default"),
    AZURE("Azure"),
    TEAL("Teal"),
    VIOLET("Violet"),
    ROSE("Rose"),
    AMBER("Amber"),
    LIME("Lime");

    companion object {
        fun fromName(name: String): ThemeAccent = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

internal fun personalizeScheme(
    base: ColorScheme,
    dark: Boolean,
    accent: ThemeAccent,
    trueBlack: Boolean,
    highContrast: Boolean = false,
): ColorScheme {
    if (highContrast) return base
    val accented = accentScheme(base, accent, dark)
    return if (dark && trueBlack)
        accented.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF242424),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1B1B1B),
            surfaceContainerHighest = Color(0xFF242424),
            surfaceVariant = Color(0xFF242424),
        )
    else accented
}

private fun accentScheme(base: ColorScheme, accent: ThemeAccent, dark: Boolean): ColorScheme {
    val tones = accent.tones() ?: return base
    return base.copy(
        primary = if (dark) tones.light else tones.strong,
        onPrimary = if (dark) tones.ink else Color.White,
        primaryContainer = if (dark) tones.deep else tones.pale,
        onPrimaryContainer = if (dark) tones.pale else tones.ink,
        inversePrimary = if (dark) tones.strong else tones.light,
        surfaceTint = if (dark) tones.light else tones.strong,
    )
}

private data class AccentTones(
    val strong: Color,
    val light: Color,
    val deep: Color,
    val pale: Color,
    val ink: Color,
)

private fun ThemeAccent.tones(): AccentTones? =
    when (this) {
        ThemeAccent.DEFAULT -> null
        ThemeAccent.AZURE ->
            AccentTones(
                Color(0xFF175BA8),
                Color(0xFF9BCBFF),
                Color(0xFF173F68),
                Color(0xFFD9E9FF),
                Color(0xFF082C52),
            )
        ThemeAccent.TEAL ->
            AccentTones(
                Color(0xFF00666A),
                Color(0xFF66D9DD),
                Color(0xFF005053),
                Color(0xFF9CF1F3),
                Color(0xFF003739),
            )
        ThemeAccent.VIOLET ->
            AccentTones(
                Color(0xFF6442A2),
                Color(0xFFD1BCFF),
                Color(0xFF4C2B83),
                Color(0xFFECDDFF),
                Color(0xFF321264),
            )
        ThemeAccent.ROSE ->
            AccentTones(
                Color(0xFF8A3F65),
                Color(0xFFFFAFD2),
                Color(0xFF6C284D),
                Color(0xFFFFD9E8),
                Color(0xFF4D1236),
            )
        ThemeAccent.AMBER ->
            AccentTones(
                Color(0xFF765500),
                Color(0xFFF2C36A),
                Color(0xFF594000),
                Color(0xFFFFDE9F),
                Color(0xFF3E2C00),
            )
        ThemeAccent.LIME ->
            AccentTones(
                Color(0xFF4B621C),
                Color(0xFFBDD983),
                Color(0xFF354C08),
                Color(0xFFD9F5A0),
                Color(0xFF203600),
            )
    }
