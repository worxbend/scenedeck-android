package com.scenedeck.android.feature.connections

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePasswordStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun passwordDoesNotSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SceneDeckTheme {
                ProfileEditForm(
                    profileId = null,
                    initialName = "Studio",
                    initialHost = "host",
                    initialPort = "4455",
                    initialPassword = "",
                    testState = TestConnectionState.Idle,
                    onTest = { _, _, _ -> },
                    onResetTest = {},
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Password (optional)").performTextInput("private-credential")
        composeRule.onNodeWithText("private-credential").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("private-credential").assertDoesNotExist()
        composeRule.onNodeWithText("Studio").assertExists()
    }
}
