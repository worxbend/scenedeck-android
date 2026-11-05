package com.scenedeck.android.feature.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.designsystem.components.MediaControls
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.components.MixerStrip
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.MixerScope

private val MEDIA_KINDS = setOf("ffmpeg_source", "vlc_source")

@Composable
internal fun MixerStripRow(
    uiState: MixerUiState,
    levelsStore: MeterLevelsStore,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    callbacks: MixerCallbacks,
    mediaStatus: Map<String, MediaStatus>,
    onExtrasClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    seeking: Set<String>,
    expanded: Boolean,
) {
    val rowState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val channel: @Composable (MixerInputState) -> Unit = { input ->
        MixerChannel(
            input,
            levelsStore,
            motionLevel,
            hapticsEnabled,
            callbacks,
            mediaStatus,
            onExtrasClick,
            onMediaClick,
            seeking,
            expanded,
            uiState.grouping,
        )
    }
    if (expanded) {
        LazyVerticalGrid(
            columns =
                GridCells.Adaptive(160.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)),
            state = gridState,
            contentPadding = PaddingValues(bottom = SceneDeckTheme.spacing.sectionGap),
            horizontalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
            verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.sectionGap),
        ) {
            items(uiState.inputs, key = { it.name }) { channel(it) }
        }
    } else {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.inputs, key = { it.name }) { channel(it) }
            }
        }
    }
}

@Composable
private fun MixerChannel(
    input: MixerInputState,
    levelsStore: MeterLevelsStore,
    motionLevel: MotionLevel,
    hapticsEnabled: Boolean,
    callbacks: MixerCallbacks,
    mediaStatus: Map<String, MediaStatus>,
    onExtrasClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    seeking: Set<String>,
    expanded: Boolean,
    grouping: MixerGrouping,
) {
    Column {
        if (grouping == MixerGrouping.SCENE_PATH) {
            Text(
                text = input.scopePath ?: scopeGroupLabel(input.scope),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.width(if (expanded) 160.dp else 120.dp)
                        .padding(start = 4.dp, bottom = 8.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
        MixerStrip(
            name = input.name,
            modifier = if (expanded) Modifier.fillMaxWidth() else Modifier,
            scope = input.scope,
            // SCOPE keeps the path on the strip badge; NONE is fully flat (no
            // headers, badge shows the scope only) and SCENE_PATH moves it to the header.
            scopePath = input.scopePath.takeIf { grouping == MixerGrouping.SCOPE },
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
            onMediaClick =
                if (input.inputKind in MEDIA_KINDS) ({ onMediaClick(input.name) }) else null,
        )
        MixerChannelMedia(input, mediaStatus[input.name], callbacks, seeking, expanded)
    }
}

@Composable
private fun MixerChannelMedia(
    input: MixerInputState,
    status: MediaStatus?,
    callbacks: MixerCallbacks,
    seeking: Set<String>,
    expanded: Boolean,
) {
    if (input.inputKind in MEDIA_KINDS) {
        MediaControls(
            state = status?.state,
            enabled = !input.locked && input.name !in seeking,
            onPlayPause = { callbacks.onMediaPlayPause(input.name) },
            onRestart = { callbacks.onMediaRestart(input.name) },
            modifier = Modifier.width(if (expanded) 160.dp else 120.dp).padding(top = 8.dp),
            compact = true,
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
