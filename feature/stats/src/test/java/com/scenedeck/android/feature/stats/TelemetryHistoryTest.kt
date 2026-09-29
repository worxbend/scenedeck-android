package com.scenedeck.android.feature.stats

import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryHistoryTest {

    private fun sample(fps: Float = 60f) = TelemetrySample(
        fps = fps,
        renderTimeMs = 1f,
        droppedPct = 0f,
        congestionPct = 0f,
        bitrateKbps = 0,
        renderSkippedDelta = 0,
        outputSkippedDelta = 0,
        renderSkippedTotal = 0,
        outputSkippedTotal = 0,
    )

    @Test
    fun capsAtCapacityAndEvictsOldest() {
        val history = TelemetryHistory(capacity = 120)
        repeat(130) { history.add(sample(fps = it.toFloat())) }

        assertEquals(120, history.size)
        val samples = history.toList()
        assertEquals(10f, samples.first().fps, 1e-4f) // 0..9 evicted
        assertEquals(129f, samples.last().fps, 1e-4f)
        assertEquals(129f, history.latest()?.fps ?: -1f, 1e-4f)
    }

    @Test
    fun keepsInsertionOrderBelowCapacity() {
        val history = TelemetryHistory(capacity = 120)
        repeat(5) { history.add(sample(fps = it.toFloat())) }

        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), history.toList().map { it.fps })
    }

    @Test
    fun clearResetsTheWindow() {
        val history = TelemetryHistory()
        history.add(sample())
        history.clear()
        assertEquals(0, history.size)
        assertEquals(null, history.latest())
    }

    @Test
    fun telemetryMappingDerivesPercentagesAndDeltas() {
        val first = telemetry(
            stats = obsStats(renderSkipped = 2, outputSkipped = 7),
            stream = streamStatus(skipped = 5, total = 500, congestion = 0.25),
        ).toSample(previous = null)
        assertEquals(1f, first.droppedPct, 1e-4f) // 5/500 × 100
        assertEquals(25f, first.congestionPct, 1e-4f)
        assertEquals(0, first.renderSkippedDelta) // first sample: no baseline
        assertEquals(0, first.outputSkippedDelta)

        val second = telemetry(
            stats = obsStats(renderSkipped = 5, outputSkipped = 9),
            stream = streamStatus(skipped = 15, total = 1000, congestion = 0.0),
        ).toSample(previous = first)
        assertEquals(1.5f, second.droppedPct, 1e-4f) // 15/1000 × 100
        assertEquals(3, second.renderSkippedDelta)
        assertEquals(2, second.outputSkippedDelta)
    }

    @Test
    fun counterResetNeverYieldsNegativeDeltas() {
        val first = telemetry(stats = obsStats(renderSkipped = 10, outputSkipped = 10))
            .toSample(previous = null)
        val second = telemetry(stats = obsStats(renderSkipped = 3, outputSkipped = 1))
            .toSample(previous = first) // OBS restarted → counters reset
        assertEquals(0, second.renderSkippedDelta)
        assertEquals(0, second.outputSkippedDelta)
    }

    private fun telemetry(
        stats: ObsStats = obsStats(),
        stream: StreamStatus = streamStatus(),
        bitrateKbps: Int = 0,
    ) = Telemetry(
        connection = com.scenedeck.android.core.model.ConnectionState.Ready(
            com.scenedeck.android.core.model.ObsVersionInfo("31.0.1", "5.6.1", 1, "test"),
        ),
        stats = stats,
        stream = stream,
        record = null,
        bitrateKbps = bitrateKbps,
    )

    private fun obsStats(renderSkipped: Int = 0, outputSkipped: Int = 0) = ObsStats(
        cpuUsage = 10.0,
        memoryUsageMb = 256.0,
        availableDiskSpaceMb = 1_000.0,
        activeFps = 60.0,
        averageFrameRenderTimeMs = 1.0,
        renderSkippedFrames = renderSkipped,
        renderTotalFrames = 10_000,
        outputSkippedFrames = outputSkipped,
        outputTotalFrames = 9_000,
    )

    private fun streamStatus(skipped: Int = 0, total: Int = 0, congestion: Double = 0.0) =
        StreamStatus(
            active = true,
            reconnecting = false,
            timecode = "00:00:01.000",
            durationMs = 1_000,
            bytes = 0,
            congestion = congestion,
            skippedFrames = skipped,
            totalFrames = total,
        )
}
