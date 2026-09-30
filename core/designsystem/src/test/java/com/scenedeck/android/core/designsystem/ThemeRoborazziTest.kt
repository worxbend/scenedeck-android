package com.scenedeck.android.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.gallery.DesignSystemGallery
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.ThemeSwitcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden shots: one [ThemeSwitcher] per [ThemeFamily] in dark mode.
 *
 * Record: `./gradlew :core:designsystem:recordRoborazziDebug` Verify: `./gradlew
 * :core:designsystem:verifyRoborazziDebug` (plain `testDebugUnitTest` verifies by default — see the
 * module's build.gradle.kts).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ThemeSwitcherRoborazziTest(private val family: ThemeFamily) {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun themeSwitcherDark() {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = true) {
                Surface {
                    ThemeSwitcher(
                        current = family,
                        onSelect = {},
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        composeRule
            .onRoot()
            .captureRoboImage(filePath = "theme_switcher_${family.name.lowercase()}_dark.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun families(): List<ThemeFamily> = ThemeFamily.entries
    }
}

/** Golden shot: [DesignSystemGallery] for the default family in dark mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GalleryRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun gallerySceneDeckDark() {
        composeRule.setContent {
            DesignSystemGallery(
                family = ThemeFamily.SCENEDECK,
                darkTheme = true,
                modifier = Modifier.testTag("gallery"),
            )
        }
        composeRule
            .onNodeWithTag("gallery")
            .captureRoboImage(filePath = "gallery_scenedeck_dark.png")
    }
}
