package com.scenedeck.android.feature.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.MeterRefresh
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.UiDensity
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

    private fun capture(
        family: ThemeFamily,
        darkTheme: Boolean,
        name: String,
        showPrivacy: Boolean = false,
    ) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme) {
                SettingsFixture(family = family)
            }
        }
        if (showPrivacy) {
            composeRule.onNodeWithText("Privacy policy").performScrollTo().assertHasClickAction()
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

    @Test
    fun privacySceneDeckDark() =
        capture(ThemeFamily.SCENEDECK, true, "settings_privacy_scenedeck_dark.png", true)

    @Test
    fun privacySceneDeckLight() =
        capture(ThemeFamily.SCENEDECK, false, "settings_privacy_scenedeck_light.png", true)

    @Test
    fun privacyObsDark() = capture(ThemeFamily.OBS, true, "settings_privacy_obs_dark.png", true)

    @Test
    fun mixerSectionCompactDark() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true, density = UiDensity.COMPACT) {
                SettingsFixture(
                    LayoutAndAudioSettings(
                        density = UiDensity.COMPACT,
                        meterRefresh = MeterRefresh.SAVER,
                        audioAllowList = setOf("Mic/Aux", "Desktop Audio"),
                    )
                )
            }
        }
        composeRule.onNodeWithText("Audio allow-list").performScrollTo().assertHasClickAction()
        composeRule.onRoot().captureRoboImage("settings_mixer_compact_dark.png")
    }

    @Test
    fun audioAllowListSheetDark() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                Surface {
                    AudioAllowListContent(
                        allowList = setOf("Mic/Aux"),
                        choices =
                            AudioInputChoices(names = listOf("Desktop Audio", "Mic/Aux", "Music")),
                        actions = LayoutAndAudioActions(),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("settings_audio_allow_list_dark.png")
    }
}
