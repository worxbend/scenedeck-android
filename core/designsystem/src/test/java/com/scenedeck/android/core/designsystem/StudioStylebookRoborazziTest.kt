package com.scenedeck.android.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.gallery.StudioStylebook
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StudioStylebookRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean, filename: String) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) { StudioStylebook() }
        }
        composeRule.onRoot().captureRoboImage(filename)
    }

    @Test fun dark() = capture(ThemeFamily.SCENEDECK, true, "studio_stylebook_dark.png")

    @Test fun light() = capture(ThemeFamily.SCENEDECK, false, "studio_stylebook_light.png")

    @Test fun obs() = capture(ThemeFamily.OBS, true, "studio_stylebook_obs.png")
}
