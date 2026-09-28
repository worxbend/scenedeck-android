package com.scenedeck.android.ui.components

import com.scenedeck.android.core.model.ConnectionState

/** Telemetry snapshot rendered by [StatusStrip]; numerals are always monospace. */
data class StatusStripState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val fps: Double = 0.0,
    val droppedFrames: Int = 0,
    val cpuPercent: Double = 0.0,
    val bitrateKbps: Int = 0,
)

// TODO(M3): replace the numeric fields with the live StatsRepository feed.
val mockStatusStripState = StatusStripState(
    connection = ConnectionState.Disconnected,
    fps = 60.0,
    droppedFrames = 3,
    cpuPercent = 11.8,
    bitrateKbps = 6000,
)

