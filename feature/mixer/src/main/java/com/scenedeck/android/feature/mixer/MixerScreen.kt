package com.scenedeck.android.feature.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.components.MixerStrip
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MixerScope

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

    when (val connection = uiState.connection) {
        is ConnectionState.Ready -> MixerContent(
            uiState = uiState,
            levelsStore = viewModel.levelsStore,
            motionLevel = motionLevel,
            hapticsEnabled = hapticsEnabled,
            callbacks = MixerCallbacks(
                onModeChange = viewModel::setMode,
                onSelectScene = { viewModel.selectScene(it) },
                onGroupingChange = viewModel::setGrouping,
                onSearchChange = viewModel::setSearch,
                onVolumePreview = viewModel::onVolumePreview,
                onVolumeCommit = viewModel::onVolumeCommit,
                onToggleMute = viewModel::toggleMute,
                onToggleLock = viewModel::toggleLock,
            ),
            modifier = modifier,
        )

        else -> DisconnectedPlaceholder(
            connectionState = connection,
            onConnect = onNavigateToConnections,
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Mixer",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        // Mode selector: Active (follows program) / Selected (frozen scene) / Pinned.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MixerMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.mode == mode,
                    onClick = { callbacks.onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, MixerMode.entries.size),
                ) {
                    Text(
                        when (mode) {
                            MixerMode.ACTIVE -> "Active"
                            MixerMode.SELECTED -> "Selected"
                            MixerMode.PINNED -> "Pinned"
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.mode == MixerMode.SELECTED) {
                ScenePicker(
                    sceneNames = uiState.sceneNames,
                    selected = uiState.displayedScene,
                    onSelect = callbacks.onSelectScene,
                )
            }
            OutlinedTextField(
                value = uiState.search,
                onValueChange = callbacks.onSearchChange,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            GroupingToggle(
                grouping = uiState.grouping,
                onGroupingChange = callbacks.onGroupingChange,
            )
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
            if (uiState.inputs.none { it.scope != MixerScope.GLOBAL } && uiState.search.isBlank()) {
                Text(
                    text = "No audio sources in “${uiState.displayedScene ?: "this scene"}” — " +
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
    OutlinedButton(
        onClick = {
            val next = MixerGrouping.entries[(grouping.ordinal + 1) % MixerGrouping.entries.size]
            onGroupingChange(next)
        },
    ) {
        Text(
            when (grouping) {
                MixerGrouping.SCOPE -> "Group: scope"
                MixerGrouping.SCENE_PATH -> "Group: path"
                MixerGrouping.NONE -> "No grouping"
            },
        )
    }
}

private sealed interface MixerRowItem {
    val key: String

    data class Header(val label: String) : MixerRowItem {
        override val key get() = "header-$label"
    }

    data class Strip(val input: MixerInputState) : MixerRowItem {
        override val key get() = "strip-${input.name}"
    }
}

@Composable
private fun MixerStripRow(
    uiState: MixerUiState,
    levelsStore: MeterLevelsStore,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    callbacks: MixerCallbacks,
) {
    val items = buildList {
        var lastGroup: String? = null
        uiState.inputs.forEach { input ->
            if (uiState.grouping != MixerGrouping.NONE) {
                val group = when (uiState.grouping) {
                    MixerGrouping.SCOPE -> scopeGroupLabel(input.scope)
                    MixerGrouping.SCENE_PATH -> input.scopePath ?: scopeGroupLabel(input.scope)
                    MixerGrouping.NONE -> ""
                }
                if (group != lastGroup) {
                    add(MixerRowItem.Header(group))
                    lastGroup = group
                }
            }
            add(MixerRowItem.Strip(input))
        }
    }

    LazyRow(
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is MixerRowItem.Header -> GroupHeaderChip(item.label)
                is MixerRowItem.Strip -> MixerStrip(
                    name = item.input.name,
                    scope = item.input.scope,
                    scopePath = item.input.scopePath,
                    volumeMul = item.input.volumeMul,
                    muted = item.input.muted,
                    locked = item.input.locked,
                    meterHolder = levelsStore.holder(item.input.name),
                    motionLevel = motionLevel,
                    hapticsEnabled = hapticsEnabled,
                    onVolumePreview = { callbacks.onVolumePreview(item.input.name, it) },
                    onVolumeCommit = { callbacks.onVolumeCommit(item.input.name, it) },
                    onToggleMute = { callbacks.onToggleMute(item.input.name, !item.input.muted) },
                    onToggleLock = { callbacks.onToggleLock(item.input.name, !item.input.locked) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeaderChip(label: String) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyMixerState(uiState: MixerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No audio sources",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
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

private fun scopeGroupLabel(scope: MixerScope): String = when (scope) {
    MixerScope.GLOBAL -> "Global"
    MixerScope.SCENE -> "Scene"
    MixerScope.NESTED -> "Nested"
    MixerScope.GROUP -> "Group"
}
