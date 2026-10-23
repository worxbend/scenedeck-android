package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVideoSettings
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import kotlinx.coroutines.CompletableDeferred
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
        val client = FakeObsClient(statsResponse = statsResponse())
        client.streamStatusResponse = streamStatus()
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
        client.streamStatusResponse = streamStatus(bytes = 750_000)
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
        val client = FakeObsClient(statsResponse = statsResponse())
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

    @Test
    fun failedRequestClearsOnlyItsReadingAndOtherOutputsStillRefresh() = runTest {
        var failStats = false
        var failRecord = false
        val client =
            object : FakeObsClient(statsResponse = statsResponse()) {
                override suspend fun getStats(): ObsStats {
                    check(!failStats)
                    return super.getStats()
                }

                override suspend fun getRecordStatus(): RecordStatus {
                    check(!failRecord)
                    return super.getRecordStatus()
                }
            }
        client.streamStatusResponse = streamStatus()
        client.recordStatusResponse = RecordStatus(true, false, "00:00:01.000", 1000, 42)
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        runCurrent()
        assertEquals(true, repository.telemetry.value.record?.active)
        failStats = true
        failRecord = true
        client.streamStatusResponse = streamStatus().copy(active = false)
        advanceTimeBy(1100)
        runCurrent()
        assertEquals(null, repository.telemetry.value.stats)
        assertEquals(null, repository.telemetry.value.record)
        assertEquals(false, repository.telemetry.value.stream?.active)
    }

    @Test
    fun outputEventUpdatesImmediatelyAndWinsAgainstOlderInflightPoll() = runTest {
        val response = CompletableDeferred<StreamStatus>()
        val client =
            object : FakeObsClient() {
                override suspend fun getStreamStatus(): StreamStatus = response.await()
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        runCurrent()
        client.emit(ObsEvent.StreamStateChanged(true, "OBS_WEBSOCKET_OUTPUT_STARTED"))
        client.emit(ObsEvent.RecordStateChanged(true, "OBS_WEBSOCKET_OUTPUT_STARTED", null))
        runCurrent()
        assertEquals(true, repository.telemetry.value.stream?.active)
        assertEquals(true, repository.telemetry.value.record?.active)
        response.complete(streamStatus().copy(active = false))
        runCurrent()
        assertEquals(true, repository.telemetry.value.stream?.active)
        client.setDisconnected()
        runCurrent()
        assertEquals(null, repository.telemetry.value.stream)
        client.emit(ObsEvent.StreamStateChanged(true, "OBS_WEBSOCKET_OUTPUT_STARTED"))
        runCurrent()
        assertEquals(null, repository.telemetry.value.stream)
    }

    @Test
    fun startEventsResetPreviousOutputTimerAndRecordPauseIsImmediate() = runTest {
        val client = FakeObsClient()
        client.streamStatusResponse = streamStatus().copy(active = false)
        client.recordStatusResponse = RecordStatus(false, false, "00:00:20.000", 20000, 1234)
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        runCurrent()
        client.emit(ObsEvent.StreamStateChanged(true, "OBS_WEBSOCKET_OUTPUT_STARTED"))
        client.emit(ObsEvent.RecordStateChanged(true, "OBS_WEBSOCKET_OUTPUT_STARTED", null))
        runCurrent()
        assertEquals(0L, repository.telemetry.value.stream?.durationMs)
        assertEquals("00:00:00.000", repository.telemetry.value.record?.timecode)
        client.emit(ObsEvent.RecordStateChanged(true, "OBS_WEBSOCKET_OUTPUT_PAUSED", null))
        runCurrent()
        assertEquals(true, repository.telemetry.value.record?.paused)
        client.emit(ObsEvent.RecordStateChanged(true, "OBS_WEBSOCKET_OUTPUT_RESUMED", null))
        runCurrent()
        assertEquals(false, repository.telemetry.value.record?.paused)
    }

    @Test
    fun canvasFrameRateReadOnConnectRefreshedOnProfileSwitchAndPeriodically() = runTest {
        val client = FakeObsClient(statsResponse = statsResponse())
        client.videoSettingsResponse = videoSettings(30, 1)
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        runCurrent()
        assertEquals(30.0, repository.telemetry.value.targetFps)
        assertEquals(1, client.videoSettingsCalls)

        // Steady polling does not re-read video settings every second.
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(1, client.videoSettingsCalls)

        // A profile switch can change the canvas; the next poll re-reads it.
        client.videoSettingsResponse = videoSettings(60_000, 1_001)
        client.emit(ObsEvent.CurrentProfileChanged("Studio"))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, client.videoSettingsCalls)
        assertEquals(59.94, repository.telemetry.value.targetFps ?: 0.0, 1e-2)

        // No event exists for edits in OBS settings, so the value is also refreshed periodically.
        client.videoSettingsResponse = videoSettings(60, 1)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, client.videoSettingsCalls)
        assertEquals(60.0, repository.telemetry.value.targetFps)
    }

    @Test
    fun failedCanvasFrameRateReadKeepsLastKnownValueAndClearsOnDisconnect() = runTest {
        val client = FakeObsClient(statsResponse = statsResponse())
        client.videoSettingsResponse = videoSettings(30, 1)
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        runCurrent()

        client.videoSettingsResponse = null
        client.emit(ObsEvent.CurrentProfileChanged("Broken"))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, client.videoSettingsCalls)
        assertEquals(30.0, repository.telemetry.value.targetFps)

        client.setDisconnected()
        runCurrent()
        assertEquals(null, repository.telemetry.value.targetFps)
    }

    @Test
    fun unknownCanvasFrameRateWhenVideoSettingsUnavailable() = runTest {
        val client = FakeObsClient(statsResponse = statsResponse())
        client.setReady()
        val repository = StatsRepository(client, backgroundScope)
        runCurrent()
        assertEquals(null, repository.telemetry.value.targetFps)
        assertEquals(59.94, repository.telemetry.value.stats?.activeFps)
    }

    private fun videoSettings(numerator: Int, denominator: Int) =
        ObsVideoSettings(numerator, denominator, 1920, 1080, 1280, 720)

    private fun statsResponse() =
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

    private fun streamStatus(bytes: Long = 0) =
        StreamStatus(
            active = true,
            reconnecting = false,
            timecode = "00:00:10.000",
            durationMs = 10_000,
            bytes = bytes,
            congestion = 0.1,
            skippedFrames = 1,
            totalFrames = 500,
        )
}
