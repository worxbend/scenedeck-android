package com.scenedeck.android.shortcuts

import android.view.KeyEvent
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.model.ObsSessionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutKeyRouterTest {
    private val router = ShortcutKeyRouter()
    private val binding =
        ShortcutBinding(
            TEST_WORKSPACE.key,
            "shortcut",
            0,
            ShortcutChord("F8"),
            ShortcutAction.TakePreview,
            true,
        )
    private var context =
        ShortcutKeyContext(0, ShortcutState.Ready(TEST_WORKSPACE, listOf(binding)))
    private val invoked = mutableListOf<ShortcutBinding>()

    @Test
    fun downFiresOnceRepeatsAndDuplicateDownsAreConsumedAndUpOnlyReleases(): Unit {
        assertTrue(dispatch(key()))
        assertTrue(dispatch(key()))
        assertTrue(dispatch(key(repeat = 2)))
        assertEquals(listOf(binding), invoked)
        assertTrue(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertTrue(dispatch(key()))
        assertEquals(2, invoked.size)
    }

    @Test
    fun childConsumedDownCannotBeAcquiredByALaterUnhandledDuplicate(): Unit {
        assertTrue(dispatch(key(), childHandled = true))
        assertFalse(dispatch(key()))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertTrue(invoked.isEmpty())
        assertTrue(dispatch(key()))
        assertEquals(1, invoked.size)
    }

    @Test
    fun disabledOrUnboundDownCannotBecomeAnInvocationWhileHeld(): Unit {
        context =
            context.copy(
                shortcuts = context.shortcuts.copy(bindings = listOf(binding.copy(enabled = false)))
            )
        assertFalse(dispatch(key()))
        context = context.copy(shortcuts = context.shortcuts.copy(bindings = listOf(binding)))
        assertFalse(dispatch(key()))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertTrue(dispatch(key()))
        assertFalse(dispatch(key(code = KeyEvent.KEYCODE_F9)))
        assertEquals(1, invoked.size)
    }

    @Test
    fun keyUpCleanupStillHappensWhenChildConsumesIt(): Unit {
        dispatch(key())
        assertTrue(dispatch(key(action = KeyEvent.ACTION_UP), childHandled = true))
        dispatch(key())
        assertEquals(2, invoked.size)
    }

    @Test
    fun separateDevicesHaveIndependentLatchesAndHeldStateIsBounded(): Unit {
        repeat(32) { assertTrue(dispatch(key(device = it))) }
        assertFalse(dispatch(key(device = 32)))
        assertEquals(32, invoked.size)
        assertTrue(dispatch(key(device = 7, action = KeyEvent.ACTION_UP)))
        assertTrue(dispatch(key(device = 32)))
        assertEquals(33, invoked.size)
    }

    @Test
    fun resetsRejectRepeatOnlyInputAndUnownedUpsAcrossScopeOrRouteChanges(): Unit {
        dispatch(key())
        context = context.copy(activation = 1)
        assertFalse(dispatch(key(repeat = 1)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        dispatch(key())
        context =
            context.copy(
                shortcuts =
                    context.shortcuts.copy(
                        origin = TEST_WORKSPACE.copy(token = ObsSessionToken(sessionId = 2))
                    )
            )
        assertFalse(dispatch(key(repeat = 2)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        dispatch(key())
        router.clear()
        assertFalse(dispatch(key(repeat = 3)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertEquals(3, invoked.size)
    }

    @Test
    fun collectionRevisionAndConnectionAttemptChangesResetHeldState(): Unit {
        val origins =
            listOf(
                TEST_WORKSPACE.copy(token = ObsSessionToken(collectionRevision = 1)),
                TEST_WORKSPACE.copy(targetGeneration = 2),
            )
        origins.forEach { origin ->
            dispatch(key())
            context = context.copy(shortcuts = context.shortcuts.copy(origin = origin))
            assertFalse(dispatch(key(repeat = 1)))
            assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        }
        assertEquals(2, invoked.size)
    }

    @Test
    fun inactiveContextNeverInvokesAndClearsEarlierOwnership(): Unit {
        dispatch(key())
        assertFalse(router.dispatch(key(), false, null) { _, _ -> error("Inactive") })
        assertTrue(router.dispatch(key(), true, null) { _, _ -> error("Inactive") })
        assertFalse(dispatch(key(repeat = 1)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertEquals(1, invoked.size)
    }

    @Test
    fun canceledDownMultipleAndUnownedUpNeverInvoke(): Unit {
        assertFalse(dispatch(KeyEvent.changeFlags(key(), KeyEvent.FLAG_CANCELED)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_MULTIPLE)))
        assertFalse(dispatch(key(action = KeyEvent.ACTION_UP)))
        assertTrue(invoked.isEmpty())
    }

    @Test
    fun modifiersCollapseSidesIgnoreLocksAndDistinguishChords(): Unit {
        val bothSides =
            listOf(
                KeyEvent.META_CTRL_LEFT_ON or
                    KeyEvent.META_ALT_RIGHT_ON or
                    KeyEvent.META_SHIFT_LEFT_ON or
                    KeyEvent.META_META_RIGHT_ON,
                KeyEvent.META_CTRL_RIGHT_ON or
                    KeyEvent.META_ALT_LEFT_ON or
                    KeyEvent.META_SHIFT_RIGHT_ON or
                    KeyEvent.META_META_LEFT_ON,
            )
        bothSides.forEach { modifiers ->
            assertEquals(ShortcutChord("F8", 15), key(modifiers = modifiers).shortcutChordOrNull())
        }
        assertEquals(
            ShortcutChord("F8"),
            key(
                    modifiers =
                        KeyEvent.META_CAPS_LOCK_ON or
                            KeyEvent.META_NUM_LOCK_ON or
                            KeyEvent.META_SCROLL_LOCK_ON
                )
                .shortcutChordOrNull(),
        )
        assertFalse(dispatch(key(modifiers = KeyEvent.META_CTRL_ON)))
        assertTrue(invoked.isEmpty())
    }

    @Test
    fun canonicalKeysPreserveNumpadIdentityAndRejectReservedKeysOrModifiers(): Unit {
        assertEquals(ShortcutChord("A"), key(code = KeyEvent.KEYCODE_A).shortcutChordOrNull())
        assertEquals(ShortcutChord("Z"), key(code = KeyEvent.KEYCODE_Z).shortcutChordOrNull())
        assertEquals(ShortcutChord("9"), key(code = KeyEvent.KEYCODE_9).shortcutChordOrNull())
        assertEquals(
            ShortcutChord("NUM9"),
            key(code = KeyEvent.KEYCODE_NUMPAD_9).shortcutChordOrNull(),
        )
        assertEquals(ShortcutChord("F12"), key(code = KeyEvent.KEYCODE_F12).shortcutChordOrNull())
        listOf(
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_CTRL_LEFT,
            )
            .forEach {
                assertNull(key(code = it).shortcutChordOrNull())
            }
        assertNull(key(modifiers = KeyEvent.META_SYM_ON).shortcutChordOrNull())
        assertNull(key(modifiers = KeyEvent.META_FUNCTION_ON).shortcutChordOrNull())
    }

    private fun dispatch(event: KeyEvent, childHandled: Boolean = false): Boolean =
        router.dispatch(event, childHandled, context) { origin, target ->
            assertEquals(context.shortcuts.origin, origin)
            invoked += target
            true
        }
}

internal fun key(
    code: Int = KeyEvent.KEYCODE_F8,
    action: Int = KeyEvent.ACTION_DOWN,
    repeat: Int = 0,
    modifiers: Int = 0,
    device: Int = 1,
): KeyEvent = KeyEvent(0, 0, action, code, repeat, modifiers, device, 0)
