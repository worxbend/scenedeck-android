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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.scenedeck.android.core.data.DeckSelection
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.NamedDeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneMetadataEdit
import com.scenedeck.android.core.data.SceneThumbnail
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.components.BroadcastStatus
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSnackbarHost
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus
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
    namedDecks: NamedDeckViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
    onNavigateToPreflight: () -> Unit = {},
) {
    val deckState by viewModel.deckState.collectAsStateWithLifecycle()
    val namedState by namedDecks.decks.collectAsStateWithLifecycle()
    val options =
        (namedState as? NamedDeckState.Ready)?.takeIf { it.origin == deckState.workspace }?.snapshot
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val pendingScene by viewModel.pendingScene.collectAsStateWithLifecycle()
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val motionLevel by viewModel.motionLevel.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val previewTimeMs by viewModel.previewTimeMs.collectAsStateWithLifecycle()
    val previewsEnabled by viewModel.previewsEnabled.collectAsStateWithLifecycle()
    val sceneItemsVersion by viewModel.sceneItemsVersion.collectAsStateWithLifecycle()
    val virtualCamPending by viewModel.virtualCamPending.collectAsStateWithLifecycle()
    var recordingToolsOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(namedDecks) {
        namedDecks.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val connection = deckState.connectionState) {
            is ConnectionState.Ready ->
                key(deckState.workspace, options?.selection) {
                    val origin = deckState.workspace
                    LiveDeckContent(
                        deckState = deckState,
                        deckOptions = options,
                        deckOptionsFailed = namedState is NamedDeckState.Failed,
                        onDeckSelection = { selection ->
                            origin?.let { namedDecks.select(it, selection) }
                        },
                        onManageDecks = { origin?.let(namedDecks::open) },
                        telemetry = telemetry,
                        pendingScene = pendingScene,
                        hapticsEnabled = hapticsEnabled,
                        motionLevel = motionLevel,
                        thumbnails = thumbnails.takeIf { it.workspace == origin }?.frames.orEmpty(),
                        thumbnailFailures =
                            thumbnails.takeIf { it.workspace == origin }?.failedScenes.orEmpty(),
                        previewTimeMs = previewTimeMs,
                        onSceneTap = { name -> origin?.let { viewModel.onSceneTap(it, name) } },
                        onStreamClick = viewModel::onStreamClick,
                        onRecordClick = viewModel::onRecordClick,
                        onRecordingTools = { recordingToolsOpen = true },
                        onToggleVirtualCam = viewModel::toggleVirtualCam,
                        onQuickEditSave = { name, edit ->
                            origin?.let { viewModel.saveSceneMeta(it, name, edit) } ?: false
                        },
                        sceneItemsVersion = sceneItemsVersion,
                        onLoadSceneItems = { name ->
                            if (origin == null) emptyList()
                            else viewModel.loadSceneItems(origin, name)
                        },
                        onToggleSceneItem = { name, id, enabled ->
                            origin?.let { viewModel.toggleSceneItem(it, name, id, enabled) }
                        },
                        onReorder = { names ->
                            reorderSelectedDeck(viewModel, namedDecks, origin, options, names)
                        },
                        onStudioToggle = { enabled ->
                            origin?.let { viewModel.toggleStudioMode(it, enabled) }
                        },
                        onTransitionClick = { origin?.let(viewModel::onTransitionClick) },
                        onCutClick = { origin?.let(viewModel::onCutClick) },
                        onTransitionSelect = { name ->
                            origin?.let { viewModel.selectTransition(it, name) }
                        },
                        onTransitionDurationChange = { duration ->
                            origin?.let { viewModel.setTransitionDuration(it, duration) }
                        },
                        previewsEnabled = previewsEnabled,
                        onPreviewsToggle = viewModel::togglePreviews,
                        virtualCamPending = virtualCamPending,
                        onRetryWorkspace = {
                            viewModel.retryWorkspace()
                            namedDecks.retry()
                        },
                    )
                }

            else ->
                DisconnectedPlaceholder(
                    connectionState = connection,
                    onConnect = onNavigateToConnections,
                    extraDetail =
                        if (previewsEnabled)
                            "Previews disconnected. Connect to refresh scene images."
                        else null,
                )
        }

        StudioSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    RecordingToolsOverlay(recordingToolsOpen, hapticsEnabled, { recordingToolsOpen = false })

    NamedDeckManagement(namedDecks, deckState)

    confirmation?.let {
        TransportConfirmationDialog(
            it,
            viewModel::confirmPending,
            viewModel::dismissConfirmation,
            onPreflight = {
                viewModel.dismissConfirmation()
                onNavigateToPreflight()
            },
        )
    }
}

