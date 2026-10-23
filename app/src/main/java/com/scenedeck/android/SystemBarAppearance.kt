package com.scenedeck.android

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
 * @property navigationBarScrim ARGB scrim behind 3-button navigation.
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

/** Re-applies edge-to-edge with bar styles that match [darkTheme], not the system night mode. */
internal fun ComponentActivity.applySystemBarAppearance(darkTheme: Boolean) {
    val appearance = systemBarAppearance(darkTheme)
    val statusBarStyle =
        if (appearance.darkIcons) {
            SystemBarStyle.light(appearance.statusBarScrim, appearance.statusBarScrim)
        } else {
            SystemBarStyle.dark(appearance.statusBarScrim)
        }
    val navigationBarStyle =
        if (appearance.darkIcons) {
            SystemBarStyle.light(appearance.navigationBarScrim, DARK_NAVIGATION_SCRIM)
        } else {
            SystemBarStyle.dark(appearance.navigationBarScrim)
        }
    enableEdgeToEdge(statusBarStyle = statusBarStyle, navigationBarStyle = navigationBarStyle)
}

private const val TRANSPARENT = 0x00000000

// Mirrors androidx.activity's private DefaultLightScrim / DefaultDarkScrim.
internal const val LIGHT_NAVIGATION_SCRIM = 0xE6FFFFFF.toInt()
internal const val DARK_NAVIGATION_SCRIM = 0x801B1B1B.toInt()
