package com.scenedeck.android.feature.presets

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PresetsRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun capturePages(family: ThemeFamily, dark: Boolean) {
        val state = mutableStateOf(presetsPreviewState(PresetPreviewPage.LIST))
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface { PresetsContent(state.value, {}, {}) }
            }
        }
        val mode = if (dark) "dark" else "light"
        PresetPreviewPage.entries.forEach { page ->
            compose.runOnIdle { state.value = presetsPreviewState(page) }
            compose
                .onRoot()
                .captureRoboImage(
                    "presets_${page.name.lowercase()}_${family.name.lowercase()}_$mode.png"
                )
            if (page == PresetPreviewPage.REVIEW) {
                compose.onNodeWithText("Run 3 steps").performScrollTo().assertIsNotEnabled()
                compose
                    .onRoot()
                    .captureRoboImage("presets_consent_${family.name.lowercase()}_$mode.png")
            }
        }
        compose.runOnIdle { state.value = presetsPreviewState(PresetPreviewPage.STEP) }
        compose.onNodeWithText("Choose input").performScrollTo().performClick()
        compose.onNodeWithText("Off-program clip").assertIsDisplayed()
        compose.onRoot().captureRoboImage("presets_picker_${family.name.lowercase()}_$mode.png")
    }

    @Test fun sceneDeckDark() = capturePages(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = capturePages(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capturePages(ThemeFamily.OBS, true)

    @Test fun obsLight() = capturePages(ThemeFamily.OBS, false)

    @Test
    @Config(qualifiers = "w840dp-h360dp-land-mdpi")
    fun compactHeightEditorCanReachAndReorderLastStep() {
        val state = presetsPreviewState(PresetPreviewPage.EDITOR)
        val actions = mutableListOf<PresetsAction>()
        compose.setContent {
            SceneDeckTheme { Surface { PresetsContent(state, actions::add, {}) } }
        }
        compose
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Move step 3 up"))
        compose
            .onNodeWithContentDescription("Move step 3 up")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        val editor = state.edits.editor!!
        assertEquals(
            PresetsAction.Editor.Change(editor.id, PresetEdit.Move(editor.steps[2].id, -1)),
            actions.single(),
        )
        compose.onRoot().captureRoboImage("presets_editor_compact.png")
    }

    @Test
    @Config(qualifiers = "w840dp-h360dp-land-mdpi")
    fun compactHeightReviewStillRequiresConcreteOutputConsent() {
        val state = mutableStateOf(presetsPreviewState(PresetPreviewPage.REVIEW))
        val actions = mutableListOf<PresetsAction>()
        compose.setContent {
            SceneDeckTheme {
                Surface {
                    PresetsContent(
                        state.value,
                        { action ->
                            actions += action
                            if (action is PresetsAction.Review.Acknowledge) {
                                val review = state.value.review.review!!
                                state.value =
                                    state.value.copy(
                                        review =
                                            PresetReviewUi(
                                                review.copy(acknowledged = setOf(action.index))
                                            )
                                    )
                            }
                        },
                        {},
                    )
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Run 3 steps"))
        compose.onNodeWithText("Run 3 steps").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Confirm: Stop recording"))
        compose.onNodeWithText("Confirm: Stop recording").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Run 3 steps"))
        compose.onNodeWithText("Run 3 steps").performScrollTo().assertIsEnabled().performClick()
        assertTrue(actions.last() is PresetsAction.Review.Run)
        compose.onRoot().captureRoboImage("presets_review_compact.png")
    }

    @Test
    @Config(qualifiers = "w840dp-h360dp-land-mdpi")
    fun compactStepFormKeepsItsApplyControlReachable() {
        val state = presetsPreviewState(PresetPreviewPage.STEP)
        val actions = mutableListOf<PresetsAction>()
        compose.setContent {
            SceneDeckTheme { Surface { PresetsContent(state, actions::add, {}) } }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add this step"))
        compose.onNodeWithText("Add this step").performScrollTo().assertIsEnabled().performClick()
        assertEquals(
            PresetsAction.Editor.Change(state.edits.editor!!.id, PresetEdit.ApplyStep),
            actions.single(),
        )
        compose.onRoot().captureRoboImage("presets_step_compact.png")
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-mdpi")
    fun unknownResultAndOriginalWorkspaceRemainReadableAtLargeFont() {
        val state = presetsPreviewState(PresetPreviewPage.RESULT)
        compose.setContent {
            SceneDeckTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 1.3f)
                ) {
                    Surface { PresetsContent(state, {}, {}) }
                }
            }
        }
        compose
            .onNodeWithText(
                "An unknown write may have taken effect. Check OBS before running again."
            )
            .performScrollTo()
            .assertIsDisplayed()
        compose.onRoot().captureRoboImage("presets_result_large_font.png")
    }

    @Test
    fun targetChooserSearchesAliasButReturnsLiteralObsName() {
        val base = presetsPreviewState(PresetPreviewPage.EDITOR)
        val origin = base.workspace.ready!!.origin
        var selected: String? = null
        compose.setContent {
            SceneDeckTheme {
                Surface {
                    PresetTargetPicker(
                        PresetStepEditor(),
                        base.workspace,
                        origin,
                        {},
                        { selected = it },
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Search names").performTextInput("Host")
        compose.onNodeWithText("Host close-up").performClick()
        assertEquals("Camera 01", selected)
    }

    @Test
    fun staleWorkspaceDisablesEditorAndRunningPanelCancelsOnlyDisplayedRun() {
        val state = mutableStateOf(presetsPreviewState(PresetPreviewPage.EDITOR))
        val old = state.value.workspace.ready!!
        state.value =
            state.value.copy(
                workspace =
                    state.value.workspace.copy(
                        saved = old.copy(origin = old.origin.copy(key = WorkspaceKey(8, "Other")))
                    )
            )
        val actions = mutableListOf<PresetsAction>()
        compose.setContent {
            SceneDeckTheme { Surface { PresetsContent(state.value, actions::add, {}) } }
        }
        compose.onNodeWithText("Save preset").assertIsNotEnabled()
        compose.onNodeWithText("Preset name").assertIsNotEnabled()
        val result = presetsPreviewState(PresetPreviewPage.RESULT)
        compose.runOnIdle {
            state.value =
                result.copy(
                    run =
                        result.run!!.copy(
                            run = result.run.run.copy(outcome = PresetRunOutcome.RUNNING)
                        ),
                    running = true,
                )
        }
        compose.onNodeWithText("Stop remaining steps").performScrollTo().performClick()
        assertEquals(PresetsAction.CancelRun("run"), actions.single())
    }
}
