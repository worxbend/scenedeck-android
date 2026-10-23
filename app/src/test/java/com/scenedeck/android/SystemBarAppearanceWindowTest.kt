package com.scenedeck.android

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The applied window state, not just the theme mapping (guards the nav-bar scrim regression). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemBarAppearanceWindowTest {

    @Test
    fun lightAppThemeOnDarkSystemDrawsDarkIconsAndKeepsSystemContrastScrim() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.applySystemBarAppearance(darkTheme = false)

        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
        @Suppress("DEPRECATION") val navigationBarColor = activity.window.navigationBarColor
        assertEquals(Color.TRANSPARENT, navigationBarColor)
        assertTrue(activity.window.isNavigationBarContrastEnforced)
    }

    @Test
    fun darkAppThemeDrawsLightIcons() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.applySystemBarAppearance(darkTheme = true)

        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)
        assertTrue(activity.window.isNavigationBarContrastEnforced)
    }
}
