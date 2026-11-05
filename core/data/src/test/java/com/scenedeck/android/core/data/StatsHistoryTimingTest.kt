package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsHistoryTimingTest {
    @Test
    fun successfulReadsHaveTimingWhileEventsAndFailedReadsInvalidateIt() = runTest {
        var fail = false
        val client =
            object : FakeObsClient() {
                override suspend fun getVirtualCamStatus(): Boolean {
                    check(!fail)
                    return false
                }
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        assertEquals(0L, repository.telemetry.value.virtualCamReadStartedAtMs)
        assertEquals(0L, repository.telemetry.value.receivedAt.virtualCam)
        assertEquals(0L, repository.telemetry.value.streamReadStartedAtMs)
        client.emit(ObsEvent.VirtualcamStateChanged(true, ""))
        client.emit(ObsEvent.StreamStateChanged(true, "", reconnecting = true))
        runCurrent()
        assertEquals(true, repository.telemetry.value.virtualCamActive)
        assertNull(repository.telemetry.value.virtualCamReadStartedAtMs)
        assertNull(repository.telemetry.value.receivedAt.virtualCam)
        assertNull(repository.telemetry.value.streamReadStartedAtMs)
        assertEquals(true, repository.telemetry.value.stream?.reconnecting)
        fail = true
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(repository.telemetry.value.virtualCamReadStartedAtMs)
        assertNull(repository.telemetry.value.receivedAt.virtualCam)
    }

    @Test
    fun cameraEventWinsOverAnEarlierReadIncludingItsReceiptMetadata() = runTest {
        val response = CompletableDeferred<Boolean>()
        val client =
            object : FakeObsClient() {
                override suspend fun getVirtualCamStatus() = response.await()
            }
        client.setReady()
        val repository = StatsRepository(client, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        advanceTimeBy(10)
        client.emit(ObsEvent.VirtualcamStateChanged(true, ""))
        runCurrent()
        response.complete(false)
        runCurrent()
        assertEquals(true, repository.telemetry.value.virtualCamActive)
        assertNull(repository.telemetry.value.virtualCamReadStartedAtMs)
        assertNull(repository.telemetry.value.receivedAt.virtualCam)
    }
}
