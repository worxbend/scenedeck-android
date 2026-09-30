package com.scenedeck.android.feature.live

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.TransitionInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots: studio-mode deck (red program + green preview + thumbnail) and picker. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StudioModeRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    private fun thumbnail(color: Int): Bitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun studioDeckDark() {
        val deck = previewDeckState()
        val thumbnails = mapOf("Cam 1" to thumbnail(0xFF336699.toInt()))

        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    LiveDeckContent(
                        deckState = deck,
                        telemetry = Telemetry(connection = deck.connectionState),
                        pendingScene = null,
                        hapticsEnabled = false,
                        motionLevel = MotionLevel.OFF,
                        thumbnails = thumbnails,
                        onSceneTap = {},
                        onStreamClick = {},
                        onRecordClick = {},
                        onToggleVirtualCam = {},
                        onQuickEditSave = { _, _, _, _ -> },
                        onReorder = {},
                        onStudioToggle = {},
                        onTransitionClick = {},
                        onCutClick = {},
                        onTransitionSelect = {},
                        onTransitionDurationChange = {},
                        previewsEnabled = true,
                        onPreviewsToggle = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("studio_deck_dark.png")
    }

    @Test
    fun transitionPickerDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    TransitionPickerSheet(
                        current =
                            CurrentTransition(
                                "Fade",
                                "fade_transition",
                                300,
                                configurable = true,
                                fixed = false,
                            ),
                        transitions =
                            listOf(
                                TransitionInfo(
                                    "Cut",
                                    "cut_transition",
                                    fixed = true,
                                    durationMs = null,
                                ),
                                TransitionInfo(
                                    "Fade",
                                    "fade_transition",
                                    fixed = false,
                                    durationMs = 300,
                                ),
                                TransitionInfo(
                                    "Swipe",
                                    "swipe_transition",
                                    fixed = false,
                                    durationMs = 1000,
                                ),
                                TransitionInfo(
                                    "Stinger",
                                    "stinger_transition",
                                    fixed = false,
                                    durationMs = 1500,
                                ),
                            ),
                        onSelect = {},
                        onDurationChange = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("transition_picker_dark.png")
    }

    private fun previewDeckState(): DeckState =
        DeckState(
            connectionState = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
            currentProgramScene = "Cam 1",
            studioMode = true,
            previewScene = "Screen",
            currentTransition =
                CurrentTransition(
                    "Fade",
                    "fade_transition",
                    300,
                    configurable = true,
                    fixed = false,
                ),
            transitions =
                listOf(
                    TransitionInfo("Cut", "cut_transition", fixed = true, durationMs = null),
                    TransitionInfo("Fade", "fade_transition", fixed = false, durationMs = 300),
                ),
            scenes = previewScenes(),
        )

    private fun previewScenes(): List<SceneCardState> =
        listOf(
            SceneCardState(
                "Cam 1",
                SceneRole.PRIMARY,
                null,
                "CAMERA",
                0,
                isActive = true,
            ),
            SceneCardState(
                "Screen",
                SceneRole.PRIMARY,
                0xFF42A5F5,
                "MONITOR",
                1,
                isActive = false,
                isPreview = true,
            ),
            SceneCardState(
                "Quiet B",
                SceneRole.PRIMARY,
                null,
                null,
                2,
                isActive = false,
            ),
            SceneCardState(
                "Scene",
                SceneRole.PRIMARY,
                0xFF26A69A,
                "CAMERA",
                3,
                isActive = false,
            ),
        )
}
