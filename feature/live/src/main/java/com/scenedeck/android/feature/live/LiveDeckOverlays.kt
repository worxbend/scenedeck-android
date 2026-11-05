package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneMetadataEdit
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.components.LocalSimulation
import com.scenedeck.android.core.designsystem.components.SimulationNotice
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus
import com.scenedeck.android.core.designsystem.theme.MotionLevel

@Composable
internal fun TransportConfirmationDialog(
    pending: TransportConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPreflight: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (pending) {
                    TransportConfirmation.START_STREAM ->
                        stringResource(R.string.confirm_start_stream_title)
                    TransportConfirmation.STOP_STREAM ->
                        stringResource(R.string.confirm_stop_stream_title)
                    TransportConfirmation.START_RECORD ->
                        stringResource(R.string.confirm_start_record_title)
                    TransportConfirmation.STOP_RECORD ->
                        stringResource(R.string.confirm_stop_record_title)
                }
            )
        },
        text = {
            Column {
                SimulationNotice()
                Text(
                    if (LocalSimulation.current)
                        "This changes the local simulation only. " +
                            "No broadcast or recording is created."
                    else
                        when (pending) {
                            TransportConfirmation.START_STREAM ->
                                stringResource(R.string.confirm_start_stream_body)
                            TransportConfirmation.STOP_STREAM ->
                                stringResource(R.string.confirm_stop_stream_body)
                            TransportConfirmation.START_RECORD ->
                                stringResource(R.string.confirm_start_record_body)
                            TransportConfirmation.STOP_RECORD ->
                                stringResource(R.string.confirm_stop_record_body)
                        }
                )
                if (onPreflight != null && pending.canOpenPreflight) {
                    TextButton(onClick = onPreflight) { Text("Open Preflight") }
                    Text("Opening checks cancels this confirmation. Start again when ready.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

internal val TransportConfirmation.canOpenPreflight: Boolean
    get() = this == TransportConfirmation.START_STREAM || this == TransportConfirmation.START_RECORD

@Composable
internal fun DeckWorkspaceStatus(deckState: DeckState, onRetryWorkspace: () -> Unit) {
    if (deckState.workspace == null)
        WorkspaceStatus(
            failed =
                deckState.curationFailed ||
                    deckState.scenesFailed ||
                    deckState.workspaceState == WorkspaceState.Failed,
            onRetry = onRetryWorkspace,
        )
}

@Composable
internal fun DeckQuickEdit(
    scene: SceneCardState,
    onDismiss: () -> Unit,
    onSave: suspend (String, SceneMetadataEdit) -> Boolean,
    sceneItemsVersion: Int,
    onLoadSceneItems: (suspend (String) -> List<com.scenedeck.android.core.model.SceneItemInfo>)?,
    onToggleSceneItem: (String, Int, Boolean) -> Unit,
) {
    QuickEditSheet(
        scene = scene,
        onDismiss = onDismiss,
        onSave = { edit -> onSave(scene.name, edit) },
        sceneItemsVersion = sceneItemsVersion,
        onLoadSceneItems = onLoadSceneItems?.let { loader -> { loader(scene.name) } },
        onToggleSceneItem = { itemId, enabled -> onToggleSceneItem(scene.name, itemId, enabled) },
    )
}

@Composable
internal fun StudioModeSection(
    deckState: DeckState,
    onTransitionClick: () -> Unit,
    onCutClick: () -> Unit,
    onTransitionSelect: (String) -> Unit,
    onTransitionDurationChange: (Int) -> Unit,
    motionLevel: MotionLevel,
    tBarOpen: Boolean,
) {
    if (deckState.studioMode) {
        Spacer(Modifier.height(8.dp))
        StudioModeBar(
            deckState = deckState,
            onTransitionClick = onTransitionClick,
            onCutClick = onCutClick,
            onTransitionSelect = onTransitionSelect,
            onTransitionDurationChange = onTransitionDurationChange,
            motionLevel = motionLevel,
            tBarOpen = tBarOpen,
        )
    }
}
