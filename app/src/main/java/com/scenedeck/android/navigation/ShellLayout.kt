package com.scenedeck.android.navigation

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType

/**
 * Resolved app-shell chrome for the current window.
 *
 * @property navigationType navigation-suite component hosting the top-level destinations.
 * @property showStatusStrip show the persistent status strip (hidden on full-screen routes).
 * @property compactStatusStrip keep the persistent status strip on a single 48dp row.
 */
internal data class ShellLayout(
    val navigationType: NavigationSuiteType,
    val showStatusStrip: Boolean,
    val compactStatusStrip: Boolean,
) {
    /** Navigation docks to the bottom edge and owns the bottom system-bar inset. */
    val bottomNavigation: Boolean
        get() = navigationType in BottomNavigationTypes

    /** Navigation docks to the start edge and owns the start system-bar/cutout inset. */
    val sideNavigation: Boolean
        get() = navigationType in SideNavigationTypes
}

/**
 * Picks the shell chrome. Full-screen routes hide navigation. A compact-height window (phone
 * landscape, < 480dp) uses a navigation rail instead of the library default bottom bar, so the
 * bottom bar plus the status strip do not consume most of the height; tabletop posture keeps the
 * default because its bottom half is the control surface.
 */
internal fun shellLayout(
    defaultType: NavigationSuiteType,
    fullScreen: Boolean,
    compactHeight: Boolean,
    tabletop: Boolean,
): ShellLayout {
    val navigationType =
        when {
            fullScreen -> NavigationSuiteType.None
            compactHeight && !tabletop -> NavigationSuiteType.NavigationRail
            else -> defaultType
        }
    return ShellLayout(
        navigationType = navigationType,
        showStatusStrip = !fullScreen,
        compactStatusStrip = compactHeight,
    )
}

private val BottomNavigationTypes =
    setOf(
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.ShortNavigationBarCompact,
        NavigationSuiteType.ShortNavigationBarMedium,
    )

private val SideNavigationTypes =
    setOf(
        NavigationSuiteType.NavigationRail,
        NavigationSuiteType.NavigationDrawer,
        NavigationSuiteType.WideNavigationRailCollapsed,
        NavigationSuiteType.WideNavigationRailExpanded,
    )
