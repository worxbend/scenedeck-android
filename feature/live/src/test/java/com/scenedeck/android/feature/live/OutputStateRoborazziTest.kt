package com.scenedeck.android.feature.live

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Output states rendered from fixtures; these tests never send OBS commands. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OutputStateRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun idle() {
        capture(false, false, MotionLevel.OFF, "broadcast_idle_dark.png")
        composeRule.onNodeWithContentDescription("Start Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Standby").assertIsDisplayed()
    }

    @Test
    fun streamingReducedMotion() {
        capture(true, false, MotionLevel.OFF, "broadcast_streaming_dark.png")
        composeRule.onNodeWithContentDescription("Stop Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertIsDisplayed()
    }

    @Test
    fun recording() {
        capture(false, true, MotionLevel.OFF, "broadcast_recording_dark.png")
        composeRule.onNodeWithContentDescription("Stop Record").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("REC, elapsed 00:04:18").assertIsDisplayed()
    }

    @Test
    fun streamingReducedMotionSnapshot() =
        capture(true, false, MotionLevel.REDUCED, "broadcast_streaming_reduced_motion.png")

    @Test
    fun studio() =
        capture(
            false,
            false,
            MotionLevel.OFF,
            "broadcast_studio_dark.png",
            appearance = OutputAppearance(studio = true),
        )

    @Test
    fun recordingLight() =
        capture(
            false,
            true,
            MotionLevel.OFF,
            "broadcast_recording_light.png",
            appearance = OutputAppearance(dark = false),
        )

    @Test
    fun simultaneousOutputs() {
        capture(true, true, MotionLevel.OFF, "broadcast_simultaneous_dark.png")
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("REC, elapsed 00:04:18").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Start virtual camera").assertIsDisplayed()
    }

    @Test
    fun switchedScene() =
        capture(
            true,
            false,
            MotionLevel.OFF,
            "broadcast_switched_scene_dark.png",
            appearance = OutputAppearance(program = "Screen"),
        )

    private fun capture(
        streaming: Boolean,
        recording: Boolean,
        motion: MotionLevel,
        name: String,
        appearance: OutputAppearance = OutputAppearance(),
    ) {
        val (studio, dark) = appearance
        val deck =
            deckFixture(appearance.program).let {
                if (studio)
                    it.copy(
                        studioMode = true,
                        previewScene = "Screen",
                        currentTransition =
                            com.scenedeck.android.core.model.CurrentTransition(
                                "Fade",
                                "fade_transition",
                                300,
                                configurable = true,
                                fixed = false,
                            ),
                        scenes =
                            it.scenes.map { scene ->
                                scene.copy(isPreview = scene.name == "Screen")
                            },
                    )
                else it
            }
        val telemetry =
            Telemetry(
                connection = deck.connectionState,
                stream =
                    StreamStatus(
                        streaming,
                        false,
                        "00:12:34.000",
                        754_000,
                        123_456,
                        0.0,
                        0,
                        45_000,
                    ),
                record = RecordStatus(recording, false, "00:04:18.000", 258_000, 1_234_000),
            )
        composeRule.setContent {
            SceneDeckTheme(darkTheme = dark, motionLevel = motion) {
                Surface {
                    OutputDeckFixture(deck, telemetry, motion)
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    private fun deckFixture(program: String) =
        DeckState(
            connectionState =
                ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "fixture")),
            currentProgramScene = program,
            scenes =
                listOf(
                    SceneCardState(
                        "Camera",
                        SceneRole.PRIMARY,
                        null,
                        "CAMERA",
                        0,
                        isActive = program == "Camera",
                    ),
                    SceneCardState(
                        "Screen",
                        SceneRole.PRIMARY,
                        null,
                        "MONITOR",
                        1,
                        isActive = program == "Screen",
                    ),
                    SceneCardState(
                        "Starting soon",
                        SceneRole.PRIMARY,
                        null,
                        "STAR",
                        2,
                        isActive = false,
                    ),
                    SceneCardState("Break", SceneRole.PRIMARY, null, null, 3, isActive = false),
                ),
        )
}

private data class OutputAppearance(
    val studio: Boolean = false,
    val dark: Boolean = true,
    val program: String = "Camera",
)
