package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsSessionTest {
    @Test
    fun individualReceiptTimesDoNotMakeEarlierReadingsFreshAfterASlowBatch() = runTest {
        val client =
            object : FakeObsClient() {
                override suspend fun getStreamStatus(): StreamStatus {
                    delay(2_000)
                    return super.getStreamStatus()
                }

                override suspend fun getRecordStatus(): RecordStatus {
                    delay(3_000)
                    return super.getRecordStatus()
                }
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(ObsSessionToken(), repository.telemetry.value.session)
        assertEquals(TelemetryReadTimes(0, 2_000, 5_000), repository.telemetry.value.receivedAt)
    }

    @Test
    fun lateReadCannotPublishOrContinueRequestsOnReplacementSession() = runTest {
        val release = CompletableDeferred<Unit>()
        val streamRequests = mutableListOf<Long>()
        var calls = 0
        val client =
            object : FakeObsClient() {
                override suspend fun getStats(): ObsStats {
                    calls++
                    if (calls == 1) withContext(NonCancellable) { release.await() }
                    return super.getStats().copy(activeFps = calls.toDouble())
                }

                override suspend fun getStreamStatus(): StreamStatus {
                    streamRequests += (connectionState.value as ConnectionState.Ready).sessionId
                    return super.getStreamStatus()
                }
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        val replacement = ObsSessionToken(2, 0)
        client.setReady(sessionId = 2)
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals(replacement, repository.telemetry.value.session)
        assertEquals(2.0, repository.telemetry.value.stats?.activeFps)
        assertEquals(1, repository.samples.value.size)
        assertEquals(2f, repository.samples.value.single().fps)
        assertEquals(listOf(2L), streamRequests)
    }

    @Test
    fun collectionChangingClearsHistoryAndResumesOnlyAfterChangeCompletes() = runTest {
        var calls = 0
        val client =
            object : FakeObsClient() {
                override suspend fun getStats(): ObsStats {
                    calls++
                    return super.getStats()
                }
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        assertEquals(1, calls)
        client.setReady(collectionChanging = true)
        runCurrent()
        assertNull(repository.telemetry.value.session)
        assertNull(repository.telemetry.value.stats)
        assertTrue(repository.samples.value.isEmpty())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, calls)
        client.setReady(collectionRevision = 2)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, repository.samples.value.size)
    }

    @Test
    fun obsoleteBufferedEventsCannotChangeNewSessionAndEventsDoNotRefreshCounters() = runTest {
        val events = MutableSharedFlow<ObsSessionEvent>()
        val client =
            object : FakeObsClient() {
                override val eventsWithContext = events
            }
        val replacement = ObsSessionToken(2, 0)
        client.setReady(sessionId = 2)
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        val started = ObsEvent.StreamStateChanged(true, "OBS_WEBSOCKET_OUTPUT_STARTED")
        events.emit(ObsSessionEvent(ObsSessionToken(), started))
        runCurrent()
        assertEquals(false, repository.telemetry.value.stream?.active)
        assertEquals(0L, repository.telemetry.value.receivedAt.stream)
        events.emit(ObsSessionEvent(replacement, started))
        runCurrent()
        assertEquals(true, repository.telemetry.value.stream?.active)
        assertNull(repository.telemetry.value.receivedAt.stream)
        assertEquals(0L, repository.telemetry.value.receivedAt.stats)
    }

    @Test
    fun failedReadHasNoReceiptTimeAndDisconnectClearsAllTimes() = runTest {
        var fail = false
        val client =
            object : FakeObsClient() {
                override suspend fun getStats(): ObsStats {
                    check(!fail)
                    return super.getStats()
                }
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        fail = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(TelemetryReadTimes(null, 1_000, 1_000), repository.telemetry.value.receivedAt)
        client.setDisconnected()
        runCurrent()
        assertEquals(TelemetryReadTimes(), repository.telemetry.value.receivedAt)
        assertNull(repository.telemetry.value.session)
    }
}
