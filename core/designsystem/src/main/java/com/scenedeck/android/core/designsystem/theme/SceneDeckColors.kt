package com.scenedeck.android.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Product-meaning colors that extend [androidx.compose.material3.ColorScheme].
 *
 * These hues carry broadcast semantics (docs/DESIGN_SYSTEM.md §3) and keep their roles across every
 * theme family: program/live is always a red tally, preview is always green, meter zones follow the
 * OBS −20 dB / −9 dB thresholds. Light/dark defaults adapt tone; individual families may override
 * (see `theme/families/ThemeFamilies.kt`).
 */
@Immutable
data class SceneDeckColors(
    /** On-air / program tally red. Used ONLY for on-air state. */
    val program: Color,
    val onProgram: Color,
    /** Preview (studio mode) green. */
    val preview: Color,
    val onPreview: Color,
    /** Recording indicator red (pulses at 1 Hz when motion allows). */
    val recording: Color,
    /** Scene ready-to-take tone (idle-but-armed). */
    val ready: Color,
    /** Neutral idle/disconnected tone. */
    val idle: Color,
    /** Meter zone: below −20 dB. */
    val meterGreen: Color,
    /** Meter zone: −20…−9 dB. */
    val meterYellow: Color,
    /** Meter zone: above −9 dB. */
    val meterRed: Color,
    /** Stats warning threshold (amber; red at critical). */
    val warning: Color,
) {
    companion object {
        val Light: SceneDeckColors =
            SceneDeckColors(
                program = Color(0xFFD32F2F),
                onProgram = Color(0xFFFFFFFF),
                preview = Color(0xFF2E7D32),
                onPreview = Color(0xFFFFFFFF),
                recording = Color(0xFFD32F2F),
                ready = Color(0xFF558B2F),
                idle = Color(0xFF757575),
                meterGreen = Color(0xFF2E7D32),
                meterYellow = Color(0xFFF9A825),
                meterRed = Color(0xFFD32F2F),
                warning = Color(0xFFF9A825),
            )

        val Dark: SceneDeckColors =
            SceneDeckColors(
                program = Color(0xFFFF5252),
                onProgram = Color(0xFF2B0505),
                preview = Color(0xFF66BB6A),
                onPreview = Color(0xFF06230B),
                recording = Color(0xFFFF5252),
                ready = Color(0xFF81C784),
                idle = Color(0xFF8A8A92),
                meterGreen = Color(0xFF4CAF50),
                meterYellow = Color(0xFFFFC107),
                meterRed = Color(0xFFF44336),
                warning = Color(0xFFFFB300),
            )
    }
}

/** Current [SceneDeckColors]; provided by [SceneDeckTheme]. */
val LocalSceneDeckColors = staticCompositionLocalOf { SceneDeckColors.Dark }
