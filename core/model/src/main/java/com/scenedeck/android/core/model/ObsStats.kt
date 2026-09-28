package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** General OBS performance stats (`GetStats`, polled at 1 Hz). */
@Immutable
data class ObsStats(
    val cpuUsage: Double,
    val memoryUsageMb: Double,
    val availableDiskSpaceMb: Double,
    val activeFps: Double,
    val averageFrameRenderTimeMs: Double,
    /** Render-lag frames (skip warning thresholds in docs/FEATURE_SPEC.md §5). */
    val renderSkippedFrames: Int,
    val renderTotalFrames: Int,
    /** Output-lag (encoding/network) frames. */
    val outputSkippedFrames: Int,
    val outputTotalFrames: Int,
)
