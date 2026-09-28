package com.scenedeck.android

import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.data.DarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDeckAppStateTest {

    @Test
    fun `default values`() {
        val state = SceneDeckAppState()

        assertEquals(ThemeFamily.SCENEDECK, state.themeFamily)
        assertEquals(DarkMode.SYSTEM, state.darkMode)
        assertFalse(state.dynamicColor)
        assertEquals(MotionLevel.FULL, state.motionLevel)
    }

    @Test
    fun `state transitions`() {
        val state = SceneDeckAppState()

        state.darkMode = DarkMode.DARK
        state.dynamicColor = true
        state.motionLevel = MotionLevel.REDUCED

        assertEquals(DarkMode.DARK, state.darkMode)
        assertTrue(state.dynamicColor)
        assertEquals(MotionLevel.REDUCED, state.motionLevel)
    }

    @Test
    fun `dark mode resolution follows system only in SYSTEM mode`() {
        val state = SceneDeckAppState()

        state.darkMode = DarkMode.SYSTEM
        assertTrue(state.isDarkTheme(systemInDarkTheme = true))
        assertFalse(state.isDarkTheme(systemInDarkTheme = false))

        state.darkMode = DarkMode.DARK
        assertTrue(state.isDarkTheme(systemInDarkTheme = false))

        state.darkMode = DarkMode.LIGHT
        assertFalse(state.isDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun `saveable map round trip preserves values`() {
        val state = SceneDeckAppState()
        state.darkMode = DarkMode.LIGHT
        state.dynamicColor = true
        state.motionLevel = MotionLevel.OFF

        val restored = SceneDeckAppState.fromSaveableMap(state.toSaveableMap())

        assertEquals(ThemeFamily.SCENEDECK, restored.themeFamily)
        assertEquals(DarkMode.LIGHT, restored.darkMode)
        assertTrue(restored.dynamicColor)
        assertEquals(MotionLevel.OFF, restored.motionLevel)
    }

    @Test
    fun `restore falls back to defaults for unknown values`() {
        val restored = SceneDeckAppState.fromSaveableMap(
            mapOf(
                "themeFamily" to "NO_SUCH_FAMILY",
                "darkMode" to "NOPE",
                "dynamicColor" to true,
                "motionLevel" to "HYPERDRIVE",
            ),
        )

        assertEquals(ThemeFamily.SCENEDECK, restored.themeFamily)
        assertEquals(DarkMode.SYSTEM, restored.darkMode)
        assertTrue(restored.dynamicColor)
        assertEquals(MotionLevel.FULL, restored.motionLevel)
    }
}
