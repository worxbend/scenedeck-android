package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepositoryTest {

    @Test
    fun pollsAt1HzWhileReadyAndComputesRollingBitrate() = runTest {
        var bytes = 0L
        val client =
            FakeObsClient(
                statsResponse =
                    ObsStats(
                        cpuUsage = 12.5,
                        memoryUsageMb = 512.0,
                        availableDiskSpaceMb = 1000.0,
                        activeFps = 59.94,
                        averageFrameRenderTimeMs = 1.2,
                        renderSkippedFrames = 2,
                        renderTotalFrames = 10000,
                        outputSkippedFrames = 7,
                        outputTotalFrames = 9000,
                    )
            )
        client.streamStatusResponse =
            StreamStatus(
                active = true,
                reconnecting = false,
                timecode = "00:00:10.000",
                durationMs = 10_000,
                bytes = 0,
                congestion = 0.1,
                skippedFrames = 1,
                totalFrames = 500,
            )
        client.setReady()
        var virtualNow = 0L
        val repository =
            StatsRepository(client, backgroundScope).apply {
                nowMs = {
                    virtualNow += 1_000L
                    virtualNow
                }
            }

        // First sample: baseline, bitrate unknown.
        testScheduler.runCurrent()
        val first = repository.telemetry.value
        assertEquals(59.94, first.stats?.activeFps)
        assertEquals(7, first.stats?.outputSkippedFrames)
        assertEquals(0, first.bitrateKbps)

        // +1 s of virtual time: OBS reports 750_000 more bytes → 750_000*8/1000 = 6000 kbit/s.
        bytes = 750_000
        client.streamStatusResponse = client.streamStatusResponse.copy(bytes = bytes)
        advanceTimeBy(1_100)
        testScheduler.runCurrent()

        val second = repository.telemetry.value
        assertEquals(6_000, second.bitrateKbps)
        assertTrue(second.connection is ConnectionState.Ready)
    }

    @Test
    fun stopsPollingOnDisconnect(): Unit = runTest {
        val client = FakeObsClient()
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        testScheduler.runCurrent()

        client.setDisconnected()
        testScheduler.runCurrent()

        assertEquals(
            ConnectionState.Disconnected,
            repository.telemetry.value.connection,
        )
        assertEquals(null, repository.telemetry.value.stats)
    }

    @Test
    fun recordStatusIncludedInTelemetry() = runTest {
        val client =
            FakeObsClient(
                recordStatusResponse =
                    RecordStatus(
                        active = true,
                        paused = false,
                        timecode = "00:00:05.000",
                        durationMs = 5_000,
                        bytes = 1234,
                    )
            )
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        testScheduler.runCurrent()

        assertEquals(true, repository.telemetry.value.record?.active)
        assertEquals("00:00:05.000", repository.telemetry.value.record?.timecode)
    }

    @Test
    fun ringBufferAccumulatesWhileReadyAndClearsOnDisconnect() = runTest {
        val client =
            FakeObsClient(
                statsResponse =
                    ObsStats(
                        cpuUsage = 12.5,
                        memoryUsageMb = 512.0,
                        availableDiskSpaceMb = 1000.0,
                        activeFps = 59.94,
                        averageFrameRenderTimeMs = 1.2,
                        renderSkippedFrames = 2,
                        renderTotalFrames = 10000,
                        outputSkippedFrames = 7,
                        outputTotalFrames = 9000,
                    )
            )
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)

        testScheduler.runCurrent()
        assertEquals(1, repository.samples.value.size)
        assertEquals(59.94f, repository.samples.value.last().fps, 1e-3f)

        advanceTimeBy(1_100)
        testScheduler.runCurrent()
        assertEquals(2, repository.samples.value.size)

        client.setDisconnected()
        testScheduler.runCurrent()
        assertTrue(repository.samples.value.isEmpty())
    }
}
