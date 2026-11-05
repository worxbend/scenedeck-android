package com.scenedeck.android.feature.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.data.MixerMediaStatus
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSnackbarHost
import com.scenedeck.android.core.designsystem.components.StudioWorkspace
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus
import com.scenedeck.android.core.designsystem.components.studioFilterChipColors
import com.scenedeck.android.core.designsystem.components.studioSegmentedButtonColors
import com.scenedeck.android.core.designsystem.components.studioTextFieldColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.model.ObsVersionInfo

/** Full mixer page (FEATURE_SPEC §3, milestone M4). */
@Composable
fun MixerScreen(
    modifier: Modifier = Modifier,
    viewModel: MixerViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
    motionLevel: MotionLevel = MotionLevel.FULL,
    hapticsEnabled: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaStatus by viewModel.mediaStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }
    Box(modifier.fillMaxSize()) {
        when (val connection = uiState.connection) {
            is ConnectionState.Ready ->
                key(uiState.workspace) {
                    val origin = uiState.workspace
                    if (origin == null) {
                        WorkspaceStatus(
                            failed = uiState.failed,
                            onRetry = viewModel::retryWorkspace,
                            modifier = Modifier.statusBarsPadding().padding(20.dp),
                        )
                    } else {
                        ScopedMixerContent(
                            uiState,
                            origin,
                            viewModel,
                            motionLevel,
                            hapticsEnabled,
                            if (mediaStatus.workspace == origin) mediaStatus
                            else MixerMediaStatus(),
                        )
                    }
                }

            else ->
                DisconnectedPlaceholder(
                    connectionState = connection,
                    onConnect = onNavigateToConnections,
                )
        }

        StudioSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScopedMixerContent(
    uiState: MixerUiState,
    origin: WorkspaceSession,
    viewModel: MixerViewModel,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    mediaStatus: MixerMediaStatus,
) {
    val haptics = LocalHapticFeedback.current
    var mediaFor by remember { mutableStateOf<String?>(null) }
    var extrasFor by remember { mutableStateOf<String?>(null) }
    MixerContent(
        uiState = uiState,
        levelsStore = viewModel.levelsStore,
        motionLevel = motionLevel,
        hapticsEnabled = hapticsEnabled,
        callbacks = viewModel.callbacks(origin),
        mediaStatus = mediaStatus.inputs,
        seeking = mediaStatus.readings.filterValues { it.seeking }.keys,
        onMediaClick = { mediaFor = it },
        onExtrasClick = { extrasFor = it },
    )
    mediaFor?.let { inputName ->
        DisposableEffect(origin, inputName) {
            val lease = viewModel.watchMediaInput(origin, inputName)
            onDispose { lease.close() }
        }
        MediaToolsSheet(
            inputName,
            mediaStatus.readings[inputName],
            mediaStatus.seekSupported,
            inputName in uiState.lockedInputs,
            { intent ->
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                viewModel.seekMedia(intent)
            },
            { mediaFor = null },
        )
    }
    extrasFor?.let { inputName ->
        AudioExtrasSheet(
            inputName = inputName,
            loadExtras = { viewModel.loadAudioExtras(origin, inputName) },
            onBalanceChange = { viewModel.setAudioBalance(origin, inputName, it) },
            onSyncOffsetChange = { viewModel.setAudioSyncOffset(origin, inputName, it) },
            onMonitorTypeChange = { viewModel.setAudioMonitorType(origin, inputName, it) },
            onDismiss = { extrasFor = null },
        )
    }
}

@Composable
internal fun MixerContent(
    uiState: MixerUiState,
    levelsStore: MeterLevelsStore,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    callbacks: MixerCallbacks,
    modifier: Modifier = Modifier,
    mediaStatus: Map<String, MediaStatus> = emptyMap(),
    onExtrasClick: (String) -> Unit = {},
    onMediaClick: (String) -> Unit = {},
    seeking: Set<String> = emptySet(),
) {
    StudioWorkspace(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = SceneDeckTheme.spacing.pageGutter),
        controls = { _ ->
            Spacer(Modifier.height(16.dp))
            StudioPageHeader(
                title = "Audio mixer",
                subtitle = uiState.displayedScene ?: "Pinned channels",
                icon = SceneDeckIcons.Mixer,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "${uiState.inputs.size} channels",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            MixerModeSelector(uiState.mode, callbacks.onModeChange)
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    colors = studioTextFieldColors(),
                    value = uiState.search,
                    onValueChange = callbacks.onSearchChange,
                    placeholder = { Text("Find a channel") },
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                GroupingToggle(
                    grouping = uiState.grouping,
                    onGroupingChange = callbacks.onGroupingChange,
                )
            }

            if (uiState.mode == MixerMode.SELECTED) {
                Spacer(Modifier.height(8.dp))
                ScenePicker(uiState.sceneNames, uiState.displayedScene, callbacks.onSelectScene)
            }
            if (uiState.notInProgram) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "“${uiState.displayedScene}” is not on program — meters may be silent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        },
    ) { expanded ->
        Column(Modifier.fillMaxSize()) {
            if (uiState.inputs.isEmpty()) {
                EmptyMixerState(uiState = uiState)
            } else {
                // Globals always exist; a scene with no audio of its own gets a hint.
                if (
                    uiState.mode != MixerMode.PINNED &&
                        uiState.inputs.none { it.scope != MixerScope.GLOBAL } &&
                        uiState.search.isBlank()
                ) {
                    Text(
                        text =
                            "No audio sources in “${uiState.displayedScene ?: "this scene"}” — " +
                                "only global channels are shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                MixerStripRow(
                    uiState = uiState,
                    levelsStore = levelsStore,
                    motionLevel = motionLevel,
                    hapticsEnabled = hapticsEnabled,
                    callbacks = callbacks,
                    mediaStatus = mediaStatus,
                    onExtrasClick = onExtrasClick,
                    onMediaClick = onMediaClick,
                    seeking = seeking,
                    expanded = expanded,
                )
            }
        }
    }
}

@Composable
private fun ScenePicker(sceneNames: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) {
            Text(selected ?: "Pick scene")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sceneNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(name)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupingToggle(grouping: MixerGrouping, onGroupingChange: (MixerGrouping) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { open = true }) {
            Icon(
                SceneIcon.INVENTORY.imageVector,
                contentDescription = "Group channels: ${grouping.name.lowercase()}",
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MixerGrouping.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                MixerGrouping.SCOPE -> "Group by scope"
                                MixerGrouping.SCENE_PATH -> "Group by scene path"
                                MixerGrouping.NONE -> "No grouping"
                            }
                        )
                    },
                    onClick = {
                        onGroupingChange(option)
                        open = false
                    },
                    leadingIcon =
                        if (option == grouping) {
                            { Icon(SceneIcon.MIXER.imageVector, contentDescription = "Selected") }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun EmptyMixerState(uiState: MixerUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No audio sources",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                when {
                    uiState.search.isNotBlank() -> "Nothing matches “${uiState.search}”."
                    uiState.displayedScene != null ->
                        "“${uiState.displayedScene}” has no audio-capable inputs."
                    else -> "No audio-capable inputs found."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@Composable
private fun MixerPreview() {
    SceneDeckTheme {
        Surface {
            MixerContent(
                uiState =
                    MixerUiState(
                        connection = ConnectionState.Ready(ObsVersionInfo("31", "5", 1, "studio")),
                        displayedScene = "Main camera",
                        inputs =
                            listOf(
                                MixerInputState(
                                    "Microphone",
                                    MixerScope.GLOBAL,
                                    null,
                                    0.8,
                                    false,
                                    false,
                                ),
                                MixerInputState(
                                    "Desktop",
                                    MixerScope.GLOBAL,
                                    null,
                                    1.0,
                                    false,
                                    false,
                                ),
                                MixerInputState("Music", MixerScope.SCENE, null, 0.5, true, false),
                            ),
                    ),
                levelsStore = MeterLevelsStore(),
                motionLevel = MotionLevel.OFF,
                hapticsEnabled = false,
                callbacks = MixerCallbacks(),
            )
        }
    }
}

@Composable
private fun MixerModeSelector(selectedMode: MixerMode, onModeChange: (MixerMode) -> Unit) {
    val labels =
        mapOf(
            MixerMode.ACTIVE to "Active",
            MixerMode.SELECTED to "Selected",
            MixerMode.PINNED to "Pinned",
        )
    BoxWithConstraints {
        if (maxWidth < 320.dp * LocalDensity.current.fontScale) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap)) {
                MixerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(labels.getValue(mode)) },
                        colors = studioFilterChipColors(),
                    )
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MixerMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        colors = studioSegmentedButtonColors(),
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, MixerMode.entries.size),
                    ) {
                        Text(labels.getValue(mode))
                    }
                }
            }
        }
    }
}
