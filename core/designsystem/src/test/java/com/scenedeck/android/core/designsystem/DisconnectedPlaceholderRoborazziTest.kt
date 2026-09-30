package com.scenedeck.android.core.designsystem

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
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
class DisconnectedPlaceholderRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(dark: Boolean, state: ConnectionState, name: String) {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = dark) {
                Surface { DisconnectedPlaceholder(state, {}) }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test fun offlineDark() = capture(true, ConnectionState.Disconnected, "offline_dark.png")

    @Test fun offlineLight() = capture(false, ConnectionState.Disconnected, "offline_light.png")

    @Test
    fun authFailure() =
        capture(true, ConnectionState.Failed(ConnectionError.Auth()), "offline_auth_dark.png")

    @Test
    fun reconnectingAllowsOpeningSettings() {
        var opened = false
        composeRule.setContent {
            SceneDeckTheme {
                DisconnectedPlaceholder(ConnectionState.Reconnecting(2), { opened = true })
            }
        }
        composeRule.onNodeWithText("Open connections").performClick()
        assertTrue(opened)
    }
}
