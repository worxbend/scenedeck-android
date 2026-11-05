package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.RecordingAction
import com.scenedeck.android.core.data.RecordingNotice
import com.scenedeck.android.core.data.RecordingOrigin
import com.scenedeck.android.core.data.RecordingResult
import com.scenedeck.android.core.data.RecordingToolsState
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RecordingToolsRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val ready =
        RecordingToolsState(
            origin = RecordingOrigin(1, ObsSessionToken(2, 0), 1),
            record = RecordStatus(true, false, "", 5_000, 0),
            capabilities = ObsCapabilities(true, true, true, true, true),
        )
    private var lastAction: Pair<RecordingAction, String>? = null
    private var closed = false

    private fun show(
        state: RecordingToolsState = ready,
        family: ThemeFamily = ThemeFamily.SCENEDECK,
        dark: Boolean = true,
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    RecordingToolsContent(
                        state,
                        { action, chapter -> lastAction = action to chapter },
                        { closed = true },
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean) {
        show(family = family, dark = dark)
        compose
            .onRoot()
            .captureRoboImage(
                "recording_tools_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun pausedShowsResumeAndDisablesAdvancedActions() {
        show(ready.copy(record = ready.record!!.copy(paused = true)))
        compose.onNodeWithText("Resume recording").performClick()
        assertEquals(RecordingAction.RESUME to "", lastAction)
        compose.onNodeWithText("Split recording file").assertIsNotEnabled()
        compose.onNodeWithText("Add OBS chapter").assertIsNotEnabled()
    }

    @Test
    fun chapterTextAndSplitAreIndependentDeliberateActions() {
        show()
        compose.onNodeWithText("Chapter name (optional)").performTextInput("Interview")
        compose.onNodeWithText("Add OBS chapter").performClick()
        assertEquals(RecordingAction.CHAPTER to "Interview", lastAction)
        compose.onNodeWithText("Split recording file").performClick()
        assertEquals(RecordingAction.SPLIT to "", lastAction)
    }

    @Test
    fun unsupportedAndUnknownStatusExplainDisabledControls() {
        show(ready.copy(record = null, capabilities = ObsCapabilities()))
        compose.onNodeWithText("Waiting for a fresh recording status").assertIsDisplayed()
        compose.onNodeWithText("Pause recording").assertIsNotEnabled()
        compose
            .onNodeWithText("File splitting is unavailable on this OBS connection.")
            .assertIsDisplayed()
        compose.onNodeWithText("Add OBS chapter").assertIsNotEnabled()
    }

    @Test
    fun pendingDisablesAllWrites() {
        show(ready.copy(pending = RecordingAction.SPLIT))
        compose.onNodeWithText("Pause recording").assertIsNotEnabled()
        compose.onNodeWithText("Split recording file").assertIsNotEnabled()
        compose.onNodeWithText("Add OBS chapter").assertIsNotEnabled()
        compose
            .onNodeWithText("Splitting file… Waiting for OBS.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun compactHeightKeepsUnknownOutcomeAndCloseReachable() {
        show(
            ready.copy(notice = RecordingNotice(RecordingAction.SPLIT, RecordingResult.UNKNOWN)),
            dark = false,
        )
        compose
            .onNodeWithText(
                "Outcome unknown. Check OBS before trying again; the action was not retried."
            )
            .performScrollTo()
            .assertIsDisplayed()
        compose.onRoot().captureRoboImage("recording_tools_compact_unknown.png")
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertEquals(true, closed)
    }
}
