package com.scenedeck.android

import com.scenedeck.android.core.data.DarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarAppearanceTest {

    @Test
    fun `light app theme uses dark icons even on a dark system`() {
        val state = SceneDeckAppState(SceneDeckAppState.Initial(darkMode = DarkMode.LIGHT))
        val appearance = systemBarAppearance(state.isDarkTheme(systemInDarkTheme = true))
        assertTrue(appearance.darkIcons)
        assertEquals(LIGHT_NAVIGATION_SCRIM, appearance.navigationBarScrim)
        assertEquals(0, appearance.statusBarScrim)
    }

    @Test
    fun `dark app theme uses light icons even on a light system`() {
        val state = SceneDeckAppState(SceneDeckAppState.Initial(darkMode = DarkMode.DARK))
        val appearance = systemBarAppearance(state.isDarkTheme(systemInDarkTheme = false))
        assertFalse(appearance.darkIcons)
        assertEquals(DARK_NAVIGATION_SCRIM, appearance.navigationBarScrim)
    }

    @Test
    fun `system mode follows the system setting`() {
        val state = SceneDeckAppState()
        assertTrue(systemBarAppearance(state.isDarkTheme(false)).darkIcons)
        assertFalse(systemBarAppearance(state.isDarkTheme(true)).darkIcons)
    }
}
