package com.scenedeck.android.feature.live

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.NamedDeckState
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NamedDeckManagement(viewModel: NamedDeckViewModel, sceneState: DeckState) {
    val manager by viewModel.manager.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            viewModel.transfer.importFrom(it, resolver)
        }
    val exportFile =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) {
            viewModel.transfer.exportTo(it, resolver)
        }
    val editor = manager
    val ready = decks as? NamedDeckState.Ready
    if (editor != null && ready?.origin == editor.origin && sceneState.workspace == editor.origin) {
        ModalBottomSheet(
            onDismissRequest = viewModel::close,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NamedDeckManagerContent(
                editor,
                ready.snapshot,
                sceneState.allScenes.map { it.name },
                onShow = viewModel::show,
                onSave = viewModel::save,
                onClose = viewModel::close,
                onImport = {
                    if (viewModel.transfer.prepare(editor.origin))
                        importFile.launch(arrayOf("application/json", "text/plain"))
                },
                onExport = {
                    if (viewModel.transfer.prepare(editor.origin))
                        exportFile.launch("scenedeck-decks.json")
                },
                modifier = Modifier.fillMaxHeight(0.9f),
                connectionName =
                    connections.find { it.id == editor.origin.key.profileId }?.name
                        ?: "Current connection",
            )
        }
    }
}

@Composable
internal fun NamedDeckManagerContent(
    state: DeckManagerState,
    snapshot: NamedDeckSnapshot,
    scenes: List<String>,
    onShow: (DeckEditorPage) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    connectionName: String = "Current connection",
) {
    Column(
        modifier.imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudioPageHeader(
            title = "My decks",
            subtitle = "$connectionName · ${state.origin.key.collectionName}",
            icon = SceneDeckIcons.Scenes,
        )
        DeckEditorActions(state, onShow, onSave, onClose)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        DeckEditorBody(state, snapshot, scenes, onShow, onImport, onExport, Modifier.weight(1f))
    }
}

@Composable
private fun DeckEditorBody(
    state: DeckManagerState,
    snapshot: NamedDeckSnapshot,
    scenes: List<String>,
    onShow: (DeckEditorPage) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier,
) {
    when (val page = state.page) {
        DeckEditorPage.Browse ->
            DeckList(snapshot, !state.busy, onShow, onImport, onExport, modifier)
        is DeckEditorPage.Name ->
            Column(modifier.verticalScroll(rememberScrollState())) {
                StudioSectionHeader(
                    if (page.target == null) "Create deck" else "Rename ${page.target.name}"
                )
                OutlinedTextField(
                    value = page.draft,
                    onValueChange = { onShow(page.copy(draft = it)) },
                    label = { Text("Deck name") },
                    enabled = !state.busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Names are unique in this workspace. Scenes and roles stay unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        is DeckEditorPage.Members ->
            DeckMemberEditor(page, scenes, !state.busy, { onShow(it) }, modifier)
        is DeckEditorPage.Delete ->
            Column(modifier.verticalScroll(rememberScrollState())) {
                StudioSectionHeader("Delete ${page.target.name}?")
                Text(
                    "This removes the local deck and its membership. OBS scenes and curation are retained. " +
                        "If selected, Scenes returns to All scenes."
                )
            }
        is DeckEditorPage.Import -> DeckImportSummary(page, scenes, modifier)
    }
}

@Composable
private fun DeckList(
    snapshot: NamedDeckSnapshot,
    enabled: Boolean,
    onShow: (DeckEditorPage) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = { onShow(DeckEditorPage.Name(null, "")) }, enabled = enabled) {
                Text("Create deck")
            }
            Text(
                "Choose scenes for each deck and arrange them independently.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                TextButton(onClick = onImport, enabled = enabled) { Text("Import decks") }
                TextButton(onClick = onExport, enabled = enabled && snapshot.decks.isNotEmpty()) {
                    Text("Export decks")
                }
            }
        }
        items(snapshot.decks, key = { it.id }) { deck ->
            ListItem(
                headlineContent = { Text(deck.name) },
                supportingContent = { Text("${deck.sceneNames.size} scenes · Edit membership") },
                modifier =
                    Modifier.clickable(enabled = enabled) {
                        onShow(DeckEditorPage.Members(deck, deck.sceneNames))
                    },
                trailingContent = {
                    DeckItemMenu("Options for ${deck.name}", enabled) {
                        Action("Rename") { onShow(DeckEditorPage.Name(deck, deck.name)) }
                        Action("Delete") { onShow(DeckEditorPage.Delete(deck)) }
                    }
                },
            )
        }
    }
}

@Composable
private fun DeckImportSummary(
    page: DeckEditorPage.Import,
    scenes: List<String>,
    modifier: Modifier,
) {
    val preview = page.preview
    val known = scenes.toSet()
    val replacing = preview.replacementNames
    LazyColumn(modifier) {
        item {
            StudioSectionHeader("Import into ${preview.origin.key.collectionName}")
            Text("${preview.additionCount} new · ${preview.replacementCount} replaced")
            Text(
                "Same-name decks are replaced, including membership and order. " +
                    "Other decks and the selected deck are retained.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(preview.entries, key = { it.name }) { entry ->
            ListItem(
                headlineContent = { Text(entry.name) },
                overlineContent = {
                    Text(if (entry.name in replacing) "Replace deck" else "New deck")
                },
                supportingContent = {
                    Text(
                        "${entry.scenes.size} scenes · ${entry.scenes.count { it !in known }} unavailable in OBS"
                    )
                },
            )
        }
    }
}

@Composable
private fun DeckEditorActions(
    state: DeckManagerState,
    onShow: (DeckEditorPage) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val browsing = state.page == DeckEditorPage.Browse
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { if (browsing) onClose() else onShow(DeckEditorPage.Browse) },
            enabled = !state.busy,
        ) {
            Text(if (browsing) "Close" else "Back")
        }
        if (!browsing)
            Button(onClick = onSave, enabled = !state.busy) { Text(editorSaveLabel(state)) }
    }
}

private fun editorSaveLabel(state: DeckManagerState): String =
    when {
        state.busy -> "Saving…"
        state.page is DeckEditorPage.Delete -> "Delete deck"
        state.page is DeckEditorPage.Import -> "Import decks"
        else -> "Save"
    }
