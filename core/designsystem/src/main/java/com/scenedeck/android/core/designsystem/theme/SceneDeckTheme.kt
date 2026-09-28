package com.scenedeck.android.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import com.scenedeck.android.core.designsystem.theme.families.colorSchemeFor
import com.scenedeck.android.core.designsystem.theme.families.sceneDeckColorsFor

/**
 * Built-in theme families (docs/DESIGN_SYSTEM.md §2). Each family ships a light and a
 * dark `ColorScheme` in `theme/families/` plus product-semantic [SceneDeckColors].
 * "Material You" is not a family: pass `dynamicColor = true` to [SceneDeckTheme].
 */
enum class ThemeFamily {
    /** Default. Signature deep-space indigo + electric violet accent. */
    SCENEDECK,

    /** Faithful to OBS Studio's dark UI: grey-blue panels, teal accent. */
    OBS,

    /** Near-black, high-contrast white/violet. */
    OBSIDIAN,

    /** Arctic blue-grey (canonical Nord palette). */
    NORD,

    /** Classic Dracula purple/pink. */
    DRACULA,

    /** Solarized base tones. */
    SOLARIZED_DARK,

    /** Broadcast red tally accents. */
    STREAM_RED,

    /** Rich purple production vibe. */
    STUDIO_PURPLE,

    /** Warm coral/amber; light-friendly. */
    SUNSET_CORAL,

    /** Fresh green-teal; light-friendly. */
    MINT_CONTROL,

    /** Accessibility-max contrast. */
    HIGH_CONTRAST,
}

/** Accessor for design-system theme values outside of `MaterialTheme`'s own surface. */
object SceneDeckTheme {
    /** Product-semantic colors (tally, preview, meter zones, warnings) for the current family. */
    val colors: SceneDeckColors
        @Composable get() = LocalSceneDeckColors.current
}

/**
 * SceneDeck theme wrapper: M3 Expressive theme + per-family color scheme +
 * [SceneDeckColors] semantics + Inter/JetBrains Mono typography.
 *
 * @param family one of the built-in [ThemeFamily] palettes.
 * @param darkTheme dark/light resolution of the family scheme.
 * @param dynamicColor "Material You": derive the scheme from the wallpaper on Android 12+,
 * overriding [family] (semantic colors fall back to the family-independent defaults).
 * @param motionLevel user motion preference, mapped via [motionSchemeFor].
 */
@Composable
fun SceneDeckTheme(
    family: ThemeFamily = ThemeFamily.SCENEDECK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    motionLevel: MotionLevel = MotionLevel.FULL,
    content: @Composable () -> Unit,
) {
    val baseScheme = colorSchemeFor(family, darkTheme)
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (dynamic) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        baseScheme
    }
    val semanticColors =
        if (dynamic) {
            if (darkTheme) SceneDeckColors.Dark else SceneDeckColors.Light
        } else {
            sceneDeckColorsFor(family, darkTheme)
        }
    CompositionLocalProvider(LocalSceneDeckColors provides semanticColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = SceneDeckShapes,
            typography = SceneDeckTypography,
            motionScheme = motionSchemeFor(motionLevel),
            content = content,
        )
    }
}
