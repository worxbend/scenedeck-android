package com.scenedeck.android.feature.inventory

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue

@Composable
internal fun StaleRemovalSnackbar(
    viewModel: InventoryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(Unit) {
        viewModel.removals.collect { removal ->
            val result =
                snackbarHostState.showSnackbar(
                    message = "Removed stale entry “${removal.entry.sceneName}”",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoveStale(removal)
        }
    }
}

@Composable
internal fun ImportConfirmationDialog(
    preview: ImportPreview,
    importing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (preview.recovery) "Copy previous curation?" else "Import registry?")
        },
        text = {
            Text(
                "Into “${preview.origin.key.collectionName}”: ${preview.newCount} new, " +
                    "${preview.updateCount} updated. Other entries in this workspace are kept."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !importing) {
                Text(if (preview.recovery) "Copy" else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        },
    )
}
