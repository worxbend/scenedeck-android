package com.scenedeck.android.feature.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.HistorySession
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    onBookmarks: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    BackHandler(state.selection.id != null) { viewModel.select(null) }
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) {
            viewModel.fileResult(it, resolver)
        }
    LaunchedEffect(state.export.request) {
        state.export.request?.let { name ->
            viewModel.pickerOpened()
            if (coroutineResult { picker.launch(name) }.isFailure) viewModel.pickerFailed()
        }
    }
    HistoryContent(
        state,
        viewModel::select,
        viewModel::configure,
        viewModel::delete,
        viewModel::export,
        viewModel::retry,
        onBookmarks,
        modifier,
    )
}

internal data class HistoryActions(
    val open: (String?) -> Unit,
    val configure: (Boolean, Int) -> Unit,
    val delete: (HistorySession?) -> Unit,
    val export: (String?) -> Unit,
    val retry: () -> Unit,
    val bookmarks: () -> Unit,
)

private data class HistoryDeletion(val session: HistorySession?)

@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onOpen: (String?) -> Unit,
    onConfigure: (Boolean, Int) -> Unit,
    onDelete: (String?) -> Unit,
    onExport: (String?) -> Unit,
    onRetry: () -> Unit,
    onBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deletion by remember { mutableStateOf<HistoryDeletion?>(null) }
    val actions =
        HistoryActions(
            onOpen,
            onConfigure,
            { deletion = HistoryDeletion(it) },
            onExport,
            onRetry,
            onBookmarks,
        )
    val spacing = SceneDeckTheme.spacing
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
        ) {
            item {
                StudioPageHeader(
                    "History",
                    "Local observations, with gaps kept visible",
                    SceneDeckIcons.History,
                )
            }
            state.operation.message?.let { message ->
                item {
                    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (state.export.busy) item { Text("Preparing or saving your export…") }
            if (state.selection.id == null) historyList(state, actions)
            else historyDetail(state, actions)
        }
    }
    deletion?.let { target ->
        HistoryDeleteDialog(
            target.session,
            state.busy,
            {
                onDelete(target.session?.id)
                deletion = null
            },
            { deletion = null },
        )
    }
}

@Composable
private fun HistoryDeleteDialog(
    session: HistorySession?,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session == null) "Clear history?" else "Delete observation?") },
        text = {
            Text(
                (session?.let {
                    "${it.workspace.collectionName} · ${bookmarkDate(it.startedAtMs)}\n\n"
                } ?: "") +
                    "Deletes this history from your device. Saved bookmarks remain in Bookmarks. " +
                    "Capture continues while enabled."
            )
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(if (session == null) "Clear saved history" else "Delete permanently")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
