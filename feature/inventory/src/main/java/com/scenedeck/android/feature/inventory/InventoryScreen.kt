package com.scenedeck.android.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.scenedeck.android.core.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/yaml")
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    coroutineResult {
                        val yaml = viewModel.exportRegistry()
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(yaml.toByteArray())
                            } ?: error("cannot open target")
                        }
                    }
                        .onSuccess { snackbarHostState.showSnackbar("Registry exported") }
                        .onFailure {
                            snackbarHostState.showSnackbar("Export failed: ${it.message}")
                        }
                }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    coroutineResult {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.readBytes().decodeToString()
                                } ?: error("Cannot open registry")
                            }
                        }
                        .onSuccess(viewModel::stageImport)
                        .onFailure {
                            snackbarHostState.showSnackbar("Import failed: ${it.message}")
                        }
                }
            }
        }

    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()

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
            onDismissRequest = viewModel::dismissImportPreview,
            title = { Text("Import registry?") },
            text = {
                Text(
                    "Merge by scene name: ${preview.newCount} new, " +
                        "${preview.updateCount} updated. Existing curation not in " +
                        "the file is kept."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImportPreview) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (wasDragging && !reorderableState.isAnyItemDragging) {
            onReorder(orderedScenes.map { it.name })
        }
        wasDragging = reorderableState.isAnyItemDragging
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        StudioPageHeader("Inventory", "Scene roles, colors & deck order", SceneDeckIcons.Inventory)
        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            OutlinedButton(onClick = onBulkAssign, enabled = uiState.unassignedCount > 0) {
                Text("Unassigned → Secondary")
            }
            OutlinedButton(onClick = onExport) { Text("Export") }
            OutlinedButton(onClick = onImport) { Text("Import") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${uiState.scenes.size} scenes · ${uiState.unassignedCount} unassigned",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(orderedScenes, key = { it.name }) { scene ->
                ReorderableItem(reorderableState, key = scene.name) { _ ->
                    InventoryRow(
                        name = scene.name,
                        icon = sceneIconForInventory(scene.entry?.iconName),
                        roleLabel = scene.role.name.lowercase().replaceFirstChar { it.uppercase() },
                        accentColor = scene.entry?.accentColorArgb?.let { Color(it) },
                        stale = scene.stale,
                        onIconClick = { iconEditorFor = scene.name },
                        onRoleClick = { roleMenuFor = scene.name },
                        onAccentClick = { accentEditorFor = scene.name },
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

    SnackbarHost(hostState = snackbarHostState)

    // Role menu.
    val roleTarget = roleMenuFor
    if (roleTarget != null) {
        ModalBottomSheet(onDismissRequest = { roleMenuFor = null }) {
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
                            roleMenuFor = null
                        },
                    ) {
                        Text(role.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }
    }

    // Accent sheet.
    val accentTarget = accentEditorFor
    if (accentTarget != null) {
        ModalBottomSheet(onDismissRequest = { accentEditorFor = null }) {
            AccentPickerContent(
                current =
                    uiState.scenes.firstOrNull { it.name == accentTarget }?.entry?.accentColorArgb,
                onPick = { argb ->
                    onSetAccent(accentTarget, argb)
                    accentEditorFor = null
                },
            )
        }
    }

    // Icon sheet.
    val iconTarget = iconEditorFor
    if (iconTarget != null) {
        ModalBottomSheet(onDismissRequest = { iconEditorFor = null }) {
            IconPickerContent(
                current = uiState.scenes.firstOrNull { it.name == iconTarget }?.entry?.iconName,
                onPick = { iconName ->
                    onSetIcon(iconTarget, iconName)
                    iconEditorFor = null
                },
            )
        }
    }
}

internal fun sceneIconForInventory(iconName: String?) =
    iconName?.let { coroutineResult { SceneIcon.valueOf(it) }.getOrNull() }?.imageVector
        ?: SceneDeckIcons.Scenes

internal fun argbToLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
