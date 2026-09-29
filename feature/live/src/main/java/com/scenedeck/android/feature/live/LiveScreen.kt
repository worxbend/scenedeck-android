package com.scenedeck.android.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import androidx.compose.ui.graphics.asImageBitmap
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.model.ConnectionState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

internal fun sceneIconFor(iconName: String?): ImageVector =
    iconName
        ?.let { runCatching { SceneIcon.valueOf(it) }.getOrNull() }
        ?.imageVector
        ?: SceneDeckIcons.Scenes

/**
 * Live page — the hero deck (FEATURE_SPEC §2, milestone M3). Adaptive grid of primary
 * scene cards (tap = program, long-press = quick edit, grip = drag reorder), wired
 * TransportBar, Output Safety confirmations, designed offline states.
 */
@Composable
fun LiveScreen(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
    onNavigateToMixer: () -> Unit = {},
) {
    val deckState by viewModel.deckState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val pendingScene by viewModel.pendingScene.collectAsStateWithLifecycle()
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val mixerState by viewModel.mixerState.collectAsStateWithLifecycle()
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
            is ConnectionState.Ready -> LiveDeckContent(
                deckState = deckState,
                telemetry = telemetry,
                pendingScene = pendingScene,
                hapticsEnabled = hapticsEnabled,
                mixerState = mixerState,
                mixerLevels = viewModel.mixerLevels,
                motionLevel = motionLevel,
                thumbnails = thumbnails,
                onSceneTap = viewModel::onSceneTap,
                onStreamClick = viewModel::onStreamClick,
                onRecordClick = viewModel::onRecordClick,
                onToggleVirtualCam = viewModel::toggleVirtualCam,
                onToggleReplayBuffer = viewModel::toggleReplayBuffer,
                onSaveReplay = viewModel::saveReplayBuffer,
                onQuickEditSave = viewModel::saveSceneMeta,
                sceneItemsVersion = sceneItemsVersion,
                onLoadSceneItems = viewModel::loadSceneItems,
                onToggleSceneItem = viewModel::toggleSceneItem,
                onReorder = viewModel::reorderDeck,
                onMixerMute = viewModel::toggleMixerMute,
                onOpenMixer = onNavigateToMixer,
                onStudioToggle = viewModel::toggleStudioMode,
                onTransitionClick = viewModel::onTransitionClick,
                onCutClick = viewModel::onCutClick,
                onTransitionSelect = viewModel::selectTransition,
                onTransitionDurationChange = viewModel::setTransitionDuration,
                previewsEnabled = previewsEnabled,
                onPreviewsToggle = viewModel::togglePreviews,
            )

            else -> DisconnectedPlaceholder(
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
                        TransportConfirmation.START_STREAM -> stringResource(R.string.confirm_start_stream_title)
                        TransportConfirmation.STOP_STREAM -> stringResource(R.string.confirm_stop_stream_title)
                        TransportConfirmation.START_RECORD -> stringResource(R.string.confirm_start_record_title)
                        TransportConfirmation.STOP_RECORD -> stringResource(R.string.confirm_stop_record_title)
                    },
                )
            },
            text = {
                Text(
                    when (pending) {
                        TransportConfirmation.START_STREAM -> stringResource(R.string.confirm_start_stream_body)
                        TransportConfirmation.STOP_STREAM -> stringResource(R.string.confirm_stop_stream_body)
                        TransportConfirmation.START_RECORD -> stringResource(R.string.confirm_start_record_body)
                        TransportConfirmation.STOP_RECORD -> stringResource(R.string.confirm_stop_record_body)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(R.string.action_cancel)) }
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
    mixerState: com.scenedeck.android.core.data.MixerState,
    mixerLevels: com.scenedeck.android.core.designsystem.components.MeterLevelsStore,
    motionLevel: MotionLevel,
    thumbnails: Map<String, android.graphics.Bitmap>,
    onSceneTap: (String) -> Unit,
    onStreamClick: () -> Unit,
    onRecordClick: () -> Unit,
    onToggleVirtualCam: () -> Unit,
    onToggleReplayBuffer: () -> Unit,
    onSaveReplay: () -> Unit,
    onQuickEditSave: (sceneName: String, primary: Boolean, accentArgb: Long?, iconName: String?) -> Unit,
    sceneItemsVersion: Int = 0,
    onLoadSceneItems: (suspend (String) -> List<com.scenedeck.android.core.model.SceneItemInfo>)? = null,
    onToggleSceneItem: (sceneName: String, itemId: Int, enabled: Boolean) -> Unit = { _, _, _ -> },
    onReorder: (List<String>) -> Unit,
    onMixerMute: (String, Boolean) -> Unit,
    onOpenMixer: () -> Unit,
    onStudioToggle: (Boolean) -> Unit,
    onTransitionClick: () -> Unit,
    onCutClick: () -> Unit,
    onTransitionSelect: (String) -> Unit,
    onTransitionDurationChange: (Int) -> Unit,
    previewsEnabled: Boolean,
    onPreviewsToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var quickEdit by remember { mutableStateOf<SceneCardState?>(null) }

    // Local order while dragging; re-syncs from the deck when not dragging.
    var orderedScenes by remember { mutableStateOf(deckState.scenes) }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        orderedScenes = orderedScenes.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }
    LaunchedEffect(deckState.scenes, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) orderedScenes = deckState.scenes
    }
    // Persist only after an actual drag ends (not on first composition).
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (wasDragging && !reorderableState.isAnyItemDragging) {
            onReorder(orderedScenes.map { it.name })
        }
        wasDragging = reorderableState.isAnyItemDragging
    }

    val streaming = telemetry.stream?.active == true
    val recording = telemetry.record?.active == true
    val elapsed = when {
        recording -> telemetry.record?.timecode
        streaming -> telemetry.stream?.timecode
        else -> null
    }?.take(8) ?: "00:00:00"

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.live_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = previewsEnabled,
                onClick = { onPreviewsToggle(!previewsEnabled) },
                label = { Text(stringResource(R.string.previews_toggle)) },
            )
            Spacer(Modifier.size(8.dp))
            FilterChip(
                selected = deckState.studioMode,
                onClick = { onStudioToggle(!deckState.studioMode) },
                label = { Text(stringResource(R.string.studio_toggle)) },
            )
        }
        Spacer(Modifier.height(16.dp))
        TransportBar(
            streaming = streaming,
            recording = recording,
            elapsedTime = elapsed,
            enabled = true,
            pulseTally = true,
            onToggleStream = onStreamClick,
            onToggleRecord = onRecordClick,
            virtualCamActive = telemetry.virtualCamActive,
            replayBufferActive = telemetry.replayBufferActive,
            onToggleVirtualCam = onToggleVirtualCam,
            onToggleReplayBuffer = onToggleReplayBuffer,
            onSaveReplay = onSaveReplay,
        )
        if (deckState.studioMode) {
            Spacer(Modifier.height(12.dp))
            StudioModeBar(
                deckState = deckState,
                onTransitionClick = onTransitionClick,
                onCutClick = onCutClick,
                onTransitionSelect = onTransitionSelect,
                onTransitionDurationChange = onTransitionDurationChange,
                motionLevel = motionLevel,
            )
        }
        Spacer(Modifier.height(12.dp))
        EmbeddedMixerRow(
            mixerState = mixerState,
            levelsStore = mixerLevels,
            motionLevel = motionLevel,
            onToggleMute = onMixerMute,
            onOpenMixer = onOpenMixer,
        )
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(orderedScenes, key = { it.name }) { scene ->
                ReorderableItem(reorderableState, key = scene.name) { isDragging ->
                    Box(
                        modifier = Modifier.graphicsLayer {
                            val s = if (isDragging) 1.04f else 1f
                            scaleX = s
                            scaleY = s
                            alpha = if (isDragging) 0.9f else 1f
                        },
                    ) {
                        SceneCard(
                            label = scene.name,
                            icon = sceneIconFor(scene.iconName),
                            active = scene.isActive,
                            preview = scene.isPreview,
                            accentColor = scene.accentColorArgb?.let { Color(it) },
                            thumbnail = thumbnails[scene.name]?.asImageBitmap(),
                            pending = pendingScene == scene.name,
                            onClick = {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                }
                                onSceneTap(scene.name)
                            },
                            onLongClick = { quickEdit = scene },
                            modifier = Modifier.padding(6.dp),
                        )
                        DragGrip(
                            sceneName = scene.name,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .draggableHandle(),
                        )
                    }
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
            onToggleSceneItem = { itemId, enabled -> onToggleSceneItem(scene.name, itemId, enabled) },
        )
    }
}

/** Grip dots marking the drag area (kept off the card so taps/long-press stay free). */
@Composable
private fun DragGrip(sceneName: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = 12.dp, height = 18.dp)
            .semantics { contentDescription = "Drag to reorder scene $sceneName" },
    ) {
        val radius = 1.6.dp.toPx()
        val stepX = size.width - 2 * radius
        val stepY = (size.height - 2 * radius) / 2
        for (row in 0..2) {
            for (col in 0..1) {
                drawCircle(
                    color = tint,
                    radius = radius,
                    center = Offset(
                        x = radius + col * stepX,
                        y = radius + row * stepY,
                    ),
                )
            }
        }
    }
}
