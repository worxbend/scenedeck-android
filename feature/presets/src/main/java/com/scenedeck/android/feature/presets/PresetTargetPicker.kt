package com.scenedeck.android.feature.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.PresetTargets
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

private data class PresetTargetChoice(val name: String, val label: String, val detail: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetTargetPicker(
    draft: PresetStepEditor,
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
    dismiss: () -> Unit,
    select: (String) -> Unit,
    retry: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val choices = targetChoices(draft, workspace, origin)
    val filtered = choices?.filter {
        it.name.contains(query, true) || it.label.contains(query, true)
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(
            Modifier.fillMaxHeight(0.85f)
                .imePadding()
                .padding(horizontal = SceneDeckTheme.spacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        ) {
            StudioSectionHeader(if (draft.kind.scene) "Choose scene" else "Choose input")
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("Search names") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (choices == null) {
                Text(
                    "Names are unavailable or still loading. You can enter an exact OBS name in the step."
                )
                if (draft.kind.input) TextButton(onClick = retry) { Text("Retry names") }
            } else if (filtered.isNullOrEmpty()) Text("No matching names.")
            LazyColumn(Modifier.weight(1f)) {
                items(filtered.orEmpty(), key = { it.name }) { target ->
                    PresetTargetRow(target) { select(target.name) }
                }
            }
            TextButton(onClick = dismiss) { Text("Close chooser") }
        }
    }
}

@Composable
private fun PresetTargetRow(target: PresetTargetChoice, select: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = select)
            .padding(vertical = SceneDeckTheme.spacing.rowPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(target.label, style = MaterialTheme.typography.bodyLarge)
        target.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun targetChoices(
    draft: PresetStepEditor,
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
): List<PresetTargetChoice>? {
    if (workspace.ready?.origin != origin) return null
    return if (draft.kind.scene)
        workspace.scenes?.map { PresetTargetChoice(it.name, it.displayName, "OBS: ${it.name}") }
    else
        (workspace.targets as? PresetTargets.Ready)
            ?.takeIf { it.origin == origin }
            ?.inputs
            ?.map { PresetTargetChoice(it.name, it.name, null) }
}
