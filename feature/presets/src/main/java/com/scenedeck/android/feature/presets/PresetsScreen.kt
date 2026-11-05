package com.scenedeck.android.feature.presets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

@Composable
fun PresetsScreen(
    modifier: Modifier = Modifier,
    viewModel: PresetsViewModel = hiltViewModel(),
    onNavigate: (PresetsDestination) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onAction(PresetsAction.LeaveScreen)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onAction(PresetsAction.LeaveScreen)
        }
    }
    BackHandler(state.edits.editor != null || state.review.review != null) {
        val editor = state.edits.editor
        if (editor != null)
            viewModel.onAction(
                PresetsAction.Editor.Change(
                    editor.id,
                    if (editor.stepDraft != null) PresetEdit.CancelStep else PresetEdit.Close,
                )
            )
        else viewModel.onAction(PresetsAction.Review.Close)
    }
    PresetsContent(
        state,
        viewModel::onAction,
        {
            viewModel.onAction(PresetsAction.LeaveScreen)
            onNavigate(it)
        },
        modifier,
    )
}

@Composable
internal fun PresetsContent(
    state: PresetsUiState,
    onAction: (PresetsAction) -> Unit,
    onNavigate: (PresetsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.edits.editor
    val review = state.review.review
    val origin = editor?.origin ?: review?.origin ?: state.workspace.ready?.origin
    val spacing = SceneDeckTheme.spacing
    val pageKey = editor?.let { "${it.id}:${it.stepDraft?.id}" } ?: review?.id ?: "list"
    Column(modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        key(pageKey) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.pageGutter),
                verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
            ) {
                item {
                    StudioPageHeader(
                        "Presets",
                        origin?.let(state.workspace::title) ?: "Ordered OBS controls",
                        SceneDeckIcons.Presets,
                    )
                }
                when {
                    editor != null -> presetEditor(state, editor, onAction)
                    review != null -> presetReview(state, review, onAction, onNavigate)
                    else -> presetList(state, onAction, onNavigate)
                }
            }
        }
    }
    PresetEditDialogs(state, onAction)
}

@Composable
private fun PresetEditDialogs(state: PresetsUiState, onAction: (PresetsAction) -> Unit) {
    val editor = state.edits.editor
    if (editor?.discard == true) {
        val change: (PresetEdit) -> Unit = { onAction(PresetsAction.Editor.Change(editor.id, it)) }
        AlertDialog(
            onDismissRequest = { change(PresetEdit.KeepEditing) },
            title = { Text("Discard preset changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { change(PresetEdit.Discard) }) { Text("Discard changes") }
            },
            dismissButton = {
                TextButton(onClick = { change(PresetEdit.KeepEditing) }) { Text("Keep editing") }
            },
        )
    }
    state.edits.deletion?.let { deletion ->
        val dismiss = { onAction(PresetsAction.Editor.DismissDelete) }
        AlertDialog(
            onDismissRequest = { if (!state.edits.busy) dismiss() },
            title = { Text("Delete ${deletion.preset.name}?") },
            text = {
                Column {
                    Text(
                        "Remove this preset from ${state.workspace.title(deletion.origin)}. " +
                            "Saved History results remain."
                    )
                    state.edits.message?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(PresetsAction.Editor.ConfirmDelete(deletion)) },
                    enabled = !state.edits.busy && state.workspace.ready?.origin == deletion.origin,
                ) {
                    Text("Delete preset")
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss, enabled = !state.edits.busy) { Text("Cancel") }
            },
        )
    }
}
