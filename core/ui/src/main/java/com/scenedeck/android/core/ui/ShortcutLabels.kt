package com.scenedeck.android.core.ui

import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutIssue
import com.scenedeck.android.core.data.ShortcutOutcome
import com.scenedeck.android.core.data.ShortcutRunState

fun ShortcutChord.label(): String = buildList {
    if (modifiers and ShortcutChord.CTRL != 0) add("Ctrl")
    if (modifiers and ShortcutChord.ALT != 0) add("Alt")
    if (modifiers and ShortcutChord.SHIFT != 0) add("Shift")
    if (modifiers and ShortcutChord.META != 0) add("Meta")
    add(if (key.startsWith("NUM")) "Numpad ${key.removePrefix("NUM")}" else key)
}
    .joinToString(" + ")

fun ShortcutAction.actionLabel(): String =
    when (this) {
        is ShortcutAction.Scene -> "Select scene: $name"
        ShortcutAction.TakePreview -> "Take preview"
        is ShortcutAction.Mute -> "${if (muted) "Mute" else "Unmute"}: $inputName"
        is ShortcutAction.Stream -> "${if (active) "Start" else "Stop"} stream"
        is ShortcutAction.Recording -> "${if (active) "Start" else "Stop"} recording"
    }

private val shortcutIssues =
    mapOf(
        ShortcutIssue.WORKSPACE_CHANGED to "The connection or collection changed.",
        ShortcutIssue.BINDING_CHANGED to "The shortcut was changed or disabled.",
        ShortcutIssue.POLICY_CHANGED to "Input locks, allowed inputs or Output Safety changed.",
        ShortcutIssue.TARGET_CHANGED to "The target or Studio state changed.",
        ShortcutIssue.UNSUPPORTED to "OBS does not support the required controls.",
        ShortcutIssue.MISSING to
            "The OBS target is missing. Edit the shortcut to select a current target.",
        ShortcutIssue.LOCKED to "That input is locked.",
        ShortcutIssue.NOT_ALLOWED to "That input is outside the allowed audio list.",
        ShortcutIssue.STUDIO_OFF to "Turn on Studio mode before taking preview.",
        ShortcutIssue.NOT_READY to "The target is not ready.",
        ShortcutIssue.UNAVAILABLE to "The target could not be checked.",
        ShortcutIssue.BUSY to "A recording tool is busy.",
        ShortcutIssue.CANCELLED to "The shortcut was cancelled.",
        ShortcutIssue.EXPIRED to "The confirmation expired. Press the shortcut again to review it.",
        ShortcutIssue.TIMEOUT to "The shortcut reached its time limit.",
        ShortcutIssue.REJECTED to "OBS rejected the action.",
    )

fun ShortcutRunState.feedback(): String? =
    when (outcome) {
        ShortcutOutcome.UNKNOWN -> "Shortcut result unknown. Check OBS before trying again."
        ShortcutOutcome.NOT_RUN ->
            "Shortcut: ${issue?.let(shortcutIssues::getValue) ?: "The action did not run."}"
        else -> null
    }
