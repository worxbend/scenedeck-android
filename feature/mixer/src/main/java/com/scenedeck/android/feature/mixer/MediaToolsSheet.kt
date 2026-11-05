package com.scenedeck.android.feature.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.MediaPlayback
import com.scenedeck.android.core.data.MediaSeekIntent
import com.scenedeck.android.core.data.MediaSeekResult
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.mono
import com.scenedeck.android.core.model.MediaStateKind
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaToolsSheet(
    inputName: String,
    playback: MediaPlayback?,
    seekSupported: Boolean,
    locked: Boolean,
    onSeek: (MediaSeekIntent) -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        MediaToolsContent(inputName, playback, seekSupported, locked, onSeek, onClose)
    }
}

@Composable
internal fun MediaToolsContent(
    inputName: String,
    playback: MediaPlayback?,
    seekSupported: Boolean,
    locked: Boolean,
    onSeek: (MediaSeekIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = playback?.durationMs
    val cursor = playback?.cursorMs
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudioPageHeader("Media tools", inputName, SceneIcon.VIDEO.imageVector)
        Text(mediaStatusText(playback?.status?.state), style = MaterialTheme.typography.titleMedium)
        Text("Elapsed · ${mediaTime(cursor)}", style = MaterialTheme.typography.mono)
        Text(
            "Remaining · ${mediaTime(playback?.remainingMs)}",
            style = MaterialTheme.typography.mono,
        )
        Text("Duration · ${mediaTime(duration)}", style = MaterialTheme.typography.mono)
        MediaSeekSlider(inputName, playback, seekSupported && !locked, onSeek)
        Text(
            seekExplanation(playback, seekSupported, locked),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Times use the latest OBS reading, refreshed about every 2 seconds. Drag and release to seek once.",
            style = MaterialTheme.typography.bodySmall,
        )
        mediaFeedback(playback)?.let { message ->
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun MediaSeekSlider(
    inputName: String,
    playback: MediaPlayback?,
    permitted: Boolean,
    onSeek: (MediaSeekIntent) -> Unit,
) {
    val duration = playback?.durationMs
    val cursor = playback?.cursorMs
    var draft by remember { mutableStateOf<MediaSeekIntent?>(null) }
    var discarded by remember { mutableStateOf(false) }
    val enabled = permitted && playback?.seeking == false && duration != null && cursor != null
    Slider(
        value =
            draft
                ?.takeIf { it.matches(playback) }
                ?.let { (it.cursorMs.toDouble() / it.durationMs).toFloat() }
                ?: if (duration != null && cursor != null)
                    (cursor.toDouble() / duration).toFloat().coerceIn(0f, 1f)
                else 0f,
        onValueChange = {
            discarded = false
            draft = seekDraft(playback, draft, it)
        },
        onValueChangeFinished = {
            val chosen = draft
            draft = null
            val matches = chosen?.matches(playback) == true
            discarded = chosen != null && !matches
            if (enabled && matches) onSeek(checkNotNull(chosen))
        },
        enabled = enabled,
        modifier =
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Seek position for $inputName"
            },
    )
    if (discarded || draft?.matches(playback) == false)
        Text(
            "Media changed during the gesture. Adjust the position again.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    draft
        ?.takeIf { it.matches(playback) }
        ?.let { chosen ->
            Text(
                "Seek to · ${mediaTime(chosen.cursorMs)}",
                style = MaterialTheme.typography.mono,
            )
        }
}

private fun seekExplanation(playback: MediaPlayback?, supported: Boolean, locked: Boolean): String =
    when {
        locked -> "Unlock this channel to seek."
        !supported -> "Seeking is unavailable on this OBS connection."
        playback?.status == null -> "Waiting for a fresh OBS reading. Seeking is unavailable."
        playback.durationMs == null ->
            "Duration unknown. Play or pause a source with a known duration to seek."
        playback.cursorMs == null -> "Position unknown. Seeking is unavailable."
        else -> "Seeking keeps the source's current playing or paused state."
    }

private fun mediaFeedback(playback: MediaPlayback?): String? =
    if (playback?.seeking == true) "Seeking… Waiting for OBS."
    else
        when (playback?.result) {
            MediaSeekResult.CONFIRMED -> "OBS reports a position near the requested time."
            MediaSeekResult.DIFFERENT ->
                "OBS reports a different position. Check the elapsed time before trying again."
            MediaSeekResult.REJECTED ->
                "OBS rejected the seek. Check this source's playback settings."
            MediaSeekResult.UNKNOWN ->
                "Outcome unknown. Check OBS before trying again; the seek was not retried."
            MediaSeekResult.CHANGED ->
                "Media changed. Check its current position before trying again."
            null -> null
        }

private fun mediaStatusText(state: MediaStateKind?): String =
    when (state) {
        MediaStateKind.PLAYING -> "Playing"
        MediaStateKind.PAUSED -> "Paused"
        MediaStateKind.OPENING -> "Opening"
        MediaStateKind.BUFFERING -> "Buffering"
        MediaStateKind.STOPPED -> "Stopped"
        MediaStateKind.ENDED -> "Ended"
        MediaStateKind.ERROR -> "Playback error"
        MediaStateKind.NONE -> "No media loaded"
        null -> "Playback state unknown"
    }

private fun mediaTime(value: Long?): String =
    value?.let {
        val seconds = it / 1_000
        String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3_600,
            seconds / 60 % 60,
            seconds % 60,
        )
    } ?: "Unknown"

private fun MediaSeekIntent.matches(playback: MediaPlayback?): Boolean =
    target == playback?.target && durationMs == playback.durationMs

private fun seekDraft(
    playback: MediaPlayback?,
    previous: MediaSeekIntent?,
    fraction: Float,
): MediaSeekIntent? {
    val initial =
        previous
            ?: playback?.durationMs?.let { MediaSeekIntent(playback.target, it, 0) }
            ?: return null
    return initial.copy(
        cursorMs = (fraction.toDouble().coerceIn(0.0, 1.0) * initial.durationMs).toLong()
    )
}
