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
            ThemeFamily.OCEAN -> "Ocean"
            ThemeFamily.ROSEWOOD -> "Rosewood"
            ThemeFamily.AMBER -> "Amber"
            ThemeFamily.PAPER -> "Paper"
            ThemeFamily.HIGH_CONTRAST -> "High Contrast"
        }

/** Resolves the [ColorScheme] for a family + dark/light mode. */
internal fun colorSchemeFor(family: ThemeFamily, darkTheme: Boolean): ColorScheme {
    val pair = schemes.getValue(family)
    return if (darkTheme) pair.second else pair.first
}

private val schemes =
    mapOf(
        ThemeFamily.SCENEDECK to (SceneDeckFamily.light to SceneDeckFamily.dark),
        ThemeFamily.OBS to (ObsFamily.light to ObsFamily.dark),
        ThemeFamily.OBSIDIAN to (ObsidianFamily.light to ObsidianFamily.dark),
        ThemeFamily.NORD to (NordFamily.light to NordFamily.dark),
        ThemeFamily.DRACULA to (DraculaFamily.light to DraculaFamily.dark),
        ThemeFamily.SOLARIZED_DARK to (SolarizedDarkFamily.light to SolarizedDarkFamily.dark),
        ThemeFamily.STREAM_RED to (StreamRedFamily.light to StreamRedFamily.dark),
        ThemeFamily.STUDIO_PURPLE to (StudioPurpleFamily.light to StudioPurpleFamily.dark),
        ThemeFamily.SUNSET_CORAL to (SunsetCoralFamily.light to SunsetCoralFamily.dark),
        ThemeFamily.MINT_CONTROL to (MintControlFamily.light to MintControlFamily.dark),
        ThemeFamily.OCEAN to (OceanFamily.light to OceanFamily.dark),
        ThemeFamily.ROSEWOOD to (RosewoodFamily.light to RosewoodFamily.dark),
        ThemeFamily.AMBER to (AmberFamily.light to AmberFamily.dark),
        ThemeFamily.PAPER to (PaperFamily.light to PaperFamily.dark),
        ThemeFamily.HIGH_CONTRAST to (HighContrastFamily.light to HighContrastFamily.dark),
    )

/** Resolves the product-semantic [SceneDeckColors] for a family + dark/light mode. */
internal fun sceneDeckColorsFor(family: ThemeFamily, darkTheme: Boolean): SceneDeckColors =
    when (family) {
        ThemeFamily.HIGH_CONTRAST ->
            if (darkTheme) HighContrastFamily.darkSemantic else HighContrastFamily.lightSemantic

        else -> if (darkTheme) SceneDeckColors.Dark else SceneDeckColors.Light
    }
