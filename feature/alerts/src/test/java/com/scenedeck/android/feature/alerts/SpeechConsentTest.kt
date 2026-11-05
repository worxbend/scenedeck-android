package com.scenedeck.android.feature.alerts

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.scenedeck.android.core.data.SpeechTarget
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechConsentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun explicitConsentIsRequiredAndAReplacedTargetCannotReuseTheDialog() {
        val state = mutableStateOf(alertsPreviewState())
        val armed = mutableListOf<SpeechTarget>()
        compose.setContent {
            SceneDeckTheme {
                Surface {
                    SpeechControls(
                        state.value,
                        {
                            armed += it
                            true
                        },
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Monitor microphone").performClick()
        assertEquals(emptyList<SpeechTarget>(), armed)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Monitor microphone").performClick()
        compose.runOnIdle { state.value = state.value.copy(speechTarget = null) }
        compose.onNodeWithText("Speech expected — arm").assertDoesNotExist()
        assertEquals(emptyList<SpeechTarget>(), armed)
        compose.runOnIdle { state.value = alertsPreviewState() }
        compose.onNodeWithText("Monitor microphone").performClick()
        compose.onNodeWithText("Speech expected — arm").performClick()
        assertEquals(listOf(alertsPreviewState().speechTarget), armed)
    }

    @Test
    fun rejectedArmingIsExplainedAndStopRemainsDirect() {
        val state = mutableStateOf(alertsPreviewState())
        var stops = 0
        compose.setContent {
            SceneDeckTheme { Surface { SpeechControls(state.value, { false }, { stops++ }, {}) } }
        }
        compose.onNodeWithText("Monitor microphone").performClick()
        compose.onNodeWithText("Speech expected — arm").performClick()
        compose
            .onNodeWithText(
                "The connection or microphone changed. Review the selection and try again."
            )
            .assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(armedSpeech = state.value.speechTarget) }
        compose.onNodeWithText("Stop monitoring").performClick()
        assertEquals(1, stops)
    }
}
