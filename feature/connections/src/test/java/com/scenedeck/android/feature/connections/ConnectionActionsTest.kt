package com.scenedeck.android.feature.connections

import androidx.compose.material3.Surface
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
class ConnectionActionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun manualEntryWorksAndCameraActionMatchesDistribution() {
        var additions = 0
        var scans = 0
        composeRule.setContent {
            SceneDeckTheme {
                Surface { ConnectionActions(onAdd = { additions++ }, onScan = { scans++ }) }
            }
        }
        composeRule.onNodeWithText("Add manually").performClick()
        assertEquals(1, additions)
        if (QR_PAIRING_AVAILABLE) {
            composeRule.onNodeWithText("Scan QR").assertExists().performClick()
            assertEquals(1, scans)
        } else {
            composeRule.onNodeWithText("Scan QR").assertDoesNotExist()
            assertEquals(0, scans)
        }
    }
}
