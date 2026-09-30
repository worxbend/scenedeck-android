package com.scenedeck.android.feature.mixer

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.MediaControls
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.components.MixerStrip
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
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

private val MEDIA_KINDS = setOf("ffmpeg_source", "vlc_source")

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
    var extrasFor by remember { mutableStateOf<String?>(null) }

    when (val connection = uiState.connection) {
        is ConnectionState.Ready ->
            MixerContent(
                uiState = uiState,
                levelsStore = viewModel.levelsStore,
                motionLevel = motionLevel,
                hapticsEnabled = hapticsEnabled,
                callbacks =
                    MixerCallbacks(
                        onModeChange = viewModel::setMode,
                        onSelectScene = { viewModel.selectScene(it) },
                        onGroupingChange = viewModel::setGrouping,
                        onSearchChange = viewModel::setSearch,
                        onVolumePreview = viewModel::onVolumePreview,
                        onVolumeCommit = viewModel::onVolumeCommit,
                        onToggleMute = viewModel::toggleMute,
                        onToggleLock = viewModel::toggleLock,
                        onMediaPlayPause = viewModel::mediaPlayPause,
                        onMediaRestart = viewModel::mediaRestart,
                    ),
                mediaStatus = mediaStatus,
                onExtrasClick = { extrasFor = it },
                modifier = modifier,
            )

        else ->
            DisconnectedPlaceholder(
                connectionState = connection,
                onConnect = onNavigateToConnections,
            )
    }

    extrasFor?.let { inputName ->
        AudioExtrasSheet(
            inputName = inputName,
            loadExtras = { viewModel.loadAudioExtras(inputName) },
            onBalanceChange = { viewModel.setAudioBalance(inputName, it) },
            onSyncOffsetChange = { viewModel.setAudioSyncOffset(inputName, it) },
            onMonitorTypeChange = { viewModel.setAudioMonitorType(inputName, it) },
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
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
    ) {
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
            )
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
private fun MixerStripRow(
    uiState: MixerUiState,
    levelsStore: MeterLevelsStore,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    callbacks: MixerCallbacks,
    mediaStatus: Map<String, MediaStatus>,
    onExtrasClick: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(uiState.inputs, key = { it.name }) { input ->
            Column {
                if (uiState.grouping == MixerGrouping.SCENE_PATH) {
                    Text(
                        text =
                            if (uiState.grouping == MixerGrouping.SCENE_PATH)
                                input.scopePath ?: scopeGroupLabel(input.scope)
                            else scopeGroupLabel(input.scope),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp).padding(start = 4.dp, bottom = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
                MixerStrip(
                    name = input.name,
                    scope = input.scope,
                    scopePath =
                        input.scopePath.takeUnless { uiState.grouping == MixerGrouping.SCENE_PATH },
                    volumeMul = input.volumeMul,
                    muted = input.muted,
                    locked = input.locked,
                    meterHolder = levelsStore.holder(input.name),
                    motionLevel = motionLevel,
                    hapticsEnabled = hapticsEnabled,
                    onVolumePreview = { callbacks.onVolumePreview(input.name, it) },
                    onVolumeCommit = { callbacks.onVolumeCommit(input.name, it) },
                    onToggleMute = { callbacks.onToggleMute(input.name, !input.muted) },
                    onToggleLock = { callbacks.onToggleLock(input.name, !input.locked) },
                    onExtrasClick = { onExtrasClick(input.name) },
                )
                if (input.inputKind in MEDIA_KINDS) {
                    MediaControls(
                        state = mediaStatus[input.name]?.state,
                        enabled = !input.locked,
                        onPlayPause = { callbacks.onMediaPlayPause(input.name) },
                        onRestart = { callbacks.onMediaRestart(input.name) },
                        modifier = Modifier.width(120.dp).padding(top = 8.dp),
                        compact = true,
                    )
                }
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

private fun scopeGroupLabel(scope: MixerScope): String =
    when (scope) {
        MixerScope.GLOBAL -> "Global"
        MixerScope.SCENE -> "Scene"
        MixerScope.NESTED -> "Nested"
        MixerScope.GROUP -> "Group"
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
    // Mode selector: Active (follows program) / Selected (frozen scene) / Pinned.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        MixerMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                colors = studioSegmentedButtonColors(),
                selected = selectedMode == mode,
                onClick = { onModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, MixerMode.entries.size),
            ) {
                Text(
                    when (mode) {
                        MixerMode.ACTIVE -> "Active"
                        MixerMode.SELECTED -> "Selected"
                        MixerMode.PINNED -> "Pinned"
                    }
                )
            }
        }
    }
}
