package com.scenedeck.android.core.designsystem.theme.families

import androidx.compose.material3.ColorScheme
import com.scenedeck.android.core.designsystem.theme.SceneDeckColors
import com.scenedeck.android.core.designsystem.theme.ThemeFamily

/**
 * Human-readable name for pickers ([com.scenedeck.android.core.designsystem.theme.ThemeSwitcher]).
 */
val ThemeFamily.displayName: String
    get() =
        when (this) {
            ThemeFamily.SCENEDECK -> "SceneDeck"
            ThemeFamily.OBS -> "OBS"
            ThemeFamily.OBSIDIAN -> "Obsidian"
            ThemeFamily.NORD -> "Nord"
            ThemeFamily.DRACULA -> "Dracula"
            ThemeFamily.SOLARIZED_DARK -> "Solarized Dark"
            ThemeFamily.STREAM_RED -> "Stream Red"
            ThemeFamily.STUDIO_PURPLE -> "Studio Purple"
            ThemeFamily.SUNSET_CORAL -> "Sunset Coral"
            ThemeFamily.MINT_CONTROL -> "Mint Control"
            ThemeFamily.HIGH_CONTRAST -> "High Contrast"
        }

/** Resolves the [ColorScheme] for a family + dark/light mode. */
internal fun colorSchemeFor(family: ThemeFamily, darkTheme: Boolean): ColorScheme {
    val pair =
        when (family) {
            ThemeFamily.SCENEDECK -> SceneDeckFamily.light to SceneDeckFamily.dark
            ThemeFamily.OBS -> ObsFamily.light to ObsFamily.dark
            ThemeFamily.OBSIDIAN -> ObsidianFamily.light to ObsidianFamily.dark
            ThemeFamily.NORD -> NordFamily.light to NordFamily.dark
            ThemeFamily.DRACULA -> DraculaFamily.light to DraculaFamily.dark
            ThemeFamily.SOLARIZED_DARK -> SolarizedDarkFamily.light to SolarizedDarkFamily.dark
            ThemeFamily.STREAM_RED -> StreamRedFamily.light to StreamRedFamily.dark
            ThemeFamily.STUDIO_PURPLE -> StudioPurpleFamily.light to StudioPurpleFamily.dark
            ThemeFamily.SUNSET_CORAL -> SunsetCoralFamily.light to SunsetCoralFamily.dark
            ThemeFamily.MINT_CONTROL -> MintControlFamily.light to MintControlFamily.dark
            ThemeFamily.HIGH_CONTRAST -> HighContrastFamily.light to HighContrastFamily.dark
        }
    return if (darkTheme) pair.second else pair.first
}

/** Resolves the product-semantic [SceneDeckColors] for a family + dark/light mode. */
internal fun sceneDeckColorsFor(family: ThemeFamily, darkTheme: Boolean): SceneDeckColors =
    when (family) {
        ThemeFamily.HIGH_CONTRAST ->
            if (darkTheme) HighContrastFamily.darkSemantic else HighContrastFamily.lightSemantic

        else -> if (darkTheme) SceneDeckColors.Dark else SceneDeckColors.Light
    }
