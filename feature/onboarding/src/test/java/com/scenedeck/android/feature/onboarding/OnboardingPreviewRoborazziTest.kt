package com.scenedeck.android.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
class OnboardingPreviewRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean, name: String) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WelcomeStep(onSetup = {}, onSkip = {})
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true, "onboarding_scenedeck_dark.png")

    @Test
    fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false, "onboarding_scenedeck_light.png")

    @Test fun obsDark() = capture(ThemeFamily.OBS, true, "onboarding_obs_dark.png")
}
