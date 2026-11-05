package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class StatsRepository
@Inject
constructor(
    private val client: ObsClient,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: MonotonicClock,
) {
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    /**
     * Rolling 2-minute [TelemetrySample] window (FEATURE_SPEC §5). Session/collection-scoped:
     * filled by the 1 Hz poll loop; cleared on disconnect or collection replacement.
     */
    private val history = TelemetryHistory()
    private val _samples = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val samples: StateFlow<List<TelemetrySample>> = _samples.asStateFlow()

    private val canvasFrameRate = CanvasFrameRate(client)

    private val outputLock = Any()
    private var streamRevision = 0L
    private var recordRevision = 0L
    private var cameraRevision = 0L

    init {
        // Subscribe before initial polling so early output events cannot be lost.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect { applyOutputEvent(it) }
        }
        scope.launch {
            client.connectionState.collectLatest { connection ->
                val origin =
                    (connection as? ConnectionState.Ready)
                        ?.takeUnless { it.collectionChanging }
                        ?.token
                reset(connection, origin)
                if (origin != null) pollLoop(connection, origin)
            }
        }
    }

    private fun isCurrent(origin: ObsSessionToken): Boolean {
        val ready = client.connectionState.value as? ConnectionState.Ready ?: return false
        return !ready.collectionChanging && ready.token == origin
    }

    private fun reset(connection: ConnectionState, origin: ObsSessionToken?) {
        history.clear()
        _samples.value = emptyList()
        canvasFrameRate.reset()
        synchronized(outputLock) {
            streamRevision++
            recordRevision++
            cameraRevision++
            _telemetry.value = Telemetry(connection = connection, session = origin)
        }
    }

    private suspend fun pollLoop(connection: ConnectionState, origin: ObsSessionToken) {
        var lastBytes: Long? = null
        var lastSampleAtMs = 0L
        while (currentCoroutineContext().isActive && isCurrent(origin)) {
            val stats = read(origin) { client.getStats() }
            val streamVersion = synchronized(outputLock) { streamRevision }
            val stream = read(origin) { client.getStreamStatus() }
            val recordVersion = synchronized(outputLock) { recordRevision }
            val record = read(origin) { client.getRecordStatus() }
            val cameraVersion = synchronized(outputLock) { cameraRevision }
            val virtualCam = read(origin) { client.getVirtualCamStatus() }
            val targetFps = read(origin) { canvasFrameRate.refreshIfDue() }.value
            val now = clock.nowMs()
            val bitrate =
                computeBitrateKbps(
                    stream.value?.active == true,
                    stream.value?.bytes ?: 0,
                    lastBytes,
                    (stream.atMs ?: now) - lastSampleAtMs,
                )
            lastBytes = stream.value?.bytes
            lastSampleAtMs = stream.atMs ?: now
            val polled =
                Telemetry(
                    connection = connection,
                    stats = stats.value,
                    stream = stream.value,
                    record = record.value,
                    bitrateKbps = bitrate,
                    virtualCamActive = virtualCam.value ?: false,
                    targetFps = targetFps,
                    session = origin,
                    receivedAt = TelemetryReadTimes(stats.atMs, stream.atMs, record.atMs),
                )
            val snapshot =
                publishPoll(polled, OutputRevisions(streamVersion, recordVersion, cameraVersion))
            if (snapshot?.stats != null) {
                history.add(snapshot.toSample(history.latest()))
                _samples.value = history.toList()
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private data class Reading<T>(val value: T?, val atMs: Long?)

    private suspend fun <T> read(origin: ObsSessionToken, block: suspend () -> T): Reading<T> {
        val result = requestResult { client.withSession(origin) { block() } }
        return Reading(result.getOrNull(), if (result.isSuccess) clock.nowMs() else null)
    }

    private data class OutputRevisions(val stream: Long, val record: Long, val camera: Long)

    /** Output events win over responses requested before that event was received. */
    private fun publishPoll(polled: Telemetry, versions: OutputRevisions): Telemetry? =
        synchronized(outputLock) {
            val origin = polled.session ?: return@synchronized null
            if (!isCurrent(origin)) return@synchronized null
            val merged = mergeOutputReadings(polled, versions)
            _telemetry.value = merged
            merged
        }

    /** Called under outputLock so revision comparisons and the publication stay atomic. */
    private fun mergeOutputReadings(polled: Telemetry, versions: OutputRevisions): Telemetry {
        val current = _telemetry.value
        val streamFresh = versions.stream == streamRevision
        val recordFresh = versions.record == recordRevision
        return polled.copy(
            stream = if (streamFresh) polled.stream else current.stream,
            record = if (recordFresh) polled.record else current.record,
            receivedAt =
                polled.receivedAt.copy(
                    stream =
                        if (streamFresh) polled.receivedAt.stream else current.receivedAt.stream,
                    record =
                        if (recordFresh) polled.receivedAt.record else current.receivedAt.record,
                ),
            virtualCamActive =
                if (versions.camera == cameraRevision) polled.virtualCamActive
                else current.virtualCamActive,
            bitrateKbps = if (streamFresh) polled.bitrateKbps else current.bitrateKbps,
        )
    }

    private fun applyOutputEvent(received: ObsSessionEvent) =
        synchronized(outputLock) {
            val origin = _telemetry.value.session ?: return@synchronized
            if (received.token != origin || !isCurrent(origin)) return@synchronized
            val event = received.event
            // A profile switch can load a different canvas frame rate.
            if (event is ObsEvent.CurrentProfileChanged) canvasFrameRate.markStale()
            applyOutputStateEvent(event)
        }

    private fun applyOutputStateEvent(event: ObsEvent) =
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
                            receivedAt = current.receivedAt.copy(stream = null),
                        )
                    }
                }
                is ObsEvent.RecordStateChanged -> {
                    recordRevision++
                    _telemetry.update { current ->
                        current.copy(
                            connection = connection,
                            record = recordFromEvent(current.record, event),
                            receivedAt = current.receivedAt.copy(record = null),
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

    /**
     * Connection-scoped canvas FPS cache. OBS has no video-settings event, so it re-reads
     * `GetVideoSettings` on connect, after a profile switch and every [REFRESH_POLLS] polls. A
     * failed read keeps the last known value.
     */
    private class CanvasFrameRate(private val client: ObsClient) {
        private val stale = AtomicBoolean(true)
        private var pollsSinceRead = 0

        @Volatile private var fps: Double? = null

        fun reset() {
            fps = null
            pollsSinceRead = 0
            stale.set(true)
        }

        fun markStale() = stale.set(true)

        suspend fun refreshIfDue(): Double? {
            pollsSinceRead++
            if (stale.getAndSet(false) || pollsSinceRead >= REFRESH_POLLS) {
                pollsSinceRead = 0
                val read = requestResult { client.getVideoSettings() }.getOrNull()?.fps
                if (read != null) {
                    fps = read
                } else if (fps == null) {
                    // Still unknown: retry on the next 1 Hz poll instead of judging a healthy
                    // canvas against the 60 fps fallback for the whole refresh period.
                    stale.set(true)
                }
            }
            return fps
        }

        private companion object {
            const val REFRESH_POLLS = 10
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
