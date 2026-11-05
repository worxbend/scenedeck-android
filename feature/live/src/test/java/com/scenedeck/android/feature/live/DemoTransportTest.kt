package com.scenedeck.android.feature.live

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.LocalSimulation
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoTransportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    private fun capture(family: ThemeFamily, dark: Boolean) {
        var confirmed = 0
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                CompositionLocalProvider(LocalSimulation provides true) {
                    TransportConfirmationDialog(
                        TransportConfirmation.START_STREAM,
                        { confirmed++ },
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("DEMO · Local simulation only").assertIsDisplayed()
        compose.onNodeWithText("Go live with the current settings?").assertDoesNotExist()
        compose
            .onNode(isDialog())
            .captureRoboImage(
                "demo_transport_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, confirmed)
    }
}
