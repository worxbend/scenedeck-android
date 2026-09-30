package com.scenedeck.android.feature.mixer

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** Golden shots of the mixer page: SCENEDECK dark + the theme matrix variants. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MixerScreenRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    private fun capture(family: ThemeFamily, darkTheme: Boolean, name: String) {
        val state = previewMixerUiState()
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme) {
                Surface {
                    MixerContent(
                        uiState = state,
                        levelsStore = MeterLevelsStore(),
                        motionLevel = MotionLevel.OFF,
                        hapticsEnabled = false,
                        callbacks = MixerCallbacks(),
                        mediaStatus =
                            mapOf(
                                "Test Tone 440" to
                                    MediaStatus(
                                        state = MediaStateKind.PLAYING,
                                        durationMs = 60_000,
                                        cursorMs = 12_345,
                                    )
                            ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun mixerScreenDark() =
        capture(ThemeFamily.SCENEDECK, darkTheme = true, name = "mixer_screen_dark.png")

    @Test
    fun mixerScreenObsDark() =
        capture(ThemeFamily.OBS, darkTheme = true, name = "mixer_screen_obs_dark.png")

    @Test
    fun mixerScreenNordDark() =
        capture(ThemeFamily.NORD, darkTheme = true, name = "mixer_screen_nord_dark.png")

    @Test
    fun mixerScreenHighContrastDark() =
        capture(
            ThemeFamily.HIGH_CONTRAST,
            darkTheme = true,
            name = "mixer_screen_high_contrast_dark.png",
        )

    @Test
    fun mixerScreenSceneDeckLight() =
        capture(ThemeFamily.SCENEDECK, darkTheme = false, name = "mixer_screen_scenedeck_light.png")

    /** NONE is fully flat: no headers and the scope badge shows no source path. */
    @Test
    fun noGroupingHidesScopePaths() {
        groupingFixture(MixerGrouping.NONE)
        composeRule.onNodeWithText("Nested · Starting Soon › Nested Audio").assertDoesNotExist()
        composeRule.onNodeWithText("Nested").assertIsDisplayed()
    }

    /** SCOPE keeps the source path on the strip badge. */
    @Test
    fun scopeGroupingShowsScopePaths() {
        groupingFixture(MixerGrouping.SCOPE)
        composeRule.onNodeWithText("Nested · Starting Soon › Nested Audio").assertIsDisplayed()
    }

    /** Single strip so the badge is on screen without scrolling the LazyRow. */
    private fun groupingFixture(grouping: MixerGrouping) {
        val state = previewMixerUiState()
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    MixerContent(
                        uiState =
                            state.copy(grouping = grouping, inputs = listOf(state.inputs.last())),
                        levelsStore = MeterLevelsStore(),
                        motionLevel = MotionLevel.OFF,
                        hapticsEnabled = false,
                        callbacks = MixerCallbacks(),
                    )
                }
            }
        }
    }

    private fun previewMixerUiState(): MixerUiState =
        MixerUiState(
            connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
            mode = MixerMode.ACTIVE,
            grouping = MixerGrouping.SCOPE,
            activeScene = "Cam 1",
            displayedScene = "Cam 1",
            inputs =
                listOf(
                    MixerInputState(
                        "Test Tone 440",
                        MixerScope.SCENE,
                        null,
                        0.7,
                        muted = false,
                        locked = false,
                        inputKind = "ffmpeg_source",
                    ),
                    MixerInputState(
                        "Desktop Audio",
                        MixerScope.GLOBAL,
                        null,
                        1.0,
                        muted = false,
                        locked = false,
                    ),
                    MixerInputState(
                        "Mic/Aux",
                        MixerScope.GLOBAL,
                        null,
                        0.8,
                        muted = true,
                        locked = false,
                    ),
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
}
