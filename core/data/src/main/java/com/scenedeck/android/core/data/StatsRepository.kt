package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 1 Hz telemetry snapshot: `GetStats` + `GetStreamStatus` + `GetRecordStatus` + virtual camera
 * status status while connected (obs-websocket has no push stats; FEATURE_SPEC §5). Drives the
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

    private val outputLock = Any()
    private var streamRevision = 0L
    private var recordRevision = 0L
    private var cameraRevision = 0L

    init {
        scope.launch {
            client.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Ready -> pollLoop(state)
                    else -> {
                        history.clear()
                        _samples.value = emptyList()
                        synchronized(outputLock) {
                            streamRevision++
                            recordRevision++
                            cameraRevision++
                            _telemetry.value = Telemetry(connection = state)
                        }
                    }
                }
            }
        }
        scope.launch { client.events.collect { applyOutputEvent(it) } }
    }

    private suspend fun pollLoop(connection: ConnectionState) {
        history.clear()
        _samples.value = emptyList()
        var lastBytes: Long? = null
        var lastSampleAtMs = 0L
        while (currentCoroutineContext().isActive) {
            val stats = requestResult { client.getStats() }.getOrNull()
            val streamVersion = synchronized(outputLock) { streamRevision }
            val stream = requestResult { client.getStreamStatus() }.getOrNull()
            val recordVersion = synchronized(outputLock) { recordRevision }
            val record = requestResult { client.getRecordStatus() }.getOrNull()
            val cameraVersion = synchronized(outputLock) { cameraRevision }
            val virtualCam = requestResult { client.getVirtualCamStatus() }.getOrDefault(false)
            val now = nowMs()
            val bitrate =
                computeBitrateKbps(
                    stream?.active == true,
                    stream?.bytes ?: 0,
                    lastBytes,
                    now - lastSampleAtMs,
                )
            lastBytes = stream?.bytes
            lastSampleAtMs = now
            val polled = Telemetry(connection, stats, stream, record, bitrate, virtualCam)
            val snapshot =
                publishPoll(polled, OutputRevisions(streamVersion, recordVersion, cameraVersion))
            if (snapshot?.stats != null) {
                history.add(snapshot.toSample(history.latest()))
                _samples.value = history.toList()
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private data class OutputRevisions(val stream: Long, val record: Long, val camera: Long)

    /** Output events win over responses requested before that event was received. */
    private fun publishPoll(polled: Telemetry, versions: OutputRevisions): Telemetry? =
        synchronized(outputLock) {
            if (client.connectionState.value !is ConnectionState.Ready) return@synchronized null
            val merged = mergeOutputReadings(polled, versions)
            _telemetry.value = merged
            merged
        }

    /** Called under outputLock so revision comparisons and the publication stay atomic. */
    private fun mergeOutputReadings(polled: Telemetry, versions: OutputRevisions): Telemetry {
        val current = _telemetry.value
        val streamFresh = versions.stream == streamRevision
        return polled.copy(
            stream = if (streamFresh) polled.stream else current.stream,
            record = if (versions.record == recordRevision) polled.record else current.record,
            virtualCamActive =
                if (versions.camera == cameraRevision) polled.virtualCamActive
                else current.virtualCamActive,
            bitrateKbps = if (streamFresh) polled.bitrateKbps else current.bitrateKbps,
        )
    }

    private fun applyOutputEvent(event: ObsEvent) =
        synchronized(outputLock) {
            val connection =
                client.connectionState.value as? ConnectionState.Ready ?: return@synchronized
            when (event) {
                is ObsEvent.StreamStateChanged -> {
                    streamRevision++
                    _telemetry.update { current ->
                        current.copy(
                            connection = connection,
                            stream = streamFromEvent(current.stream, event),
                            bitrateKbps = 0,
                        )
                    }
                }
                is ObsEvent.RecordStateChanged -> {
                    recordRevision++
                    _telemetry.update { current ->
                        current.copy(
                            connection = connection,
                            record = recordFromEvent(current.record, event),
                        )
                    }
                }
                is ObsEvent.VirtualcamStateChanged -> {
                    cameraRevision++
                    _telemetry.update {
                        it.copy(connection = connection, virtualCamActive = event.active)
                    }
                }
                else -> Unit
            }
        }

    private fun streamFromEvent(
        previous: StreamStatus?,
        event: ObsEvent.StreamStateChanged,
    ): StreamStatus {
        val old = previous ?: StreamStatus(false, false, "00:00:00.000", 0, 0, 0.0, 0, 0)
        val base =
            if (event.active && !old.active)
                old.copy(timecode = "00:00:00.000", durationMs = 0, bytes = 0)
            else old
        return base.copy(active = event.active, reconnecting = event.state.endsWith("RECONNECTING"))
    }

    private fun recordFromEvent(
        previous: RecordStatus?,
        event: ObsEvent.RecordStateChanged,
    ): RecordStatus {
        val old = previous ?: RecordStatus(false, false, "00:00:00.000", 0, 0)
        val base =
            if (event.active && !old.active)
                old.copy(timecode = "00:00:00.000", durationMs = 0, bytes = 0)
            else old
        return base.copy(active = event.active, paused = event.state.endsWith("PAUSED"))
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
