package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus

/**
 * 1 Hz telemetry snapshot: `GetStats` + `GetStreamStatus` + `GetRecordStatus` + virtual camera
 * status while connected (obs-websocket has no push stats; FEATURE_SPEC §5). Drives the
 * StatusStrip, the TransportBar and the stats page; [StatsRepository.samples] keeps the rolling
 * 2-minute window for charts.
 */
data class Telemetry(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val stats: ObsStats? = null,
    val stream: StreamStatus? = null,
    val record: RecordStatus? = null,
    /** Rolling bitrate computed from consecutive `GetStreamStatus` byte counters. */
    val bitrateKbps: Int = 0,
    val virtualCamActive: Boolean = false,
    /**
     * OBS canvas frame rate from `GetVideoSettings` (e.g. 30, 59.94); null until known. Stats
     * thresholds derive from it instead of assuming 60 fps.
     */
    val targetFps: Double? = null,
    /** Origin and individual successful receipt times; never persisted. */
    val session: ObsSessionToken? = null,
    val receivedAt: TelemetryReadTimes = TelemetryReadTimes(),
)

/** Monotonic milliseconds for successful reads, not a wall-clock report timestamp. */
data class TelemetryReadTimes(
    val stats: Long? = null,
    val stream: Long? = null,
    val record: Long? = null,
)
