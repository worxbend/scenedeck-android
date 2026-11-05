package com.scenedeck.android.feature.inventory

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus

@Composable
internal fun InventoryWorkspaceContent(
    uiState: InventoryUiState,
    viewModel: InventoryViewModel,
    snackbarHostState: SnackbarHostState,
    onLaunchExport: () -> Unit,
    onLaunchImport: () -> Unit,
    modifier: Modifier,
) {
    key(uiState.workspace) {
        val origin = uiState.workspace
        if (origin == null)
            WorkspaceStatus(
                failed = uiState.failed || uiState.workspaceState == WorkspaceState.Failed,
                onRetry = viewModel::retryWorkspace,
                modifier = modifier.statusBarsPadding(),
            )
        else
            InventoryContent(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onSetRole = { name, role -> viewModel.setRole(origin, name, role) },
                onSetAccent = { name, accent -> viewModel.setAccent(origin, name, accent) },
                onSetIcon = { name, icon -> viewModel.setIcon(origin, name, icon) },
                onRemoveStale = { name -> viewModel.removeStale(origin, name) },
                onReorder = { names -> viewModel.reorder(origin, names) },
                onBulkAssign = { viewModel.bulkAssignUnassigned(origin) },
                onExport = {
                    if (viewModel.transfer.beginExport(origin)) onLaunchExport()
                },
                onImport = {
                    if (viewModel.transfer.beginImport(origin)) onLaunchImport()
                },
                onCopyPrevious = { viewModel.transfer.copyPreviousCuration(origin) },
                modifier = modifier,
            )
    }
}
