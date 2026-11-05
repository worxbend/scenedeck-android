package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.RecordingAction
import com.scenedeck.android.core.data.RecordingNotice
import com.scenedeck.android.core.data.RecordingResult
import com.scenedeck.android.core.data.RecordingToolsRepository
import com.scenedeck.android.core.data.RecordingToolsState
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingToolsSheet(
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    viewModel: RecordingToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        RecordingToolsContent(
            state,
            { action, chapter ->
                state.origin?.let {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.perform(it, action, chapter)
                }
            },
            onDismiss,
        )
    }
}

@Composable
internal fun RecordingToolsContent(
    state: RecordingToolsState,
    onAction: (RecordingAction, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chapter by remember(state.origin) { mutableStateOf("") }
    val paused = state.record?.paused == true
    val pauseAction = if (paused) RecordingAction.RESUME else RecordingAction.PAUSE
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudioPageHeader("Recording tools", recordingStatus(state), SceneIcon.RECORD.imageVector)
        Text(
            "Controls apply to the current OBS recording. Streaming continues while recording is paused."
        )
        Button(
            onClick = { onAction(pauseAction, "") },
            enabled = state.canRun(pauseAction),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (paused) "Resume recording" else "Pause recording")
        }
        if (!state.capabilities.pauseRecording || !state.capabilities.resumeRecording)
            Text(
                "OBS does not advertise all pause/resume controls.",
                style = MaterialTheme.typography.bodySmall,
            )
        StudioSectionHeader("Advanced recording")
        Text(
            "Support depends on the recording format and settings in OBS. " +
                "Resume recording before splitting or adding a chapter.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { onAction(RecordingAction.SPLIT, "") },
            enabled = state.canRun(RecordingAction.SPLIT),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Split recording file")
        }
        if (!state.capabilities.splitRecording)
            Text(
                "File splitting is unavailable on this OBS connection.",
                style = MaterialTheme.typography.bodySmall,
            )
        OutlinedTextField(
            value = chapter,
            onValueChange = { chapter = it.take(RecordingToolsRepository.MAX_CHAPTER_LENGTH) },
            label = { Text("Chapter name (optional)") },
            singleLine = true,
            enabled = state.canRun(RecordingAction.CHAPTER),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction(RecordingAction.CHAPTER, chapter) },
            enabled = state.canRun(RecordingAction.CHAPTER),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add OBS chapter")
        }
        if (!state.capabilities.recordingChapter)
            Text(
                "Chapter markers are unavailable on this OBS connection.",
                style = MaterialTheme.typography.bodySmall,
            )
        RecordingFeedback(state)
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun RecordingFeedback(state: RecordingToolsState) {
    val message =
        when {
            state.pending != null -> "${state.pending!!.label()}… Waiting for OBS."
            state.busy -> "Waiting for the previous action to finish."
            else -> state.notice?.message()
        }
    if (message != null)
        Text(
            message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
}

private fun recordingStatus(state: RecordingToolsState): String =
    when {
        state.origin == null -> "Not connected · recording state unknown"
        state.record == null -> "Waiting for a fresh recording status"
        state.record?.active == false -> "Recording is stopped"
        state.record?.paused == true -> "Recording paused"
        else -> "Recording active"
    }

private fun RecordingAction.label(): String =
    when (this) {
        RecordingAction.PAUSE -> "Pausing recording"
        RecordingAction.RESUME -> "Resuming recording"
        RecordingAction.SPLIT -> "Splitting file"
        RecordingAction.CHAPTER -> "Adding chapter"
    }

private fun RecordingNotice.message(): String =
    when (result) {
        RecordingResult.CONFIRMED ->
            if (action == RecordingAction.PAUSE) "OBS confirmed recording paused."
            else "OBS confirmed recording resumed."
        RecordingResult.ACCEPTED ->
            if (action == RecordingAction.SPLIT) "OBS accepted the file split."
            else "OBS accepted the chapter marker."
        RecordingResult.REJECTED ->
            "OBS rejected this action. Check the recording format and current state in OBS."
        RecordingResult.UNKNOWN ->
            "Outcome unknown. Check OBS before trying again; the action was not retried."
        RecordingResult.CHANGED -> "Recording changed. Check its current state before trying again."
    }

@Composable
internal fun RecordingToolsOverlay(open: Boolean, hapticsEnabled: Boolean, onDismiss: () -> Unit) {
    if (open) RecordingToolsSheet(hapticsEnabled, onDismiss)
}
