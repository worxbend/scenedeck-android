package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** Playback state of a media input (`GetMediaInputStatus`). */
enum class MediaStateKind {
    NONE,
    PLAYING,
    OPENING,
    BUFFERING,
    PAUSED,
    STOPPED,
    ENDED,
    ERROR,
}

/** Actions accepted by `TriggerMediaInputAction`. */
enum class MediaActionKind {
    NONE,
    PLAY,
    PAUSE,
    STOP,
    RESTART,
    NEXT,
    PREVIOUS,
}

/** Audio monitoring mode of an input. */
enum class MonitorTypeKind {
    NONE,
    MONITOR_ONLY,
    MONITOR_AND_OUTPUT,
}

/** Media playback status of one input. */
@Immutable
data class MediaStatus(
    val state: MediaStateKind,
    val durationMs: Long?,
    val cursorMs: Long?,
)
