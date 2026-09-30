package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.obs.ObsClient
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
 * 1 Hz telemetry snapshot: `GetStats` + `GetStreamStatus` + `GetRecordStatus` + virtual camera status
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
)

@Singleton
class StatsRepository
@Inject
constructor(
    private val client: ObsClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Injectable sample clock (wall clock in production; virtual in tests). */
    internal var nowMs: () -> Long = System::currentTimeMillis
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    /**
     * Rolling 2-minute [TelemetrySample] window (FEATURE_SPEC §5). Connection-scoped: filled by the
     * 1 Hz poll loop for the whole session, cleared on disconnect.
     */
    private val history = TelemetryHistory()
    private val _samples = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val samples: StateFlow<List<TelemetrySample>> = _samples.asStateFlow()

    init {
        scope.launch {
            client.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Ready -> pollLoop(state)
                    else -> {
                        history.clear()
                        _samples.value = emptyList()
                        _telemetry.value = Telemetry(connection = state)
                    }
                }
            }
        }
    }

    private suspend fun pollLoop(connection: ConnectionState) {
        history.clear()
        _samples.value = emptyList()
        var lastBytes: Long? = null
        var lastSampleAtMs = 0L
        while (currentCoroutineContext().isActive) {
            requestResult {
                val stats = client.getStats()
                val stream = client.getStreamStatus()
                val record = client.getRecordStatus()
                val virtualCam = requestResult { client.getVirtualCamStatus() }.getOrDefault(false)
                val nowMs = nowMs()
                val bitrate =
                    computeBitrateKbps(
                        active = stream.active,
                        bytes = stream.bytes,
                        previousBytes = lastBytes,
                        elapsedMs = nowMs - lastSampleAtMs,
                    )
                lastBytes = stream.bytes
                lastSampleAtMs = nowMs
                val snapshot =
                    Telemetry(
                        connection = connection,
                        stats = stats,
                        stream = stream,
                        record = record,
                        bitrateKbps = bitrate,
                        virtualCamActive = virtualCam,
                    )
                _telemetry.value = snapshot
                history.add(snapshot.toSample(history.latest()))
                _samples.value = history.toList()
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
            // Floating-point multiplication avoids overflow for long-running counters.
            return (deltaBytes.toDouble() * 8 / elapsedMs)
                .coerceAtMost(Int.MAX_VALUE.toDouble())
                .toInt() // bits per ms == kbit/s
        }
    }
}
