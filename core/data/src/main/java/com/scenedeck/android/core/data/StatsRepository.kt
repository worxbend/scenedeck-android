package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 1 Hz telemetry snapshot: `GetStats` + `GetStreamStatus` + `GetRecordStatus` while
 * connected (obs-websocket has no push stats; FEATURE_SPEC §5). Drives the StatusStrip
 * and the TransportBar; the full stats page (ring buffer, charts) is M5.
 */
data class Telemetry(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val stats: ObsStats? = null,
    val stream: StreamStatus? = null,
    val record: RecordStatus? = null,
    /** Rolling bitrate computed from consecutive `GetStreamStatus` byte counters. */
    val bitrateKbps: Int = 0,
    val virtualCamActive: Boolean = false,
    val replayBufferActive: Boolean = false,
)

@Singleton
class StatsRepository @Inject constructor(
    private val client: ObsClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Injectable sample clock (wall clock in production; virtual in tests). */
    internal var nowMs: () -> Long = System::currentTimeMillis
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    init {
        scope.launch {
            client.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Ready -> pollLoop(state)
                    else -> _telemetry.value = Telemetry(connection = state)
                }
            }
        }
    }

    private suspend fun pollLoop(connection: ConnectionState) {
        var lastBytes: Long? = null
        var lastSampleAtMs = 0L
        while (currentCoroutineContext().isActive) {
            runCatching {
                val stats = client.getStats()
                val stream = client.getStreamStatus()
                val record = client.getRecordStatus()
                val virtualCam = runCatching { client.getVirtualCamStatus() }.getOrDefault(false)
                val replayBuffer = runCatching { client.getReplayBufferStatus() }.getOrDefault(false)
                val nowMs = nowMs()
                val bitrate = computeBitrateKbps(
                    active = stream.active,
                    bytes = stream.bytes,
                    previousBytes = lastBytes,
                    elapsedMs = nowMs - lastSampleAtMs,
                )
                lastBytes = stream.bytes
                lastSampleAtMs = nowMs
                _telemetry.value = Telemetry(
                    connection = connection,
                    stats = stats,
                    stream = stream,
                    record = record,
                    bitrateKbps = bitrate,
                    virtualCamActive = virtualCam,
                    replayBufferActive = replayBuffer,
                )
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L

        /** bytes delta over elapsed → kbit/s; 0 when inactive or first sample. */
        fun computeBitrateKbps(
            active: Boolean,
            bytes: Long,
            previousBytes: Long?,
            elapsedMs: Long,
        ): Int {
            if (!active || previousBytes == null || elapsedMs <= 0) return 0
            val deltaBytes = (bytes - previousBytes).coerceAtLeast(0)
            return (deltaBytes * 8 / elapsedMs).toInt() // bits per ms == kbit/s
        }
    }
}
