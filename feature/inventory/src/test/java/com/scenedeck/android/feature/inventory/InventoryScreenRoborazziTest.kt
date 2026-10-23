package com.scenedeck.android.feature.inventory

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.UiDensity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots of Inventory: roles, implicit Primary, long stale names, both densities. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class InventoryScreenRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun inventoryScreenDark() =
        capture(ThemeFamily.SCENEDECK, darkTheme = true, name = "inventory_screen_dark.png")

    @Test
    fun inventoryScreenObsDark() =
        capture(ThemeFamily.OBS, darkTheme = true, name = "inventory_screen_obs_dark.png")

    @Test
    fun inventoryScreenNordDark() =
        capture(ThemeFamily.NORD, darkTheme = true, name = "inventory_screen_nord_dark.png")

    @Test
    fun inventoryScreenHighContrastDark() =
        capture(
            ThemeFamily.HIGH_CONTRAST,
            darkTheme = true,
            name = "inventory_screen_high_contrast_dark.png",
        )

    @Test
    fun inventoryScreenSceneDeckLight() =
        capture(
            ThemeFamily.SCENEDECK,
            darkTheme = false,
            name = "inventory_screen_scenedeck_light.png",
        )

    @Test
    fun inventoryScreenCompactDark() =
        capture(
            ThemeFamily.SCENEDECK,
            darkTheme = true,
            name = "inventory_screen_compact_dark.png",
            density = UiDensity.COMPACT,
        )

    private fun capture(
        family: ThemeFamily,
        darkTheme: Boolean,
        name: String,
        density: UiDensity = UiDensity.COMFORTABLE,
    ) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme, density = density) {
                Surface { InventoryFixture() }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }
}
