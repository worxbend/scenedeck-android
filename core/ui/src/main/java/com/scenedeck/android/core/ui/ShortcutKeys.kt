package com.scenedeck.android.core.ui

import android.view.KeyEvent
import com.scenedeck.android.core.data.ShortcutChord

/** A finite allow-list leaves editing, navigation, activation, media and system keys alone. */
fun KeyEvent.shortcutChordOrNull(): ShortcutChord? {
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
