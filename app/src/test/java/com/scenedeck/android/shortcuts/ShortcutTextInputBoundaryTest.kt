package com.scenedeck.android.shortcuts

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTextInputBoundaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val guard = ShortcutTextInputGuard()
    private val router = ShortcutKeyRouter()
    private lateinit var focus: FocusManager
    private var keyboard: SoftwareKeyboardController? = null
    private var invoked = 0
    private val context =
        ShortcutKeyContext(
            0,
            ShortcutState.Ready(
                TEST_WORKSPACE,
                listOf(
                    ShortcutBinding(
                        TEST_WORKSPACE.key,
                        "shortcut",
                        0,
                        ShortcutChord("F8"),
                        ShortcutAction.TakePreview,
                        true,
                    )
                ),
            ),
        )

    @Test
    fun focusedTextFieldBlocksUnhandledHardwareKeyEvenWithSoftwareKeyboardHidden() {
        render()
        compose.onNodeWithTag("first").performClick().assertIsFocused()
        compose.runOnIdle {
            assertTrue(guard.editing)
            keyboard?.hide()
        }
        compose.onNodeWithTag("first").assertIsFocused()
        dispatch(key())
        dispatch(key(action = KeyEvent.ACTION_UP))
        assertEquals(0, invoked)
        compose.runOnIdle {
            assertTrue(guard.editing)
            focus.clearFocus(force = true)
        }
        compose.runOnIdle { assertFalse(guard.editing) }
        dispatch(key())
        assertEquals(1, invoked)
    }

    @Test
    fun switchingBetweenFieldsKeepsGuardAndRemovingContentReleasesIt() {
        val visible = mutableStateOf(true)
        render { visible.value }
        compose.onNodeWithTag("first").performClick()
        compose.onNodeWithTag("second").performClick().assertIsFocused()
        compose.runOnIdle { assertTrue(guard.editing) }
        dispatch(key())
        assertEquals(0, invoked)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertFalse(guard.editing) }
    }

    @Test
    fun oldSessionCleanupCannotReleaseANewerSession() {
        val first = guard.enter()
        val second = guard.enter()
        guard.leave(first)
        guard.leave(first)
        assertTrue(guard.editing)
        guard.leave(second)
        assertFalse(guard.editing)
    }

    private fun render(visible: () -> Boolean = { true }) {
        compose.setContent {
            SceneDeckTheme {
                ShortcutTextInputBoundary(guard) {
                    focus = LocalFocusManager.current
                    keyboard = LocalSoftwareKeyboardController.current
                    if (visible()) {
                        var first by remember { mutableStateOf("") }
                        var second by remember { mutableStateOf("") }
                        Column {
                            OutlinedTextField(first, { first = it }, Modifier.testTag("first"))
                            OutlinedTextField(second, { second = it }, Modifier.testTag("second"))
                        }
                    }
                }
            }
        }
    }

    private fun dispatch(event: KeyEvent) {
        compose.runOnIdle {
            val childHandled = compose.activity.dispatchKeyEvent(event)
            assertFalse("F8 is unhandled by the focused child", childHandled)
            router.dispatch(event, childHandled, context.takeUnless { guard.editing }) { _, _ ->
                invoked++
                true
            }
        }
    }
}
