package com.scenedeck.android.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.components.BroadcastStatus
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.studioFilterChipColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.model.ConnectionState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Search retains the curated order and uses literal, case-insensitive scene names. */
@Composable
fun LiveScreen(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
) {
    val deckState by viewModel.deckState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val pendingScene by viewModel.pendingScene.collectAsStateWithLifecycle()
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val motionLevel by viewModel.motionLevel.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val previewsEnabled by viewModel.previewsEnabled.collectAsStateWithLifecycle()
    val sceneItemsVersion by viewModel.sceneItemsVersion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val connection = deckState.connectionState) {
            is ConnectionState.Ready ->
                LiveDeckContent(
                    deckState = deckState,
                    telemetry = telemetry,
                    pendingScene = pendingScene,
                    hapticsEnabled = hapticsEnabled,
                    motionLevel = motionLevel,
                    thumbnails = thumbnails,
                    onSceneTap = viewModel::onSceneTap,
                    onStreamClick = viewModel::onStreamClick,
                    onRecordClick = viewModel::onRecordClick,
                    onToggleVirtualCam = viewModel::toggleVirtualCam,
                    onQuickEditSave = viewModel::saveSceneMeta,
                    sceneItemsVersion = sceneItemsVersion,
                    onLoadSceneItems = viewModel::loadSceneItems,
                    onToggleSceneItem = viewModel::toggleSceneItem,
                    onReorder = viewModel::reorderDeck,
                    onStudioToggle = viewModel::toggleStudioMode,
                    onTransitionClick = viewModel::onTransitionClick,
                    onCutClick = viewModel::onCutClick,
                    onTransitionSelect = viewModel::selectTransition,
                    onTransitionDurationChange = viewModel::setTransitionDuration,
                    previewsEnabled = previewsEnabled,
                    onPreviewsToggle = viewModel::togglePreviews,
                )

            else ->
                DisconnectedPlaceholder(
                    connectionState = connection,
                    onConnect = onNavigateToConnections,
                )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = {
                Text(
                    when (pending) {
                        TransportConfirmation.START_STREAM ->
                            stringResource(R.string.confirm_start_stream_title)
                        TransportConfirmation.STOP_STREAM ->
                            stringResource(R.string.confirm_stop_stream_title)
                        TransportConfirmation.START_RECORD ->
                            stringResource(R.string.confirm_start_record_title)
                        TransportConfirmation.STOP_RECORD ->
                            stringResource(R.string.confirm_stop_record_title)
                    }
                )
            },
            text = {
                Text(
                    when (pending) {
                        TransportConfirmation.START_STREAM ->
                            stringResource(R.string.confirm_start_stream_body)
                        TransportConfirmation.STOP_STREAM ->
                            stringResource(R.string.confirm_stop_stream_body)
                        TransportConfirmation.START_RECORD ->
                            stringResource(R.string.confirm_start_record_body)
                        TransportConfirmation.STOP_RECORD ->
                            stringResource(R.string.confirm_stop_record_body)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmation) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun LiveDeckContent(
    deckState: DeckState,
    telemetry: Telemetry,
    pendingScene: String?,
    hapticsEnabled: Boolean,
    motionLevel: MotionLevel,
    thumbnails: Map<String, android.graphics.Bitmap>,
    onSceneTap: (String) -> Unit,
    onStreamClick: () -> Unit,
    onRecordClick: () -> Unit,
    onToggleVirtualCam: () -> Unit,
    onQuickEditSave:
        (sceneName: String, primary: Boolean, accentArgb: Long?, iconName: String?) -> Unit,
    modifier: Modifier = Modifier,
    sceneItemsVersion: Int = 0,
    onLoadSceneItems: (suspend (String) -> List<com.scenedeck.android.core.model.SceneItemInfo>)? =
        null,
    onToggleSceneItem: (sceneName: String, itemId: Int, enabled: Boolean) -> Unit = { _, _, _ -> },
    onReorder: (List<String>) -> Unit,
    onStudioToggle: (Boolean) -> Unit,
    onTransitionClick: () -> Unit,
    onCutClick: () -> Unit,
    onTransitionSelect: (String) -> Unit,
    onTransitionDurationChange: (Int) -> Unit,
    previewsEnabled: Boolean,
    onPreviewsToggle: (Boolean) -> Unit,
) {
    var quickEdit by remember { mutableStateOf<SceneCardState?>(null) }

    // Local order while dragging; re-syncs from the deck when not dragging.
    var deckOnly by rememberSaveable { mutableStateOf(false) }
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleScenes =
        filterScenes(if (deckOnly) deckState.scenes else deckState.allScenes, searchQuery)
    var orderedScenes by remember { mutableStateOf(visibleScenes) }
    val gridState = rememberLazyGridState()
    val reorderableState =
        rememberReorderableLazyGridState(gridState) { from, to ->
            orderedScenes =
                orderedScenes.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
        }
    LaunchedEffect(visibleScenes, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) orderedScenes = visibleScenes
    }
    PersistSceneOrder(reorderableState.isAnyItemDragging, orderedScenes, onReorder)

    val toggleSearch = {
        searchOpen = !searchOpen
        if (!searchOpen) searchQuery = ""
    }
    val emptyMessage =
        when {
            searchQuery.isNotBlank() -> R.string.no_matching_scenes
            deckOnly -> R.string.empty_deck
            else -> R.string.empty_scenes
        }
    val streaming = telemetry.stream?.active == true
    val recording = telemetry.record?.active == true

    Column(
        modifier =
            modifier.fillMaxSize().imePadding().statusBarsPadding().padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        DeckHeader(
            deckState,
            reorderMode,
            { reorderMode = !reorderMode },
            previewsEnabled,
            onPreviewsToggle,
            onStudioToggle,
            telemetry,
            motionLevel,
        )
        Spacer(Modifier.height(8.dp))
        TransportBar(
            streaming = streaming,
            recording = recording,
            enabled = true,
            pulseTally = motionLevel == MotionLevel.FULL,
            onToggleStream = onStreamClick,
            onToggleRecord = onRecordClick,
            virtualCamActive = telemetry.virtualCamActive,
            onToggleVirtualCam = onToggleVirtualCam,
        )
        if (deckState.studioMode) {
            Spacer(Modifier.height(8.dp))
            StudioModeBar(
                deckState = deckState,
                onTransitionClick = onTransitionClick,
                onCutClick = onCutClick,
                onTransitionSelect = onTransitionSelect,
                onTransitionDurationChange = onTransitionDurationChange,
                motionLevel = motionLevel,
            )
        }
        DeckFilters(
            deckOnly,
            { deckOnly = it },
            deckState.allScenes.size,
            searchOpen,
            toggleSearch,
            reorderMode,
            { reorderMode = false },
        )
        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.search_scenes)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = MaterialTheme.shapes.large,
            )
        }
        if (orderedScenes.isEmpty()) {
            Text(
                text = stringResource(emptyMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }

        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(orderedScenes, key = { it.name }) { scene ->
                ReorderableItem(reorderableState, key = scene.name) { isDragging ->
                    DeckSceneTile(
                        scene,
                        orderedScenes.indexOf(scene) + 1,
                        isDragging,
                        reorderMode,
                        searchQuery.isBlank(),
                        motionLevel,
                        deckState.studioMode,
                        thumbnails[scene.name],
                        pendingScene,
                        onSceneTap,
                        { quickEdit = it },
                        hapticsEnabled,
                        Modifier.draggableHandle(),
                    )
                }
            }
        }
    }

    quickEdit?.let { scene ->
        QuickEditSheet(
            scene = scene,
            onDismiss = { quickEdit = null },
            onSave = { primary, accentArgb, iconName ->
                onQuickEditSave(scene.name, primary, accentArgb, iconName)
                quickEdit = null
            },
            sceneItemsVersion = sceneItemsVersion,
            onLoadSceneItems = onLoadSceneItems?.let { loader -> { loader(scene.name) } },
            onToggleSceneItem = { itemId, enabled ->
                onToggleSceneItem(scene.name, itemId, enabled)
            },
        )
    }
}

