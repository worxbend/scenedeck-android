package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1400dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OperatorDeckTest {
    @get:Rule val compose = createComposeRule()
    private var width by mutableStateOf(1200.dp)
    private var height by mutableStateOf(800.dp)
    private var scene: String? = null
    private var takes = 0
    private lateinit var inputMode: InputModeManager

    @Test fun sceneDeckDark() = render(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = render(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = render(ThemeFamily.OBS, true)

    @Test fun obsLight() = render(ThemeFamily.OBS, false)

    @Test
    fun portrait() {
        width = 900.dp
        height = 1100.dp
        render(ThemeFamily.SCENEDECK, false, suffix = "portrait")
    }

    @Test
    fun compactLargeText() {
        width = 760.dp
        height = 340.dp
        render(ThemeFamily.SCENEDECK, true, 1.3f, "compact")
        compose.onNodeWithText("TRANSITION").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun largeText() {
        width = 960.dp
        height = 600.dp
        render(ThemeFamily.SCENEDECK, false, 2f, "large_text")
        compose.onNodeWithText("TRANSITION").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resizeRetainsSearchAndKeyboardDispatchesLiteralTargetOnce() {
        render(ThemeFamily.SCENEDECK, true, suffix = "resize_before")
        compose.onNodeWithContentDescription("Find scene").performClick()
        compose.onNodeWithText("Search scenes").performTextInput("guest")
        compose.runOnIdle { width = 393.dp }
        compose.onNodeWithText("guest").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("Guest camera").performSemanticsAction(
            SemanticsActions.RequestFocus
        ) {
            it()
        }
        compose.onNodeWithText("Guest camera").assertIsFocused()
        compose.onNodeWithText("Guest camera").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals("Camera B", scene)
        assertEquals(0, takes)
        compose.runOnIdle { width = 1200.dp }
        compose.onNodeWithText("TRANSITION").performClick()
        assertEquals(1, takes)
    }

    private fun render(
        family: ThemeFamily,
        dark: Boolean,
        fontScale: Float = 1f,
        suffix: String = "${family.name.lowercase()}_${if (dark) "dark" else "light"}",
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                val density = LocalDensity.current
                val manager = LocalInputModeManager.current
                SideEffect { inputMode = manager }
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    Box(Modifier.requiredSize(width, height).testTag("operator")) {
                        Surface {
                            LiveDeckContent(
                                deckState = deck(),
                                deckOptions = NamedDeckSnapshot(),
                                telemetry = Telemetry(),
                                pendingScene = null,
                                hapticsEnabled = false,
                                motionLevel = MotionLevel.OFF,
                                thumbnails = emptyMap(),
                                onSceneTap = { scene = it },
                                onStreamClick = {},
                                onRecordClick = {},
                                onToggleVirtualCam = {},
                                onQuickEditSave = { _, _ -> true },
                                onReorder = {},
                                onStudioToggle = {},
                                onTransitionClick = { takes++ },
                                onCutClick = {},
                                onTransitionSelect = {},
                                onTransitionDurationChange = {},
                                previewsEnabled = true,
                                onPreviewsToggle = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Start Stream").assertIsDisplayed()
        compose.onNodeWithContentDescription("Find scene").assertIsDisplayed()
        compose.onNodeWithTag("operator").captureRoboImage("operator_deck_$suffix.png")
        if (width >= 900.dp) compose.onAllNodesWithText("OBS · Camera A")[0].assertIsDisplayed()
    }

    private fun deck(): DeckState {
        val scenes =
            listOf(
                SceneCardState(
                    "Camera A",
                    SceneRole.PRIMARY,
                    null,
                    null,
                    0,
                    true,
                    localAlias = "Presenter",
                ),
                SceneCardState(
                    "Camera B",
                    SceneRole.PRIMARY,
                    null,
                    null,
                    1,
                    false,
                    localAlias = "Guest camera",
                ),
                SceneCardState(
                    "Screen",
                    SceneRole.PRIMARY,
                    null,
                    null,
                    2,
                    false,
                    true,
                ),
                SceneCardState(
                    "Break",
                    SceneRole.SECONDARY,
                    null,
                    null,
                    3,
                    false,
                ),
            )
        return DeckState(
            connectionState = ConnectionState.Ready(ObsVersionInfo("Demo", "5", 1)),
            workspace = TEST_WORKSPACE,
            scenes = scenes,
            currentProgramScene = "Camera A",
            previewScene = "Screen",
            studioMode = true,
        )
    }
}
