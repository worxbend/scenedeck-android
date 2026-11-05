package com.scenedeck.android.core.data

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MAX_SHORTCUT_BINDINGS = 32

/** Platform-independent identifiers; Android key codes never enter persisted definitions. */
data class ShortcutChord(val key: String, val modifiers: Int = 0) {
    companion object {
        const val CTRL = 1
        const val ALT = 2
        const val SHIFT = 4
        const val META = 8
    }
}

/** Explicit target values. Scene selection follows Studio mode at invocation time. */
@Serializable
sealed interface ShortcutAction {
    @Serializable @SerialName("scene") data class Scene(val name: String) : ShortcutAction

    @Serializable @SerialName("take") data object TakePreview : ShortcutAction

    @Serializable
    @SerialName("mute")
    data class Mute(val inputName: String, val muted: Boolean) : ShortcutAction

    @Serializable @SerialName("stream") data class Stream(val active: Boolean) : ShortcutAction

    @Serializable
    @SerialName("recording")
    data class Recording(val active: Boolean) : ShortcutAction
}

data class ShortcutBinding(
    val workspace: WorkspaceKey,
    val id: String,
    val revision: Long,
    val chord: ShortcutChord,
    val action: ShortcutAction,
    val enabled: Boolean,
) {
    fun draft() = ShortcutDraft(id, chord, action, enabled)
}

/** A new editor starts unbound; retaining this ID makes failed-save retries idempotent. */
data class ShortcutDraft(
    val id: String = "shortcut:${UUID.randomUUID()}",
    val chord: ShortcutChord? = null,
    val action: ShortcutAction? = null,
    val enabled: Boolean = true,
)

sealed interface ShortcutState {
    data class Unavailable(val workspace: WorkspaceState) : ShortcutState

    data class Loading(val origin: WorkspaceSession) : ShortcutState

    data class Failed(val origin: WorkspaceSession) : ShortcutState

    data class Ready(val origin: WorkspaceSession, val bindings: List<ShortcutBinding>) :
        ShortcutState
}

enum class ShortcutEditFailure {
    INVALID,
    CHANGED,
    CHORD_IN_USE,
    LIMIT,
    STORAGE,
}

class ShortcutEditException(val reason: ShortcutEditFailure) :
    IllegalStateException("Shortcut is unavailable or changed")
