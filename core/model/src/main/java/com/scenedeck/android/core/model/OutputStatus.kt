package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** Streaming output status (`GetStreamStatus`). */
@Immutable
data class StreamStatus(
    val active: Boolean,
    val reconnecting: Boolean,
    val timecode: String,
    val durationMs: Long,
    val bytes: Long,
    /** 0..1 — warn at 0.3, crit at 0.6 (docs/FEATURE_SPEC.md §5). */
    val congestion: Double,
    val skippedFrames: Int,
    val totalFrames: Int,
)

/** Recording output status (`GetRecordStatus`). */
@Immutable
data class RecordStatus(
    val active: Boolean,
    val paused: Boolean,
    val timecode: String,
    val durationMs: Long,
    val bytes: Long,
)
