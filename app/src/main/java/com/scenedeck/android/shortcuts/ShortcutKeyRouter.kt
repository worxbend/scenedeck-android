package com.scenedeck.android.shortcuts

import android.view.KeyEvent
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.WorkspaceSession

/** The shell increments activation on route/focus changes; null disables input entirely. */
internal data class ShortcutKeyContext(val activation: Long, val shortcuts: ShortcutState.Ready)

/**
 * Activity-thread adapter, called AFTER normal child dispatch. It owns no coroutine or OBS client.
 */
internal class ShortcutKeyRouter {
    private data class PhysicalKey(val device: Int, val code: Int)

    private data class Owner(val activation: Long, val origin: WorkspaceSession)

    private var owner: Owner? = null
    private val held = mutableMapOf<PhysicalKey, Boolean>()

    fun clear() {
        owner = null
        held.clear()
    }

    fun dispatch(
        event: KeyEvent,
        childHandled: Boolean,
        context: ShortcutKeyContext?,
        invoke: (WorkspaceSession, ShortcutBinding) -> Boolean,
    ): Boolean {
        val nextOwner = context?.let { Owner(it.activation, it.shortcuts.origin) }
        if (owner != nextOwner) {
            clear()
            owner = nextOwner
        }
        if (context == null) return childHandled
        val physical = PhysicalKey(event.deviceId, event.keyCode)
        return when (event.action) {
            KeyEvent.ACTION_UP -> (held.remove(physical) == true) || childHandled
            KeyEvent.ACTION_DOWN -> down(event, physical, childHandled, context, invoke)
            else -> childHandled
        }
    }

    private fun down(
        event: KeyEvent,
        physical: PhysicalKey,
        childHandled: Boolean,
        context: ShortcutKeyContext,
        invoke: (WorkspaceSession, ShortcutBinding) -> Boolean,
    ): Boolean {
        val owned = held[physical]
        val chord = event.shortcutChordOrNull()
        return when {
            owned != null -> owned || childHandled
            !event.isInitialDown() || chord == null || held.size >= MAX_HELD_KEYS -> childHandled
            else -> {
                // Remember unbound/child-owned downs too; duplicate downs cannot acquire them.
                held[physical] = false
                val binding =
                    context.shortcuts.bindings.firstOrNull { it.enabled && it.chord == chord }
                val consumed =
                    !childHandled && binding != null && invoke(context.shortcuts.origin, binding)
                held[physical] = consumed
                childHandled || consumed
            }
        }
    }

    private companion object {
        const val MAX_HELD_KEYS = 32
    }
}

private fun KeyEvent.isInitialDown() = repeatCount == 0 && !isCanceled

/** A finite allow-list leaves editing, navigation, activation, media and system keys alone. */
internal fun KeyEvent.shortcutChordOrNull(): ShortcutChord? {
    val key =
        when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                ('A' + keyCode - KeyEvent.KEYCODE_A).toString()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (keyCode - KeyEvent.KEYCODE_0).toString()
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                "NUM${keyCode - KeyEvent.KEYCODE_NUMPAD_0}"
            else -> null
        } ?: return null
    return shortcutModifiers(metaState)?.let { ShortcutChord(key, it) }
}

private fun shortcutModifiers(metaState: Int): Int? {
    val normalized = KeyEvent.normalizeMetaState(metaState)
    val supported =
        KeyEvent.META_CTRL_MASK or
            KeyEvent.META_ALT_MASK or
            KeyEvent.META_SHIFT_MASK or
            KeyEvent.META_META_MASK or
            KeyEvent.META_CAPS_LOCK_ON or
            KeyEvent.META_NUM_LOCK_ON or
            KeyEvent.META_SCROLL_LOCK_ON
    if (normalized and supported.inv() != 0) return null
    var modifiers = 0
    if (normalized and KeyEvent.META_CTRL_MASK != 0) modifiers = modifiers or ShortcutChord.CTRL
    if (normalized and KeyEvent.META_ALT_MASK != 0) modifiers = modifiers or ShortcutChord.ALT
    if (normalized and KeyEvent.META_SHIFT_MASK != 0) modifiers = modifiers or ShortcutChord.SHIFT
    if (normalized and KeyEvent.META_META_MASK != 0) modifiers = modifiers or ShortcutChord.META
    return modifiers
}
