package com.scenedeck.android.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.InventoryRow
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val EXPORT_FILE_NAME = "scenedeck-registry.yaml"

/** Inventory page — local scene registry management (FEATURE_SPEC §6). */
@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier,
    viewModel: InventoryViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
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

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/yaml")
        ) { uri ->
            uri?.let { viewModel.transfer.exportTo(it, context.contentResolver) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.transfer.importFrom(it, context.contentResolver) }
        }

    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val importing by viewModel.transfer.importing.collectAsStateWithLifecycle()

    when (val connection = uiState.connection) {
        is ConnectionState.Ready ->
            InventoryContent(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onSetRole = viewModel::setRole,
                onSetAccent = viewModel::setAccent,
                onSetIcon = viewModel::setIcon,
                onRemoveStale = viewModel::removeStale,
                onReorder = viewModel::reorder,
                onBulkAssign = viewModel::bulkAssignUnassigned,
                onExport = { exportLauncher.launch(EXPORT_FILE_NAME) },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                modifier = modifier,
            )

        else ->
            DisconnectedPlaceholder(
                connectionState = connection,
                onConnect = onNavigateToConnections,
            )
    }

    importPreview?.let { preview ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel.transfer::dismissPreview,
            title = { Text("Import registry?") },
            text = {
                Text(
                    "Merge by scene name: ${preview.newCount} new, " +
                        "${preview.updateCount} updated. Existing curation not in " +
                        "the file is kept."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel.transfer::confirmImport, enabled = !importing) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel.transfer::dismissPreview, enabled = !importing) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
internal fun InventoryContent(
    uiState: InventoryUiState,
    snackbarHostState: SnackbarHostState,
    onSetRole: (String, SceneRole) -> Unit,
    onSetAccent: (String, Long?) -> Unit,
    onSetIcon: (String, String?) -> Unit,
    onRemoveStale: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBulkAssign: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var roleMenuFor by remember { mutableStateOf<String?>(null) }
    var accentEditorFor by remember { mutableStateOf<String?>(null) }
    var iconEditorFor by remember { mutableStateOf<String?>(null) }

    var orderedScenes by remember { mutableStateOf(uiState.scenes) }
    val listState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            orderedScenes =
                orderedScenes.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
        }
    LaunchedEffect(uiState.scenes, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) orderedScenes = uiState.scenes
    }
    PersistSceneOrder(reorderableState.isAnyItemDragging, orderedScenes, onReorder)

    val spacing = SceneDeckTheme.spacing
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = spacing.pageGutter)
        ) {
            Spacer(Modifier.height(spacing.sectionGap))
            StudioPageHeader(
                "Inventory",
                "Scene roles, colors & deck order",
                SceneDeckIcons.Inventory,
            )
            Spacer(Modifier.height(spacing.sectionGap))
            InventoryActions(uiState.unassignedCount > 0, onBulkAssign, onExport, onImport)
            Spacer(Modifier.height(spacing.itemGap))
            Text(
                text = "${uiState.scenes.size} scenes · ${uiState.unassignedCount} unassigned",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.itemGap + 4.dp))
            InventoryList(
                scenes = orderedScenes,
                listState = listState,
                reorderableState = reorderableState,
                onIconClick = { iconEditorFor = it },
                onRoleClick = { roleMenuFor = it },
                onAccentClick = { accentEditorFor = it },
                onRemoveStale = onRemoveStale,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp),
        )
    }

    InventorySheets(
        scenes = uiState.scenes,
        targets = SheetTargets(roleMenuFor, accentEditorFor, iconEditorFor),
        onDismiss = {
            roleMenuFor = null
            accentEditorFor = null
            iconEditorFor = null
        },
        onSetRole = onSetRole,
        onSetAccent = onSetAccent,
        onSetIcon = onSetIcon,
    )
}

