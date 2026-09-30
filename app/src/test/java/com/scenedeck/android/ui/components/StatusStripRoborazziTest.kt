package com.scenedeck.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class StatusStripRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun standby() {
        render(false, false, "status_standby_dark.png")
        composeRule.onNodeWithText("OFF AIR").assertExists()
        composeRule.onNodeWithText("OBS").assertExists()
        composeRule.onNodeWithText("FPS").assertDoesNotExist()
        composeRule.onNodeWithText("DROP").assertDoesNotExist()
        composeRule.onNodeWithText("CPU").assertDoesNotExist()
    }

    @Test
    fun liveAndRecording() {
        render(true, true, "status_live_recording_dark.png")
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertExists()
        composeRule.onNodeWithContentDescription("REC, elapsed 00:04:18").assertExists()
        composeRule.onNodeWithText("FPS").assertExists()
    }

    @Test
    fun liveLight() {
        render(true, false, "status_live_light.png", dark = false)
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertExists()
    }

    @Test
    fun outputUnknownOnDisconnect() {
        val stale = Telemetry(stream = stream(true), record = record(true)).toStatusStripState()
        assertNull(stale.stream)
        assertNull(stale.record)
        composeRule.setContent { SceneDeckTheme { StatusStrip(stale, animateConnection = false) } }
        composeRule.onNodeWithText("Output status unknown").assertExists()
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertDoesNotExist()
    }

    @Test
    fun checkingBeforeFirstPoll() {
        composeRule.setContent {
            SceneDeckTheme {
                StatusStrip(StatusStripState(connection = ready), animateConnection = false)
            }
        }
        composeRule.onNodeWithText("Checking outputs…").assertExists()
        composeRule.onNodeWithText("OFF AIR").assertDoesNotExist()
    }

    @Test
    fun reconnectingStreamAndPausedRecording() {
        val state =
            StatusStripState(
                connection = ready,
                stream = stream(true).copy(reconnecting = true),
                record = record(true).copy(paused = true),
            )
        composeRule.setContent { SceneDeckTheme { StatusStrip(state, animateConnection = false) } }
        composeRule.onNodeWithContentDescription("RETRYING, elapsed 00:12:34").assertExists()
        composeRule.onNodeWithContentDescription("REC PAUSED, elapsed 00:04:18").assertExists()
    }

    @Test
    fun recordingWithoutStreamingHidesMetrics() {
        render(false, true, "status_recording_dark.png")
        composeRule.onNodeWithContentDescription("REC, elapsed 00:04:18").assertExists()
        composeRule.onNodeWithText("FPS").assertDoesNotExist()
        composeRule.onNodeWithText("CPU").assertDoesNotExist()
        composeRule.onNodeWithText("OFF AIR").assertExists()
    }

    @Test
    fun liveRemainsVisibleWhenRecordPollFails() {
        val state = StatusStripState(connection = ready, stream = stream(true))
        composeRule.setContent { SceneDeckTheme { StatusStrip(state, animateConnection = false) } }
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertExists()
        composeRule.onNodeWithText("FPS").assertExists()
    }

    @Test
    fun obsThemeLive() {
        render(true, false, "status_live_obs.png", family = ThemeFamily.OBS)
        composeRule.onNodeWithContentDescription("LIVE, elapsed 00:12:34").assertExists()
    }

    private fun render(
        live: Boolean,
        recording: Boolean,
        filename: String,
        dark: Boolean = true,
        family: ThemeFamily = ThemeFamily.SCENEDECK,
    ) {
        val state =
            StatusStripState(
                connection = ready,
                fps = 60.0,
                cpuPercent = 0.5,
                bitrateKbps = if (live) 6000 else 0,
                stream = stream(live),
                record = record(recording),
            )
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                StatusStrip(state, animateConnection = false)
            }
        }
        composeRule.onRoot().captureRoboImage(filename)
    }
}

private fun stream(active: Boolean) =
    StreamStatus(active, false, "00:12:34.000", 754000, 0, 0.0, 0, 0)

private fun record(active: Boolean) = RecordStatus(active, false, "00:04:18.000", 258000, 0)

private val ready = ConnectionState.Ready(ObsVersionInfo("31.0", "5.6", 1, "test"))
