package com.scenedeck.android.navigation

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLayoutTest {

    @Test
    fun `portrait phone keeps the default bottom bar and full strip`() {
        val layout = shellLayout(NavigationSuiteType.NavigationBar, false, false, false)
        assertEquals(NavigationSuiteType.NavigationBar, layout.navigationType)
        assertTrue(layout.bottomNavigation)
        assertFalse(layout.sideNavigation)
        assertFalse(layout.compactStatusStrip)
        assertTrue(layout.showStatusStrip)
    }

    @Test
    fun `compact height landscape switches to a rail with a compact strip`() {
        val layout = shellLayout(NavigationSuiteType.NavigationBar, false, true, false)
        assertEquals(NavigationSuiteType.NavigationRail, layout.navigationType)
        assertTrue(layout.sideNavigation)
        assertFalse(layout.bottomNavigation)
        assertTrue(layout.compactStatusStrip)
    }

    @Test
    fun `tabletop posture keeps the default navigation`() {
        val layout = shellLayout(NavigationSuiteType.NavigationBar, false, true, true)
        assertEquals(NavigationSuiteType.NavigationBar, layout.navigationType)
        assertTrue(layout.compactStatusStrip)
    }

    @Test
    fun `tablet keeps the default rail`() {
        val layout = shellLayout(NavigationSuiteType.NavigationRail, false, false, false)
        assertEquals(NavigationSuiteType.NavigationRail, layout.navigationType)
        assertFalse(layout.compactStatusStrip)
    }

    @Test
    fun `full screen routes hide navigation`() {
        val layout = shellLayout(NavigationSuiteType.NavigationBar, true, true, false)
        assertEquals(NavigationSuiteType.None, layout.navigationType)
        assertFalse(layout.sideNavigation)
        assertFalse(layout.bottomNavigation)
        assertFalse(layout.showStatusStrip)
    }
}
