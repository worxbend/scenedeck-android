package com.scenedeck.android.feature.live

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shot of the Live deck with a streaming session in dark mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LiveDeckRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveDeckDark() {
        val deck = DeckState(
            connectionState = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
            currentProgramScene = "Scene",
            scenes = listOf(
                SceneCardState("Scene", SceneRole.PRIMARY, null, null, 0, isActive = true),
                SceneCardState("Cam 1", SceneRole.PRIMARY, 0xFF7E57C2, "CAMERA", 1, isActive = false),
                SceneCardState("Screen", SceneRole.PRIMARY, 0xFF42A5F5, "MONITOR", 2, isActive = false),
                SceneCardState("Starting Soon", SceneRole.PRIMARY, null, "STAR", 3, isActive = false),
            ),
        )
        val telemetry = Telemetry(
            connection = deck.connectionState,
            stream = StreamStatus(
                active = true,
                reconnecting = false,
                timecode = "00:12:34.000",
                durationMs = 754_000,
                bytes = 123_456_789,
                congestion = 0.05,
                skippedFrames = 1,
                totalFrames = 45_000,
            ),
        )

        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    LiveDeckContent(
                        deckState = deck,
                        telemetry = telemetry,
                        pendingScene = "Screen",
                        hapticsEnabled = false,
                        onSceneTap = {},
                        onStreamClick = {},
                        onRecordClick = {},
                        onQuickEditSave = { _, _, _, _ -> },
                        onReorder = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("live_deck_dark.png")
    }
}
