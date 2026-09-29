package com.scenedeck.android.feature.mixer

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.model.ObsVersionInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shot of the mixer page with grouped strips (dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MixerScreenRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixerScreenDark() {
        val state = MixerUiState(
            connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
            mode = MixerMode.ACTIVE,
            grouping = MixerGrouping.SCOPE,
            activeScene = "Cam 1",
            displayedScene = "Cam 1",
            inputs = listOf(
                MixerInputState(
                    "Test Tone 440", MixerScope.SCENE, null, 0.7,
                    muted = false, locked = false, inputKind = "ffmpeg_source",
                ),
                MixerInputState("Desktop Audio", MixerScope.GLOBAL, null, 1.0, muted = false, locked = false),
                MixerInputState("Mic/Aux", MixerScope.GLOBAL, null, 0.8, muted = true, locked = false),
                MixerInputState(
                    "Nested Tone",
                    MixerScope.NESTED,
                    "Starting Soon › Nested Audio",
                    0.35,
                    muted = false,
                    locked = true,
                ),
            ),
        )
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    MixerContent(
                        uiState = state,
                        levelsStore = MeterLevelsStore(),
                        motionLevel = MotionLevel.OFF,
                        hapticsEnabled = false,
                        callbacks = MixerCallbacks(),
                        mediaStatus = mapOf(
                            "Test Tone 440" to MediaStatus(
                                state = MediaStateKind.PLAYING,
                                durationMs = 60_000,
                                cursorMs = 12_345,
                            ),
                        ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("mixer_screen_dark.png")
    }
}
