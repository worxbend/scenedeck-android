package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.RecordingPhase
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.ObsRequestTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingToolsRepositoryTest {
    @Test
    fun pauseWaitsForSharedConfirmationAndDuplicateIsRejected() = runTest {
        val f = fixture()
        val origin = f.origin()
        val operation = async { f.tools.perform(origin, RecordingAction.PAUSE) }
        runCurrent()
        assertEquals(RecordingAction.PAUSE, f.tools.state.value.pending)
        assertFalse(f.tools.state.value.record!!.paused)
        assertFalse(f.tools.perform(origin, RecordingAction.PAUSE))
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(operation.await())
        assertTrue(f.tools.state.value.record!!.paused)
        assertEquals(RecordingResult.CONFIRMED, f.tools.state.value.notice?.result)
        assertEquals(listOf(RecordingAction.PAUSE), f.client.writes)
    }

    @Test
    fun resumeUsesTargetValueAndConfirmsFromPoll() = runTest {
        val f = fixture(paused = true)
        val operation = async { f.tools.perform(f.origin(), RecordingAction.RESUME) }
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        operation.await()
        assertFalse(f.tools.state.value.record!!.paused)
        assertEquals(listOf(RecordingAction.RESUME), f.client.writes)
    }

    @Test
    fun unsupportedAndPausedAdvancedActionsNeverSendWrites() = runTest {
        val f =
            fixture(
                paused = true,
                capabilities = ObsCapabilities(pauseRecording = true, resumeRecording = true),
            )
        assertFalse(f.tools.perform(f.origin(), RecordingAction.SPLIT))
        assertFalse(f.tools.perform(f.origin(), RecordingAction.CHAPTER))
        assertFalse(f.tools.perform(f.origin(), RecordingAction.PAUSE))
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun chapterDraftIsBoundedTrimmedAndAcknowledged() = runTest {
        val f = fixture()
        assertFalse(f.tools.perform(f.origin(), RecordingAction.CHAPTER, "x".repeat(121)))
        assertTrue(f.tools.perform(f.origin(), RecordingAction.CHAPTER, "  Guest intro  "))
        assertEquals("Guest intro", f.client.chapter)
        assertEquals(RecordingResult.ACCEPTED, f.tools.state.value.notice?.result)
    }

    @Test
    fun formatFailureIsSanitizedAndDoesNotRetry() = runTest {
        val f = fixture()
        f.client.failure =
            ObsRequestFailedException(
                "CreateRecordChapter",
                "RequestProcessingFailed",
                "private/path password",
            )
        f.tools.perform(f.origin(), RecordingAction.CHAPTER)
        assertEquals(RecordingResult.REJECTED, f.tools.state.value.notice?.result)
        assertFalse(f.tools.state.value.toString().contains("private/path"))
        assertEquals(1, f.client.writes.size)
    }

    @Test
    fun timeoutHasUnknownOutcomeWithoutRetry() = runTest {
        val f = fixture()
        f.client.failure = ObsRequestTimeoutException()
        f.tools.perform(f.origin(), RecordingAction.SPLIT)
        assertEquals(RecordingResult.UNKNOWN, f.tools.state.value.notice?.result)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, f.client.writes.size)
    }

    @Test
    fun acknowledgedPauseWithoutConfirmationRemainsUnknown() = runTest {
        val f = fixture()
        f.client.updatePaused = false
        val operation = async { f.tools.perform(f.origin(), RecordingAction.PAUSE) }
        runCurrent()
        advanceTimeBy(3_501)
        runCurrent()
        operation.await()
        assertEquals(RecordingResult.UNKNOWN, f.tools.state.value.notice?.result)
        assertFalse(f.tools.state.value.record!!.paused)
        assertEquals(1, f.client.writes.size)
    }

    @Test
    fun cancellationPropagatesAndReleasesPendingSlot() = runTest {
        val f = fixture()
        f.client.gate = CompletableDeferred()
        val operation = async { f.tools.perform(f.origin(), RecordingAction.SPLIT) }
        runCurrent()
        operation.cancel()
        runCurrent()
        assertTrue(operation.isCancelled)
        assertNull(f.tools.state.value.pending)
        assertEquals(RecordingResult.UNKNOWN, f.tools.state.value.notice?.result)
    }

    @Test
    fun newTargetInvalidatesOldCommandsSynchronously() = runTest {
        val f = fixture()
        val origin = f.origin()
        f.active.begin(8)
        assertFalse(f.tools.perform(origin, RecordingAction.SPLIT))
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun replacementDuringPendingActionCannotPublishOldSuccess() = runTest {
        val f = fixture()
        f.client.gate = CompletableDeferred()
        val operation = async { f.tools.perform(f.origin(), RecordingAction.SPLIT) }
        runCurrent()
        f.active.confirm(f.active.begin(8))
        f.client.setReady(sessionId = 2, capabilities = ALL_CAPABILITIES)
        runCurrent()
        f.client.gate!!.complete(Unit)
        runCurrent()
        operation.join()
        assertTrue(operation.isCancelled)
        assertNull(f.tools.state.value.notice)
        assertEquals(1, f.client.writes.size)
    }

    @Test
    fun stoppedRecordingInvalidatesOldSheetAndPendingSuccess() = runTest {
        val f = fixture()
        val origin = f.origin()
        f.client.gate = CompletableDeferred()
        val operation = async { f.tools.perform(origin, RecordingAction.SPLIT) }
        runCurrent()
        f.client.recordStatusResponse = f.client.recordStatusResponse.copy(active = false)
        f.client.emit(
            ObsEvent.RecordStateChanged(
                false,
                "OBS_WEBSOCKET_OUTPUT_STOPPED",
                null,
                RecordingPhase.STOPPED,
            )
        )
        runCurrent()
        f.client.gate!!.complete(Unit)
        runCurrent()
        operation.join()
        assertTrue(operation.isCancelled)
        assertFalse(f.tools.perform(origin, RecordingAction.SPLIT))
        assertNull(f.tools.state.value.notice)
        assertNull(f.tools.state.value.record)
    }

    @Test
    fun prewriteStatusChangeRejectsStaleIntent() = runTest {
        val f = fixture()
        f.client.recordStatusResponse = f.client.recordStatusResponse.copy(paused = true)
        f.tools.perform(f.origin(), RecordingAction.PAUSE)
        assertTrue(f.client.writes.isEmpty())
        assertEquals(RecordingResult.CHANGED, f.tools.state.value.notice?.result)
    }

    @Test
    fun readStartedBeforeWriteCannotConfirmItsOutcome() = runTest {
        val f = fixture()
        f.client.updatePaused = false
        val staleRead = CompletableDeferred<RecordStatus>()
        f.client.nextRead = staleRead
        advanceTimeBy(1_100)
        runCurrent()
        val operation = async { f.tools.perform(f.origin(), RecordingAction.PAUSE) }
        runCurrent()
        advanceTimeBy(100)
        staleRead.complete(f.client.recordStatusResponse.copy(paused = true))
        runCurrent()
        assertEquals(RecordingAction.PAUSE, f.tools.state.value.pending)
        advanceTimeBy(3_401)
        runCurrent()
        operation.await()
        assertEquals(RecordingResult.UNKNOWN, f.tools.state.value.notice?.result)
    }

    @Test
    fun recordingBoundaryCancelsAQueuedWriteBeforeDispatch() = runTest {
        val f = fixture()
        f.client.admission = CompletableDeferred()
        val operation = async { f.tools.perform(f.origin(), RecordingAction.SPLIT) }
        runCurrent()
        f.client.emit(ObsEvent.RecordStateChanged(false, "", null, RecordingPhase.STOPPING))
        runCurrent()
        f.client.admission!!.complete(Unit)
        operation.join()
        assertTrue(operation.isCancelled)
        assertTrue(f.client.writes.isEmpty())
        assertNull(f.tools.state.value.pending)
        assertFalse(f.tools.state.value.busy)
    }

    private fun TestScope.fixture(
        paused: Boolean = false,
        capabilities: ObsCapabilities = ALL_CAPABILITIES,
    ): Fixture {
        val client = RecordingClient()
        client.recordStatusResponse = RecordStatus(true, paused, "", 1_000, 0)
        client.setReady(capabilities = capabilities)
        val active = ActiveConnection().apply { confirm(begin(7)) }
        val clock = MonotonicClock { testScheduler.currentTime }
        val stats = StatsRepository(client, backgroundScope, clock)
        val tools = RecordingToolsRepository(client, stats, active, clock, backgroundScope)
        runCurrent()
        return Fixture(client, active, tools)
    }

    private data class Fixture(
        val client: RecordingClient,
        val active: ActiveConnection,
        val tools: RecordingToolsRepository,
    ) {
        fun origin() = checkNotNull(tools.state.value.origin)
    }

    private class RecordingClient : FakeObsClient() {
        val writes = mutableListOf<RecordingAction>()
        var chapter: String? = null
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var admission: CompletableDeferred<Unit>? = null
        var updatePaused = true
        var nextRead: CompletableDeferred<RecordStatus>? = null

        override suspend fun getRecordStatus(): RecordStatus {
            val pending = nextRead
            nextRead = null
            return pending?.await() ?: super.getRecordStatus()
        }

        private suspend fun write(action: RecordingAction) {
            admission?.await()
            writes += action
            gate?.await()
            failure?.let { throw it }
            if (
                updatePaused &&
                    (action == RecordingAction.PAUSE || action == RecordingAction.RESUME)
            )
                recordStatusResponse =
                    recordStatusResponse.copy(paused = action == RecordingAction.PAUSE)
        }

        override suspend fun pauseRecord() = write(RecordingAction.PAUSE)

        override suspend fun resumeRecord() = write(RecordingAction.RESUME)

        override suspend fun splitRecordFile() = write(RecordingAction.SPLIT)

        override suspend fun createRecordChapter(chapterName: String?) {
            chapter = chapterName
            write(RecordingAction.CHAPTER)
        }
    }

    companion object {
        private val ALL_CAPABILITIES = ObsCapabilities(true, true, true, true, true)
    }
}
