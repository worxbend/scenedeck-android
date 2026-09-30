package com.scenedeck.android.feature.live

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots of the Live deck: SCENEDECK dark + the theme matrix variants. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LiveDeckRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    @Suppress("LongMethod") // rich fixture setup
    private fun capture(
        family: ThemeFamily,
        darkTheme: Boolean,
        name: String,
        includeSecondary: Boolean = false,
        motion: MotionLevel = MotionLevel.REDUCED,
    ) {
        val deck =
            DeckState(
                connectionState =
                    ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
                currentProgramScene = "Scene",
                scenes =
                    listOf(
                        SceneCardState("Scene", SceneRole.PRIMARY, null, null, 0, isActive = true),
                        SceneCardState(
                            "Cam 1",
                            SceneRole.PRIMARY,
                            0xFF7E57C2,
                            "CAMERA",
                            1,
                            isActive = false,
                        ),
                        SceneCardState(
                            "Screen",
                            SceneRole.PRIMARY,
                            0xFF42A5F5,
                            "MONITOR",
                            2,
                            isActive = false,
                        ),
                        SceneCardState(
                            "Starting Soon",
                            SceneRole.PRIMARY,
                            null,
                            "STAR",
                            3,
                            isActive = false,
                        ),
                    ),
            )
        val displayedDeck =
            if (includeSecondary)
                deck.copy(
                    allScenes =
                        listOf(
                            SceneCardState(
                                "Utility scene",
                                SceneRole.SECONDARY,
                                null,
                                null,
                                0,
                                false,
                            )
                        ) + deck.scenes
                )
            else deck
        val telemetry =
            Telemetry(
                connection = deck.connectionState,
                stream =
                    StreamStatus(
                        active = true,
                        reconnecting = false,
                        timecode = "00:12:34.000",
                        durationMs = 754_000,
                        bytes = 123_456_789,
                        congestion = 0.05,
                        skippedFrames = 1,
                        totalFrames = 45_000,
                    ),
                record =
                    RecordStatus(
                        active = false,
                        paused = false,
                        timecode = "00:04:18.000",
                        durationMs = 258_000,
                        bytes = 1_234_000,
                    ),
                virtualCamActive = true,
            )

        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme) {
                Surface {
                    LiveDeckContent(
                        deckState = displayedDeck,
                        telemetry = telemetry,
                        pendingScene = "Screen",
                        hapticsEnabled = false,
                        motionLevel = motion,
                        thumbnails = emptyMap(),
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
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun allScenesIncludesSecondaryAndDeckFilterPreservesCuration() {
        capture(ThemeFamily.SCENEDECK, true, "all_scenes_dark.png", includeSecondary = true)
        composeRule.onNodeWithText("Utility scene").assertIsDisplayed()
        composeRule.onNodeWithText("Deck").performClick()
        composeRule.onNodeWithText("Utility scene").assertDoesNotExist()
        composeRule.onNodeWithText("All scenes · 5").performClick()
        composeRule.onNodeWithText("Utility scene").assertIsDisplayed()
    }

    @Test
    fun searchFiltersSceneNamesAndClosingRestoresCollection() {
        capture(ThemeFamily.SCENEDECK, true, "all_scenes_dark.png", includeSecondary = true)
        composeRule.onNodeWithText("Find scene").performClick()
        composeRule.onNodeWithText("Search scenes").performTextInput("utility")
        composeRule.onNodeWithText("Utility scene").assertIsDisplayed()
        composeRule.onNodeWithText("Cam 1").assertDoesNotExist()
        composeRule.onNodeWithText("Close search").performClick()
        composeRule.onNodeWithText("Cam 1").assertIsDisplayed()
    }

    @Test
    fun liveDeckDark() =
        capture(ThemeFamily.SCENEDECK, darkTheme = true, name = "live_deck_dark.png")

    @Test
    fun reorderEnabledInAllScenes() {
        filterFixture()
        composeRule.onNodeWithContentDescription("Scene options").performClick()
        composeRule.onNodeWithText("Reorder scenes").assertIsEnabled()
    }

    @Test
    fun reorderDisabledWhileDeckFilterActive() {
        filterFixture()
        composeRule.onNodeWithText("Deck").performClick()
        composeRule.onNodeWithContentDescription("Scene options").performClick()
        composeRule.onNodeWithText("Reorder scenes").assertIsNotEnabled()
    }

    private fun filterFixture() {
        val deck =
            DeckState(
                connectionState =
                    ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
                currentProgramScene = "Scene",
                scenes =
                    listOf(
                        SceneCardState("Scene", SceneRole.PRIMARY, null, null, 0, isActive = true)
                    ),
            )
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    OutputDeckFixture(
                        deck = deck,
                        telemetry = Telemetry(connection = deck.connectionState),
                        motion = MotionLevel.OFF,
                    )
                }
            }
        }
    }

    @Test
    fun liveDeckObsDark() =
        capture(ThemeFamily.OBS, darkTheme = true, name = "live_deck_obs_dark.png")

    @Test
    fun liveDeckNordDark() =
        capture(ThemeFamily.NORD, darkTheme = true, name = "live_deck_nord_dark.png")

    @Test
    fun liveDeckHighContrastDark() =
        capture(
            ThemeFamily.HIGH_CONTRAST,
            darkTheme = true,
            name = "live_deck_high_contrast_dark.png",
        )

    @Test
    fun liveDeckSceneDeckLight() =
        capture(ThemeFamily.SCENEDECK, darkTheme = false, name = "live_deck_scenedeck_light.png")

    /** Tablet width: the adaptive grid should fit ≥4 columns. */
    @Test
    @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PixelTablet)
    fun liveDeckTabletDark() =
        capture(ThemeFamily.SCENEDECK, darkTheme = true, name = "live_deck_tablet_dark.png")
}
