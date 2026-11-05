package com.scenedeck.android.feature.shortcuts

import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutDraft
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.isSupported
import java.util.UUID

internal enum class ShortcutKind(val label: String, val targetType: String? = null) {
    SCENE("Select scene", "scene"),
    TAKE("Take preview"),
    MUTE("Mute input", "input"),
    UNMUTE("Unmute input", "input"),
    START_STREAM("Start stream"),
    STOP_STREAM("Stop stream"),
    START_RECORD("Start recording"),
    STOP_RECORD("Stop recording");

    fun action(name: String): ShortcutAction? =
        when {
            targetType != null && (name.isBlank() || name.length > 1_024) -> null
            else ->
                when (this) {
                    SCENE -> ShortcutAction.Scene(name)
                    TAKE -> ShortcutAction.TakePreview
                    MUTE,
                    UNMUTE -> ShortcutAction.Mute(name, this == MUTE)
                    START_STREAM,
                    STOP_STREAM -> ShortcutAction.Stream(this == START_STREAM)
                    START_RECORD,
                    STOP_RECORD -> ShortcutAction.Recording(this == START_RECORD)
                }
        }
}

internal data class ShortcutEditorState(
    val origin: WorkspaceSession,
    val original: ShortcutBinding? = null,
    val draft: ShortcutDraft = original?.draft() ?: ShortcutDraft(),
    val id: String = UUID.randomUUID().toString(),
    val kind: ShortcutKind = draft.action.kind(),
    val name: String = draft.action.targetName().orEmpty(),
    val discard: Boolean = false,
) {
    fun value() = draft.copy(action = kind.action(name))

    val dirty: Boolean
        get() = value() != (original?.draft() ?: ShortcutDraft(id = draft.id))

    val valid: Boolean
        get() = draft.chord?.isSupported() == true && kind.action(name) != null
}

internal data class ShortcutDeletion(
    val origin: WorkspaceSession,
    val binding: ShortcutBinding,
    val id: String = UUID.randomUUID().toString(),
)

internal data class ShortcutEditUi(
    val editor: ShortcutEditorState? = null,
    val deletion: ShortcutDeletion? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

internal sealed interface ShortcutEdit {
    data class Kind(val value: ShortcutKind) : ShortcutEdit

    data class Name(val value: String) : ShortcutEdit

    data class Chord(val value: ShortcutChord) : ShortcutEdit

    data class Enabled(val value: Boolean) : ShortcutEdit

    data object Close : ShortcutEdit

    data object KeepEditing : ShortcutEdit

    data object Discard : ShortcutEdit
}

internal fun ShortcutAction?.targetName(): String? =
    when (this) {
        is ShortcutAction.Scene -> name
        is ShortcutAction.Mute -> inputName
        else -> null
    }

private fun ShortcutAction?.kind(): ShortcutKind =
    when (this) {
        is ShortcutAction.Scene,
        null -> ShortcutKind.SCENE
        ShortcutAction.TakePreview -> ShortcutKind.TAKE
        is ShortcutAction.Mute -> if (muted) ShortcutKind.MUTE else ShortcutKind.UNMUTE
        is ShortcutAction.Stream ->
            if (active) ShortcutKind.START_STREAM else ShortcutKind.STOP_STREAM
        is ShortcutAction.Recording ->
            if (active) ShortcutKind.START_RECORD else ShortcutKind.STOP_RECORD
    }
