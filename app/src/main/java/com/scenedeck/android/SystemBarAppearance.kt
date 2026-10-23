package com.scenedeck.android

import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * System-bar appearance for the resolved in-app theme. `enableEdgeToEdge()` defaults follow the
 * system night mode, which leaves white status icons on a light surface when the app forces Light
 * on a dark system (and the reverse), so the style is derived from the app theme instead.
 *
 * @property darkIcons draw dark system-bar icons (light app theme).
 * @property statusBarScrim ARGB scrim behind the status bar.
 * @property navigationBarScrim ARGB scrim behind 3-button navigation before API 29 (the system
 *   contrast scrim is used from API 29).
 */
internal data class SystemBarAppearance(
    val darkIcons: Boolean,
    val statusBarScrim: Int,
    val navigationBarScrim: Int,
)

/** Same transparent status bar and button-navigation scrims as the AndroidX defaults. */
internal fun systemBarAppearance(darkTheme: Boolean): SystemBarAppearance =
    SystemBarAppearance(
        darkIcons = !darkTheme,
        statusBarScrim = TRANSPARENT,
        navigationBarScrim = if (darkTheme) DARK_NAVIGATION_SCRIM else LIGHT_NAVIGATION_SCRIM,
    )

/**
 * Re-applies edge-to-edge with bar icon colors that match [darkTheme], not the system night mode.
 *
 * Keeps the AndroidX `auto` styles and overrides only their dark-mode detection: the bars stay
 * transparent, gesture navigation gets no band, and 3-button navigation keeps the system contrast
 * scrim (API 29+). `SystemBarStyle.light/dark` would instead paint a fixed scrim and switch the
 * system contrast enforcement off.
 */
internal fun ComponentActivity.applySystemBarAppearance(darkTheme: Boolean) {
    val appearance = systemBarAppearance(darkTheme)
    val isDark = { _: Resources -> !appearance.darkIcons }
    enableEdgeToEdge(
        statusBarStyle =
            SystemBarStyle.auto(appearance.statusBarScrim, appearance.statusBarScrim, isDark),
        navigationBarStyle =
            SystemBarStyle.auto(LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM, isDark),
    )
}

private const val TRANSPARENT = 0x00000000

// Mirrors androidx.activity's private DefaultLightScrim / DefaultDarkScrim (used before API 29).
internal const val LIGHT_NAVIGATION_SCRIM = 0xE6FFFFFF.toInt()
internal const val DARK_NAVIGATION_SCRIM = 0x801B1B1B.toInt()
