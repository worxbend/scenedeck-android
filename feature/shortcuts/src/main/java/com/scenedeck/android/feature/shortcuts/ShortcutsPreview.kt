package com.scenedeck.android.feature.shortcuts

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ObsSessionToken

internal fun shortcutsPreviewState(editing: Boolean = false): ShortcutsUiState {
    val origin = WorkspaceSession(WorkspaceKey(7, "Studio"), 1, ObsSessionToken())
    val scene =
        ShortcutBinding(
            origin.key,
            "camera",
            0,
            ShortcutChord("F8", ShortcutChord.CTRL),
            ShortcutAction.Scene("Camera 01"),
            true,
        )
    val stop =
        ShortcutBinding(
            origin.key,
            "record",
            0,
            ShortcutChord("F9"),
            ShortcutAction.Recording(false),
            false,
        )
    return ShortcutsUiState(
        workspace =
            ShortcutWorkspaceUi(
                saved = ShortcutState.Ready(origin, listOf(scene, stop)),
                scenes =
                    listOf(
                        SceneCardState(
                            "Camera 01",
                            SceneRole.PRIMARY,
                            null,
                            null,
                            0,
                            true,
                            localAlias = "Host close-up",
                        )
                    ),
                profiles = mapOf(7L to "Main studio"),
            ),
        edits =
            if (editing) ShortcutEditUi(editor = ShortcutEditorState(origin, scene))
            else ShortcutEditUi(),
    )
}

@PreviewLightDark
@Composable
private fun ShortcutsListPreview() {
    SceneDeckTheme { Surface { ShortcutsContent(shortcutsPreviewState(), {}, {}) } }
}

@PreviewLightDark
@Composable
private fun ShortcutEditorPreview() {
    SceneDeckTheme { Surface { ShortcutsContent(shortcutsPreviewState(true), {}, {}) } }
}
