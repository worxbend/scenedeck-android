package com.scenedeck.android.feature.shortcuts

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
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
class ShortcutsRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean) {
        val state = mutableStateOf(shortcutsPreviewState())
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface { ShortcutsContent(state.value, {}, {}) }
            }
        }
        val suffix = "${family.name.lowercase()}_${if (dark) "dark" else "light"}"
        compose.onRoot().captureRoboImage("shortcuts_list_$suffix.png")
        compose.runOnIdle { state.value = shortcutsPreviewState(true) }
        compose.onRoot().captureRoboImage("shortcuts_editor_$suffix.png")
        compose.onNodeWithText("Change key").performScrollTo().performClick()
        compose.onNode(isDialog()).captureRoboImage("shortcuts_capture_$suffix.png")
        compose.onNodeWithText("Cancel capture").performClick()
        compose.onNodeWithText("Shortcut help").performScrollTo().performClick()
        compose.onNode(isDialog()).captureRoboImage("shortcuts_help_$suffix.png")
    }

    @Test fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    @Config(qualifiers = "w840dp-h360dp-land-mdpi")
    fun compactEditorKeepsSaveReachableAndCaptureRecordsOnlyOneChord() {
        val state = shortcutsPreviewState(true)
        val actions = mutableListOf<ShortcutsAction>()
        compose.setContent {
            SceneDeckTheme { Surface { ShortcutsContent(state, actions::add, {}) } }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save shortcut"))
        compose.onNodeWithText("Save shortcut").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("shortcuts_editor_compact.png")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Change key"))
        compose.onNodeWithText("Change key").performScrollTo().performClick()
        compose.onNode(isDialog()).performKeyInput { keyDown(Key.F8) }
        assertEquals(
            listOf(
                ShortcutsAction.Edit(
                    state.edits.editor!!.id,
                    ShortcutEdit.Chord(ShortcutChord("F8")),
                )
            ),
            actions,
        )
        compose.onNodeWithText("Record shortcut key").assertDoesNotExist()
    }

    @Test
    fun reservedKeyExplainsRejectionAndAliasPickerReturnsLiteralIdentity() {
        val state = shortcutsPreviewState(true)
        val actions = mutableListOf<ShortcutsAction>()
        compose.setContent {
            SceneDeckTheme { Surface { ShortcutsContent(state, actions::add, {}) } }
        }
        compose.onNodeWithText("Change key").performScrollTo().performClick()
        compose.onNode(isDialog()).performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        compose.onNodeWithText("This key is reserved.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel capture").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Choose scene"))
        compose.onNodeWithText("Choose scene").performScrollTo().performClick()
        compose.onNodeWithText("Search names").performTextInput("Host")
        compose.onNodeWithText("Host close-up").performClick()
        assertEquals(
            listOf(ShortcutsAction.Edit(state.edits.editor!!.id, ShortcutEdit.Name("Camera 01"))),
            actions,
        )
    }

    @Test
    fun largeTextAndStaleWorkspaceRetainDraftWithDisabledSave() {
        val base = shortcutsPreviewState(true)
        val ready = base.workspace.ready!!
        val state =
            base.copy(
                workspace =
                    base.workspace.copy(
                        saved =
                            ready.copy(origin = ready.origin.copy(key = WorkspaceKey(8, "Other")))
                    )
            )
        compose.setContent {
            SceneDeckTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Surface { ShortcutsContent(state, {}, {}) }
                }
            }
        }
        compose.onNodeWithText("The workspace changed.", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("shortcuts_stale_large_font.png")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save shortcut"))
        compose.onNodeWithText("Save shortcut").performScrollTo().assertIsNotEnabled()
    }
}
