package com.scenedeck.android.feature.preflight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.PreflightConfig
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreflightSetupSheet(
    original: PreflightConfig,
    scenes: List<SceneCardState>,
    microphones: List<String>,
    onDismiss: () -> Unit,
    onSave: suspend (PreflightConfig) -> Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PreflightSetupContent(
            original,
            scenes,
            microphones,
            onDismiss,
            onSave,
            Modifier.fillMaxHeight(0.92f).imePadding().padding(horizontal = 20.dp),
        )
    }
}

@Composable
internal fun PreflightSetupContent(
    original: PreflightConfig,
    scenes: List<SceneCardState>,
    microphones: List<String>,
    onDismiss: () -> Unit,
    onSave: suspend (PreflightConfig) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var draft by remember(original) { mutableStateOf(original) }
    var disk by remember(original) { mutableStateOf(original.minimumDiskMb.toString()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val validDisk = disk.toLongOrNull()?.takeIf { it in 1..1_000_000 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioSectionHeader("Preflight setup")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                enabled = !saving && validDisk != null && draft.expectedCollection.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (onSave(draft.copy(minimumDiskMb = requireNotNull(validDisk))))
                                onDismiss()
                            else failed = true
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) "Saving…" else "Save setup")
            }
        }
        if (failed)
            Text(
                "Couldn't save setup. Check the fields, or reopen if the workspace or checklist changed.",
                color = MaterialTheme.colorScheme.error,
            )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                draft.expectedCollection,
                { draft = draft.copy(expectedCollection = it) },
                label = { Text("Expected OBS collection") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            PreflightMicrophoneField(draft.microphone.orEmpty(), microphones, !saving) {
                draft = draft.copy(microphone = it.takeIf(String::isNotBlank))
            }
            OutlinedTextField(
                disk,
                { disk = it },
                label = { Text("Minimum free disk space (MB)") },
                supportingText = { Text("1–1,000,000 MB on the OBS recording disk.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validDisk == null,
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            RequiredScenePicker(scenes, draft.requiredScenes, !saving) {
                draft = draft.copy(requiredScenes = it)
            }
            ManualCheckEditor(draft.manualChecks, !saving) { draft = draft.copy(manualChecks = it) }
        }
    }
}
