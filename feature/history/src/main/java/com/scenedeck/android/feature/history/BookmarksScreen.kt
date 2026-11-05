package com.scenedeck.android.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.BookmarksState
import com.scenedeck.android.core.data.LocalBookmark
import com.scenedeck.android.core.data.MAX_BOOKMARKS
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.mono

@Composable
fun BookmarksScreen(
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookmarksContent(
        state,
        { state.workspace?.let(viewModel::mark) },
        viewModel::retrySave,
        viewModel::discard,
        viewModel::retryLoad,
        viewModel::rename,
        viewModel::delete,
        modifier,
    )
}

@Composable
internal fun BookmarksContent(
    state: BookmarksUiState,
    onMark: () -> Unit,
    onRetrySave: () -> Unit,
    onDiscard: () -> Unit,
    onRetryLoad: () -> Unit,
    onRename: suspend (LocalBookmark, String) -> Boolean,
    onDelete: (LocalBookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<LocalBookmark?>(null) }
    var deleting by remember { mutableStateOf<LocalBookmark?>(null) }
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudioPageHeader("Bookmarks", "Moments saved on this device", SceneDeckIcons.Bookmarks)
        }
        item {
            BookmarkCaptureControls(state, onMark, onRetrySave, onDiscard, onRetryLoad)
        }
        when (val saved = state.saved) {
            BookmarksState.Loading -> item { Text("Loading saved bookmarks…") }
            BookmarksState.Failed ->
                item {
                    Text("Couldn't load saved bookmarks.")
                    TextButton(onClick = onRetryLoad) { Text("Retry loading") }
                }
            is BookmarksState.Ready -> {
                if (saved.bookmarks.isEmpty())
                    item { Text("No bookmarks yet. Mark a moment while connected to OBS.") }
                items(saved.bookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(
                        bookmark,
                        state.connectionNames[bookmark.workspace.profileId]
                            ?: "Removed connection #${bookmark.workspace.profileId}",
                        { editing = bookmark },
                        { deleting = bookmark },
                    )
                }
            }
        }
    }
    editing?.let { BookmarkLabelDialog(it, onRename) { editing = null } }
    deleting?.let {
        BookmarkDeleteDialog(
            it,
            {
                onDelete(it)
                deleting = null
            },
        ) {
            deleting = null
        }
    }
}

@Composable
private fun BookmarkCaptureControls(
    state: BookmarksUiState,
    onMark: () -> Unit,
    onRetrySave: () -> Unit,
    onDiscard: () -> Unit,
    onRetryWorkspace: () -> Unit,
) {
    val pending = state.operation
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            state.workspace?.key?.let {
                val name = state.connectionNames[it.profileId] ?: "Connection ${it.profileId}"
                "Current workspace · $name / ${it.collectionName}"
            } ?: workspaceExplanation(state.workspaceState)
        )
        if (state.workspaceState == WorkspaceState.Failed)
            TextButton(onClick = onRetryWorkspace) { Text("Retry workspace") }
        Button(
            onClick = onMark,
            enabled = state.workspace != null && !pending.saving && pending.unsaved == null,
        ) {
            Text(if (pending.saving) "Saving…" else "Mark now")
        }
        Text(
            "Uses the latest OBS times, up to 3 seconds old. " +
                "Paused recording keeps its reported position; unknown readings stay unknown.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Keeps the newest $MAX_BOOKMARKS bookmarks. Older bookmarks are removed automatically. " +
                "Local marks do not add chapters in OBS.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Times are for OBS outputs, not positions within individual split files.",
            style = MaterialTheme.typography.bodySmall,
        )
        pending.message?.let {
            Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (pending.unsaved != null && !pending.saving)
            FlowRow {
                TextButton(onClick = onRetrySave) { Text("Retry save") }
                TextButton(onClick = onDiscard) { Text("Discard unsaved bookmark") }
            }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: LocalBookmark,
    connection: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(bookmark.label, style = MaterialTheme.typography.titleMedium)
            Text(
                "$connection · ${bookmark.workspace.collectionName}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Device time · ${bookmarkDate(bookmark.createdAtMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                outputTimeText("Stream", bookmark.times.stream),
                style = MaterialTheme.typography.mono,
            )
            Text(
                outputTimeText("Recording", bookmark.times.recording),
                style = MaterialTheme.typography.mono,
            )
            FlowRow {
                TextButton(
                    onClick = onEdit,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Edit label for ${bookmark.label}"
                        },
                ) {
                    Text("Edit label")
                }
                TextButton(
                    onClick = onDelete,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Delete bookmark ${bookmark.label}"
                        },
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun workspaceExplanation(state: WorkspaceState): String =
    when (state) {
        WorkspaceState.Failed ->
            "Couldn't resolve the current workspace. Saved bookmarks remain available."
        WorkspaceState.Resolving -> "Resolving the current OBS workspace…"
        else ->
            "Connect to a known workspace to capture a new moment. Saved bookmarks remain available."
    }