/** Grip dots marking the drag area (kept off the card so taps/long-press stay free). */
@Composable
private fun DragGrip(sceneName: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier =
            modifier.size(48.dp).semantics {
                contentDescription = "Drag to reorder scene $sceneName"
            }
    ) {
        val radius = 1.6.dp.toPx()
        val stepX = 6.dp.toPx()
        val stepY = 6.dp.toPx()
        for (row in 0..2) {
            for (col in 0..1) {
                drawCircle(
                    color = tint,
                    radius = radius,
                    center =
                        Offset(
                            x = size.width / 2 - stepX / 2 + col * stepX,
                            y = size.height / 2 - stepY + row * stepY,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DeckHeader(
    deckState: DeckState,
    reorderMode: Boolean,
    onReorderToggle: () -> Unit,
    previewsEnabled: Boolean,
    onPreviewsToggle: (Boolean) -> Unit,
    onStudioToggle: (Boolean) -> Unit,
    telemetry: Telemetry,
    motionLevel: MotionLevel,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    val reorderLabel = if (reorderMode) R.string.finish_reordering else R.string.reorder_scenes
    val previewsLabel = if (previewsEnabled) R.string.hide_previews else R.string.show_previews
    val studioLabel = if (deckState.studioMode) R.string.disable_studio else R.string.enable_studio
    StudioPageHeader(
        title = stringResource(R.string.live_title),
        subtitle =
            deckState.currentProgramScene?.let {
                stringResource(R.string.program_scene, it)
            } ?: stringResource(R.string.scene_switch_hint),
        icon = SceneDeckIcons.Scenes,
    ) {
        HeaderBroadcastStatus(telemetry, motionLevel)
        Box {
            IconButton(onClick = { optionsOpen = true }) {
                Icon(SceneDeckIcons.More, stringResource(R.string.scene_options))
            }
            DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(reorderLabel)) },
                    onClick = {
                        onReorderToggle()
                        optionsOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(previewsLabel)) },
                    onClick = {
                        onPreviewsToggle(!previewsEnabled)
                        optionsOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(studioLabel)) },
                    onClick = {
                        onStudioToggle(!deckState.studioMode)
                        optionsOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DeckSceneTile(
    scene: SceneCardState,
    sceneNumber: Int,
    isDragging: Boolean,
    reorderMode: Boolean,
    searchBlank: Boolean,
    motionLevel: MotionLevel,
    studioMode: Boolean,
    thumbnail: android.graphics.Bitmap?,
    pendingScene: String?,
    onSceneTap: (String) -> Unit,
    onQuickEdit: (SceneCardState) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val position = if (reorderMode) null else sceneNumber
    val dragScale = if (isDragging) 1.04f else 1f
    val dragAlpha = if (isDragging) 0.9f else 1f
    val tapScene = {
        if (!reorderMode) {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSceneTap(scene.name)
        }
    }
    Box(
        modifier =
            Modifier.graphicsLayer {
                val s = dragScale
                scaleX = s
                scaleY = s
                alpha = dragAlpha
            }
    ) {
        SceneCard(
            label = scene.name,
            sceneNumber = position,
            motionLevel = motionLevel,
            studioMode = studioMode,
            icon = sceneIconFor(scene.iconName),
            active = scene.isActive,
            preview = scene.isPreview,
            accentColor = scene.accentColorArgb?.let { Color(it) },
            thumbnail = thumbnail?.asImageBitmap(),
            pending = pendingScene == scene.name,
            onClick = tapScene,
            onLongClick = { onQuickEdit(scene) },
        )
        // Keep the reorder handle available before a drag begins.
        androidx.compose.animation.AnimatedVisibility(
            visible = reorderMode && searchBlank,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            DragGrip(
                sceneName = scene.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(10.dp).then(modifier),
            )
        }
    }
}

@Composable
private fun DeckFilters(
    deckOnly: Boolean,
    onDeckOnlyChange: (Boolean) -> Unit,
    sceneCount: Int,
    searchOpen: Boolean,
    toggleSearch: () -> Unit,
    reorderMode: Boolean,
    finishReordering: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            colors = studioFilterChipColors(),
            border = null,
            selected = !deckOnly,
            onClick = { onDeckOnlyChange(false) },
            label = { Text(stringResource(R.string.all_scenes, sceneCount)) },
        )
        FilterChip(
            colors = studioFilterChipColors(),
            border = null,
            selected = deckOnly,
            onClick = { onDeckOnlyChange(true) },
            label = { Text(stringResource(R.string.deck_filter)) },
        )
        TextButton(onClick = toggleSearch) {
            Text(stringResource(if (searchOpen) R.string.close_search else R.string.find_scene))
        }
    }
    if (reorderMode) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reorder_hint),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { finishReordering() }) {
                Text(stringResource(R.string.finish_reordering))
            }
        }
    }
}

private fun sceneNames(scenes: List<SceneCardState>): List<String> = scenes.map { it.name }

@Composable
private fun PersistSceneOrder(
    dragging: Boolean,
    scenes: List<SceneCardState>,
    onReorder: (List<String>) -> Unit,
) {
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (wasDragging && !dragging) onReorder(sceneNames(scenes))
        wasDragging = dragging
    }
}

@Composable
private fun HeaderBroadcastStatus(telemetry: Telemetry, motionLevel: MotionLevel) {
    BroadcastStatus(
        streaming = telemetry.stream?.active == true,
        recording = telemetry.record?.active == true,
        streamElapsed = telemetry.stream?.timecode?.take(8) ?: "00:00:00",
        recordElapsed = telemetry.record?.timecode?.take(8) ?: "00:00:00",
        pulse = motionLevel == MotionLevel.FULL,
    )
}
