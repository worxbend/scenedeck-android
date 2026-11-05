package com.scenedeck.android.feature.live

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreflightConfirmationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun preflightDoesNotConfirmThePendingOutput() {
        var confirms = 0
        var checks = 0
        compose.setContent {
            SceneDeckTheme {
                TransportConfirmationDialog(
                    TransportConfirmation.START_STREAM,
                    { confirms++ },
                    {},
                    { checks++ },
                )
            }
        }
        compose.onNodeWithText("Open Preflight").performClick()
        assertEquals(0, confirms)
        assertEquals(1, checks)
    }

    @Test
    fun stopConfirmationHasNoPreflightDetour() {
        compose.setContent {
            SceneDeckTheme {
                TransportConfirmationDialog(TransportConfirmation.STOP_STREAM, {}, {}, {})
            }
        }
        compose.onNodeWithText("Open Preflight").assertDoesNotExist()
    }
}
