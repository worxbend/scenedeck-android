package com.scenedeck.android.core.designsystem.components

import com.scenedeck.android.core.designsystem.R

/** What a tap on the card does right now; drives the caption and the TalkBack label. */
internal data class SceneCardMode(
    val active: Boolean,
    val preview: Boolean,
    val pending: Boolean = false,
    val studioMode: Boolean = false,
    val reordering: Boolean = false,
)

/** TalkBack label: scene, tally state and the action a double-tap performs (if any). */
internal fun sceneCardDescription(label: String, mode: SceneCardMode): String {
    val state =
        when {
            mode.active -> "on air, current program scene"
            mode.preview -> "on preview"
            mode.pending -> "switching"
            else -> "ready"
        }
    val action =
        when {
            mode.reordering -> "reorder mode, drag the grip to move"
            mode.pending -> null
            mode.studioMode && mode.preview -> "use Transition or Cut to take it to program"
            mode.studioMode -> "double-tap to load in preview"
            mode.active -> null
            else -> "double-tap to switch"
        }
    return listOfNotNull("Scene $label", state, action).joinToString(", ")
}

internal fun sceneCardCaption(mode: SceneCardMode): Int =
    when {
        mode.pending -> R.string.scene_switching
        mode.reordering -> R.string.scene_drag_to_move
        mode.studioMode -> R.string.scene_tap_preview
        else -> R.string.scene_tap_switch
    }
