package com.scenedeck.android.ui.components

import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus

/** Telemetry snapshot rendered by [StatusStrip]; numerals are always monospace. */
data class StatusStripState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val fps: Double = 0.0,
    val droppedFrames: Int = 0,
    val cpuPercent: Double = 0.0,
    val bitrateKbps: Int = 0,
    val stream: StreamStatus? = null,
    val record: RecordStatus? = null,
)

/** Sample state for previews; production instances come from the live StatsRepository feed. */
val mockStatusStripState =
    StatusStripState(
        connection = ConnectionState.Disconnected,
        fps = 60.0,
        droppedFrames = 3,
        cpuPercent = 11.8,
        bitrateKbps = 6000,
    )

/** Disconnected outputs are unknown, never a stale LIVE or a guessed standby state. */
internal fun Telemetry.toStatusStripState(): StatusStripState {
    if (connection !is ConnectionState.Ready) return StatusStripState(connection = connection)
    return StatusStripState(
        connection = connection,
        fps = stats?.activeFps ?: 0.0,
        droppedFrames = stream?.skippedFrames ?: 0,
        cpuPercent = stats?.cpuUsage ?: 0.0,
        bitrateKbps = bitrateKbps,
        stream = stream,
        record = record,
    )
}
