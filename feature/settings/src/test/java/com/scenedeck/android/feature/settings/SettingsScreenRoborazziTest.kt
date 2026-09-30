package com.scenedeck.android.feature.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots of Settings across the key theme matrix variants. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    private fun capture(family: ThemeFamily, darkTheme: Boolean, name: String) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme) {
                Surface {
                    SettingsContent(
                        currentTheme = family,
                        onThemeSelect = {},
                        darkMode = DarkMode.SYSTEM,
                        onDarkModeChange = {},
                        motionLevel = MotionLevel.FULL,
                        onMotionLevelChange = {},
                        dynamicColor = false,
                        onDynamicColorChange = {},
                        haptics = true,
                        onHapticsChange = {},
                        keepScreenOn = false,
                        onKeepScreenOnChange = {},
                        outputSafety = OutputSafety(),
                        onConfirmStartStreamChange = {},
                        onConfirmStopStreamChange = {},
                        onConfirmStartRecordChange = {},
                        onConfirmStopRecordChange = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun settingsScreenSceneDeckDark() =
        capture(ThemeFamily.SCENEDECK, darkTheme = true, name = "settings_scenedeck_dark.png")

    @Test
    fun settingsScreenHighContrastDark() =
        capture(
            ThemeFamily.HIGH_CONTRAST,
            darkTheme = true,
            name = "settings_high_contrast_dark.png",
        )

    @Test
    fun settingsScreenSceneDeckLight() =
        capture(ThemeFamily.SCENEDECK, darkTheme = false, name = "settings_scenedeck_light.png")
}
