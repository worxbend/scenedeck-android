package com.scenedeck.android.feature.live

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scenedeck.android.core.data.DeckState

@Composable
internal fun DeckOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    deckState: DeckState,
    reorderMode: Boolean,
    reorderAllowed: Boolean,
    previewsEnabled: Boolean,
    tBarOpen: Boolean,
    onReorderToggle: () -> Unit,
    onPreviewsToggle: (Boolean) -> Unit,
    onStudioToggle: (Boolean) -> Unit,
    onTBarToggle: () -> Unit,
    onRecordingTools: () -> Unit,
) {
    val reorderLabel = if (reorderMode) R.string.finish_reordering else R.string.reorder_scenes
    val previewsLabel = if (previewsEnabled) R.string.hide_previews else R.string.show_previews
    val studioLabel = if (deckState.studioMode) R.string.disable_studio else R.string.enable_studio
    val tBarLabel = if (tBarOpen) R.string.hide_tbar else R.string.show_tbar
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Recording tools") },
            onClick = {
                onDismiss()
                onRecordingTools()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(reorderLabel)) },
            onClick = {
                onReorderToggle()
                onDismiss()
            },
            enabled = reorderAllowed,
        )
        DropdownMenuItem(
            text = { Text(stringResource(previewsLabel)) },
            onClick = {
                onPreviewsToggle(!previewsEnabled)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(studioLabel)) },
            onClick = {
                onStudioToggle(!deckState.studioMode)
                onDismiss()
            },
        )
        if (deckState.studioMode) {
            DropdownMenuItem(
                text = { Text(stringResource(tBarLabel)) },
                onClick = {
                    onTBarToggle()
                    onDismiss()
                },
            )
        }
    }
}