/** Bulk/transfer actions; wraps onto a second line instead of clipping at the screen edge. */
@Composable
private fun InventoryActions(
    canBulkAssign: Boolean,
    onBulkAssign: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(onClick = onBulkAssign, enabled = canBulkAssign) {
            Text("Unassigned → Secondary")
        }
        OutlinedButton(onClick = onExport) { Text("Export") }
        OutlinedButton(onClick = onImport) { Text("Import") }
    }
}

/**
 * Role chip text. A scene without a chosen role reads "Unassigned" (rendered outlined) even though
 * the deck still treats it as Primary, so it never looks like an explicit Primary assignment.
 */
internal fun inventoryRoleLabel(scene: InventoryScene): String =
    if (!scene.roleAssigned) "Unassigned"
    else scene.role.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun InventoryList(
    scenes: List<InventoryScene>,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    onIconClick: (String) -> Unit,
    onRoleClick: (String) -> Unit,
    onAccentClick: (String) -> Unit,
    onRemoveStale: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
    ) {
        items(scenes, key = { it.name }) { scene ->
            ReorderableItem(reorderableState, key = scene.name) { _ ->
                InventoryRow(
                    name = scene.name,
                    icon = sceneIconForInventory(scene.entry?.iconName),
                    roleLabel = inventoryRoleLabel(scene),
                    roleAssigned = scene.roleAssigned,
                    accentColor = scene.entry?.accentColorArgb?.let { Color(it) },
                    stale = scene.stale,
                    onIconClick = { onIconClick(scene.name) },
                    onRoleClick = { onRoleClick(scene.name) },
                    onAccentClick = { onAccentClick(scene.name) },
                    onRemoveStale = { onRemoveStale(scene.name) },
                    leadingContent = {
                        DragGripDots(
                            sceneName = scene.name,
                            modifier = Modifier.draggableHandle(),
                        )
                    },
                )
            }
        }
    }
}

/** Which scene each editor sheet is open for (null = closed). */
private data class SheetTargets(val role: String?, val accent: String?, val icon: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySheets(
    scenes: List<InventoryScene>,
    targets: SheetTargets,
    onDismiss: () -> Unit,
    onSetRole: (String, SceneRole) -> Unit,
    onSetAccent: (String, Long?) -> Unit,
    onSetIcon: (String, String?) -> Unit,
) {
    targets.role?.let { roleTarget ->
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(roleTarget, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Scene role",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SceneRole.entries.forEach { role ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onSetRole(roleTarget, role)
                            onDismiss()
                        },
                    ) {
                        Text(role.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }
    }

    targets.accent?.let { accentTarget ->
        ModalBottomSheet(onDismissRequest = onDismiss) {
            AccentPickerContent(
                current = scenes.firstOrNull { it.name == accentTarget }?.entry?.accentColorArgb,
                onPick = { argb ->
                    onSetAccent(accentTarget, argb)
                    onDismiss()
                },
            )
        }
    }

    targets.icon?.let { iconTarget ->
        ModalBottomSheet(onDismissRequest = onDismiss) {
            IconPickerContent(
                current = scenes.firstOrNull { it.name == iconTarget }?.entry?.iconName,
                onPick = { iconName ->
                    onSetIcon(iconTarget, iconName)
                    onDismiss()
                },
            )
        }
    }
}

internal fun sceneIconForInventory(iconName: String?) =
    iconName?.let { coroutineResult { SceneIcon.valueOf(it) }.getOrNull() }?.imageVector
        ?: SceneDeckIcons.Scenes

internal fun argbToLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

/**
 * Persists the dragged order once the drag ends. The list is captured as a parameter at composition
 * time so the effect reads the post-drag order even after the reset effect above re-syncs
 * `orderedScenes` from `uiState.scenes`.
 */
@Composable
private fun PersistSceneOrder(
    dragging: Boolean,
    scenes: List<InventoryScene>,
    onReorder: (List<String>) -> Unit,
) {
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (wasDragging && !dragging) onReorder(scenes.map { it.name })
        wasDragging = dragging
    }
}
