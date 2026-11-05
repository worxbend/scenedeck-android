package com.scenedeck.android.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.scenedeck.android.core.data.LocalBookmark
import com.scenedeck.android.core.data.MAX_BOOKMARK_LABEL_LENGTH
import kotlinx.coroutines.launch

@Composable
internal fun BookmarkLabelDialog(
    bookmark: LocalBookmark,
    onSave: suspend (LocalBookmark, String) -> Boolean,
    onClose: () -> Unit,
) {
    var label by remember(bookmark.id) { mutableStateOf(bookmark.label) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text("Bookmark label") },
        text = {
            Column {
                OutlinedTextField(
                    label,
                    { label = it.take(MAX_BOOKMARK_LABEL_LENGTH) },
                    label = { Text("Label") },
                    enabled = !saving,
                    singleLine = true,
                )
                if (failed)
                    Text("Couldn't save. The bookmark may have changed; your draft is kept here.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (onSave(bookmark, label)) onClose() else failed = true
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !saving) { Text("Cancel") } },
    )
}

@Composable
internal fun BookmarkDeleteDialog(
    bookmark: LocalBookmark,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Delete bookmark?") },
        text = {
            Text(
                "Remove “${bookmark.label}” from ${bookmark.workspace.collectionName} on this device?"
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete bookmark") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