@Composable
internal fun LiveDeckContent(
    deckState: DeckState,
    deckOptions: NamedDeckSnapshot?,
    telemetry: Telemetry,
    pendingScene: String?,
    hapticsEnabled: Boolean,
    motionLevel: MotionLevel,
    thumbnails: Map<String, SceneThumbnail>,
    onSceneTap: (String) -> Unit,
    onStreamClick: () -> Unit,
    onRecordClick: () -> Unit,
    onToggleVirtualCam: () -> Unit,
    onQuickEditSave: suspend (sceneName: String, edit: SceneMetadataEdit) -> Boolean,
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
    virtualCamPending: VirtualCamPending? = null,
    onRetryWorkspace: () -> Unit = {},
    onDeckSelection: (DeckSelection) -> Unit = {},
    onManageDecks: () -> Unit = {},
    deckOptionsFailed: Boolean = false,
    thumbnailFailures: Set<String> = emptySet(),
    previewTimeMs: Long = 0L,
    onRecordingTools: () -> Unit = {},
) {
    var quickEdit by remember { mutableStateOf<SceneCardState?>(null) }

    // Local order while dragging; re-syncs from the deck when not dragging.
    val previews =
        ScenePreviews(
            thumbnails,
            thumbnailFailures,
            previewTimeMs,
            previewsEnabled,
            deckState.previewConnected,
        )
    val selection = deckOptions?.selection ?: DeckSelection.All
    val projection = projectNamedDeck(deckState, deckOptions)
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    var tBarOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val reorderAllowed = deckOptions != null && projection.allowsReorder(selection, searchQuery)
    val positions = projection.positions
    val visibleScenes = filterScenes(projection.scenes, searchQuery)
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

    val streaming = telemetry.stream?.active == true
    val recording = telemetry.record?.active == true
    val showReorderGrips = reorderMode && reorderAllowed
    val knownEmpty = deckState.workspace != null && deckOptions != null && orderedScenes.isEmpty()

    Column(
        modifier =
            modifier.fillMaxSize().imePadding().statusBarsPadding().padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        DeckHeader(
            deckState,
            reorderMode,
            { reorderMode = !reorderMode },
            reorderAllowed,
            previewsEnabled,
            onPreviewsToggle,
            onStudioToggle,
            telemetry,
            motionLevel,
            tBarOpen,
            { tBarOpen = !tBarOpen },
            onRecordingTools,
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
            virtualCamPending = virtualCamPending,
        )
        StudioModeSection(
            deckState,
            onTransitionClick,
            onCutClick,
            onTransitionSelect,
            onTransitionDurationChange,
            motionLevel,
            tBarOpen,
        )
        DeckFilterSection(
            deckOptions = deckOptions,
            onDeckSelection = {
                onDeckSelection(it)
                reorderMode = false
            },
            onManageDecks = onManageDecks,
            sceneCount = deckState.allScenes.size,
            searchOpen = searchOpen,
            onSearchToggle = {
                searchOpen = !searchOpen
                if (!searchOpen) searchQuery = ""
            },
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            reorderMode = reorderMode,
            onFinishReordering = { reorderMode = false },
            listEmpty = knownEmpty,
        )

        DeckWorkspaceStatus(deckState, onRetryWorkspace)
        if (deckState.workspace != null && deckOptions == null)
            WorkspaceStatus(failed = deckOptionsFailed, onRetry = onRetryWorkspace)
        if (projection.missing.isNotEmpty())
            TextButton(onClick = onManageDecks) {
                Text("${projection.missing.size} scenes unavailable · Manage deck")
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
                        positions[scene.name] ?: (orderedScenes.indexOf(scene) + 1),
                        isDragging,
                        showReorderGrips,
                        searchQuery.isBlank(),
                        motionLevel,
                        deckState.studioMode,
                        previews.image(scene.name),
                        previews.label(scene.name),
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
        DeckQuickEdit(
            scene,
            { quickEdit = null },
            onQuickEditSave,
            sceneItemsVersion,
            onLoadSceneItems,
            onToggleSceneItem,
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
    reorderAllowed: Boolean,
    previewsEnabled: Boolean,
    onPreviewsToggle: (Boolean) -> Unit,
    onStudioToggle: (Boolean) -> Unit,
    telemetry: Telemetry,
    motionLevel: MotionLevel,
    tBarOpen: Boolean,
    onTBarToggle: () -> Unit,
    onRecordingTools: () -> Unit,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    val reorderLabel = if (reorderMode) R.string.finish_reordering else R.string.reorder_scenes
    val previewsLabel = if (previewsEnabled) R.string.hide_previews else R.string.show_previews
    val studioLabel = if (deckState.studioMode) R.string.disable_studio else R.string.enable_studio
    val tBarLabel = if (tBarOpen) R.string.hide_tbar else R.string.show_tbar
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
                    text = { Text("Recording tools") },
                    onClick = {
                        optionsOpen = false
                        onRecordingTools()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(reorderLabel)) },
                    onClick = {
                        onReorderToggle()
                        optionsOpen = false
                    },
                    enabled = reorderAllowed,
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
                if (deckState.studioMode) {
                    DropdownMenuItem(
                        text = { Text(stringResource(tBarLabel)) },
                        onClick = {
                            onTBarToggle()
                            optionsOpen = false
                        },
                    )
                }
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
    previewStatus: String?,
    pendingScene: String?,
    onSceneTap: (String) -> Unit,
    onQuickEdit: (SceneCardState) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Reorder mode shows grips instead of numbers (positions change while dragging).
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
            label = scene.displayName,
            obsName = scene.name.takeIf { it != scene.displayName },
            sceneNumber = position,
            motionLevel = motionLevel,
            studioMode = studioMode,
            reordering = reorderMode,
            icon = sceneIconFor(scene.iconName),
            active = scene.isActive,
            preview = scene.isPreview,
            accentColor = scene.accentColorArgb?.let { Color(it) },
            thumbnail = thumbnail?.asImageBitmap(),
            previewStatus = previewStatus,
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

/** Deck filter chips, the search field, and the empty-list hint (kept out of LiveDeckContent). */
@Composable
private fun DeckFilterSection(
    deckOptions: NamedDeckSnapshot?,
    onDeckSelection: (DeckSelection) -> Unit,
    onManageDecks: () -> Unit,
    sceneCount: Int,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    reorderMode: Boolean,
    onFinishReordering: () -> Unit,
    listEmpty: Boolean,
) {
    // Focus only when the user opens search, not when saved state restores it open.
    var focusOnOpen by remember { mutableStateOf(false) }
    DeckFilters(
        deckOptions,
        onDeckSelection,
        onManageDecks,
        sceneCount,
        searchOpen,
        {
            focusOnOpen = !searchOpen
            onSearchToggle()
        },
        reorderMode,
        onFinishReordering,
    )
    if (searchOpen) SceneSearchField(searchQuery, onSearchChange, focusOnOpen)
    if (listEmpty) {
        val emptyMessage =
            when {
                searchQuery.isNotBlank() -> R.string.no_matching_scenes
                deckOptions?.selection == DeckSelection.Primary -> R.string.empty_deck
                deckOptions?.selection is DeckSelection.Named -> R.string.empty_named_deck
                else -> R.string.empty_scenes
            }
        Text(
            text = stringResource(emptyMessage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun DeckFilters(
    deckOptions: NamedDeckSnapshot?,
    onDeckSelection: (DeckSelection) -> Unit,
    onManageDecks: () -> Unit,
    sceneCount: Int,
    searchOpen: Boolean,
    toggleSearch: () -> Unit,
    reorderMode: Boolean,
    finishReordering: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                colors = studioFilterChipColors(),
                border = null,
                selected = deckOptions?.selection == DeckSelection.All,
                enabled = deckOptions != null,
                onClick = { onDeckSelection(DeckSelection.All) },
                label = { Text(stringResource(R.string.all_scenes, sceneCount)) },
            )
            FilterChip(
                colors = studioFilterChipColors(),
                border = null,
                selected = deckOptions?.selection == DeckSelection.Primary,
                enabled = deckOptions != null,
                onClick = { onDeckSelection(DeckSelection.Primary) },
                label = { Text(stringResource(R.string.deck_filter)) },
            )
            NamedDeckSelector(deckOptions, onDeckSelection, onManageDecks)
        }
        IconButton(onClick = toggleSearch) {
            Icon(
                SceneDeckIcons.Search,
                stringResource(if (searchOpen) R.string.close_search else R.string.find_scene),
            )
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
    var persist by remember { mutableStateOf(onReorder) }
    LaunchedEffect(dragging) {
        if (dragging) persist = onReorder
        if (wasDragging && !dragging) persist(sceneNames(scenes))
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
