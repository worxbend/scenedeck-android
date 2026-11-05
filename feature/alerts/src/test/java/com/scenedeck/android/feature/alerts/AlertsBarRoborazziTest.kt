package com.scenedeck.android.feature.alerts

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AlertsBarRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean) {
        var opened = false
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface { LiveAlertsBar(alertsPreviewState(), { opened = true }) }
            }
        }
        compose.onNodeWithText("2 active warnings").assertIsDisplayed()
        compose
            .onRoot()
            .captureRoboImage(
                "alerts_bar_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
        compose.onNodeWithText("Details").performClick()
        assertTrue(opened)
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)
}
