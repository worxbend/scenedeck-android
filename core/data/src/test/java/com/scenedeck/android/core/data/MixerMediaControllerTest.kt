package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.ObsRequestTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MixerMediaControllerTest {
    @Test
    fun seekClampsBothBoundsAndReconcilesReportedPosition() = runTest {
        val f = fixture()
        assertTrue(f.media.seek(f.target(), Long.MAX_VALUE))
        assertEquals(listOf(60_000L), f.client.writes)
        assertEquals(MediaSeekResult.CONFIRMED, f.reading().result)
        assertEquals(0L, f.reading().remainingMs)
        assertTrue(f.media.seek(f.target(), Long.MIN_VALUE))
        assertEquals(listOf(60_000L, 0L), f.client.writes)
        assertEquals(0L, f.reading().cursorMs)
    }

    @Test
    fun unknownInvalidAndNonPlayingTimesCannotSeek() = runTest {
        val f = fixture()
        listOf(
                MediaStatus(MediaStateKind.PLAYING, null, 100),
                MediaStatus(MediaStateKind.PLAYING, 0, 0),
                MediaStatus(MediaStateKind.PLAYING, 100, -1),
                MediaStatus(MediaStateKind.PLAYING, 100, 101),
                MediaStatus(MediaStateKind.BUFFERING, 100, 1),
                MediaStatus(MediaStateKind.STOPPED, 100, 1),
            )
            .forEach { status ->
                f.client.status = status
                f.media.refresh(TEST_WORKSPACE, "Clip")
                assertFalse(f.media.seek(f.target(), 50))
            }
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun unsupportedRequestAndExpiredReadNeverWrite() = runTest {
        val f = fixture()
        f.client.setReady()
        runCurrent()
        assertFalse(f.media.seek(f.target(), 100))
        f.client.setReady(capabilities = ObsCapabilities(mediaSeek = true))
        f.client.readGate = CompletableDeferred()
        advanceTimeBy(6_001)
        runCurrent()
        assertNull(f.reading().status)
        assertFalse(f.media.seek(f.target(), 100))
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun failedPollClearsOldPositionAndOlderReadCannotRestoreIt() = runTest {
        val f = fixture()
        val gate = CompletableDeferred<Unit>()
        f.client.readGate = gate
        val older = async { f.media.refresh(TEST_WORKSPACE, "Clip") }
        runCurrent()
        f.client.readGate = null
        f.client.readFailure = IllegalStateException("private password")
        f.media.refresh(TEST_WORKSPACE, "Clip")
        assertNull(f.reading().status)
        gate.complete(Unit)
        older.await()
        assertNull(f.reading().status)
        assertFalse(f.media.state.value.toString().contains("private password"))
    }

    @Test
    fun changedDurationBeforeDispatchRejectsOldGesture() = runTest {
        val f = fixture()
        f.client.status = f.client.status.copy(durationMs = 30_000)
        assertTrue(f.media.seek(f.target(), 20_000))
        assertEquals(MediaSeekResult.CHANGED, f.reading().result)
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun durationChangedBeforeIntentAdmissionCannotRetargetTheGesture() = runTest {
        val f = fixture()
        f.client.status = f.client.status.copy(durationMs = 30_000)
        f.media.refresh(TEST_WORKSPACE, "Clip")
        assertFalse(f.media.seek(f.target(), 20_000, expectedDurationMs = 60_000))
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun pausedSeekStaysPausedAndDuplicateCannotSendAnotherWrite() = runTest {
        val f = fixture()
        f.client.status = f.client.status.copy(state = MediaStateKind.PAUSED)
        val gate = CompletableDeferred<Unit>()
        f.client.writeGate = gate
        val operation = async { f.media.seek(f.target(), 20_000) }
        runCurrent()
        assertTrue(f.reading().seeking)
        assertFalse(f.media.seek(f.target(), 30_000))
        gate.complete(Unit)
        operation.await()
        assertEquals(listOf(20_000L), f.client.writes)
        assertEquals(MediaStateKind.PAUSED, f.reading().status?.state)
        assertFalse(f.reading().seeking)
    }

    @Test
    fun acknowledgedButUnchangedPositionIsNotConfirmed() = runTest {
        val f = fixture()
        f.client.applyWrite = false
        f.media.seek(f.target(), 45_000)
        assertEquals(MediaSeekResult.DIFFERENT, f.reading().result)
        assertEquals(1_000L, f.reading().cursorMs)
    }

    @Test
    fun missingPostWriteReadIsUnknownAndNeverRetried() = runTest {
        val f = fixture()
        f.client.failAfterWrite = true
        f.media.seek(f.target(), 20_000)
        assertEquals(MediaSeekResult.UNKNOWN, f.reading().result)
        assertNull(f.reading().status)
        assertEquals(listOf(20_000L), f.client.writes)
    }

    @Test
    fun writeRejectionAndTimeoutAreDistinctSanitizedOutcomes() = runTest {
        val f = fixture()
        f.client.writeFailure =
            ObsRequestFailedException("SetMediaInputCursor", "Rejected", "password path")
        f.media.seek(f.target(), 20_000)
        assertEquals(MediaSeekResult.REJECTED, f.reading().result)
        f.client.writeFailure = ObsRequestTimeoutException()
        f.media.seek(f.target(), 30_000)
        assertEquals(MediaSeekResult.UNKNOWN, f.reading().result)
        assertEquals(2, f.client.writes.size)
        assertFalse(f.media.state.value.toString().contains("password"))
    }

    @Test
    fun removedSourceCancelsQueuedSeekAndCannotBeResurrectedByLateRead() = runTest {
        val f = fixture()
        val oldTarget = f.target()
        f.client.writeGate = CompletableDeferred()
        val operation = async { f.media.seek(oldTarget, 20_000) }
        runCurrent()
        f.client.emit(ObsEvent.InputRemoved("Clip"))
        runCurrent()
        assertTrue(operation.isCancelled)
        assertTrue(f.client.writes.isEmpty())
        assertTrue(f.media.state.value.readings.isEmpty())
        assertFalse(f.media.seek(oldTarget, 20_000))
    }

    @Test
    fun playbackBoundaryInvalidatesHeldGestureAndPendingWrite() = runTest {
        val f = fixture()
        val old = f.target()
        f.client.writeGate = CompletableDeferred()
        val operation = async { f.media.seek(old, 20_000) }
        runCurrent()
        f.client.emit(ObsEvent.MediaInputPlaybackStarted("Clip"))
        runCurrent()
        assertTrue(operation.isCancelled)
        assertNotEquals(old, f.target())
        assertFalse(f.media.seek(old, 20_000))
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun callerCancellationReleasesPendingAndDoesNotRetry() = runTest {
        val f = fixture()
        f.client.writeGate = CompletableDeferred()
        val operation = async { f.media.seek(f.target(), 20_000) }
        runCurrent()
        operation.cancel()
        operation.join()
        assertFalse(f.reading().seeking)
        assertEquals(MediaSeekResult.UNKNOWN, f.reading().result)
        assertTrue(f.client.writes.isEmpty())
    }

    @Test
    fun disconnectCancelsSeekAndClearsOldContext() = runTest {
        val f = fixture()
        f.client.writeGate = CompletableDeferred()
        val operation = async { f.media.seek(f.target(), 20_000) }
        runCurrent()
        f.workspaces.unavailable()
        f.client.setDisconnected()
        runCurrent()
        assertTrue(operation.isCancelled)
        assertNull(f.media.state.value.workspace)
        assertTrue(f.media.state.value.readings.isEmpty())
    }

    @Test
    fun focusedOffProgramSourceUsesSharedPollAndReleasesItsLease() = runTest {
        val f = fixture()
        val lease = f.media.watch(TEST_WORKSPACE, "Off program")
        runCurrent()
        assertTrue(f.media.state.value.inputs.containsKey("Off program"))
        lease.close()
        runCurrent()
        assertFalse(f.media.state.value.readings.containsKey("Off program"))
        assertTrue(f.media.state.value.readings.containsKey("Clip"))
    }

    private fun TestScope.fixture(): Fixture {
        val client = MediaClient()
        client.setReady(capabilities = ObsCapabilities(mediaSeek = true))
        val workspaces = MutableWorkspaceContext()
        val sources = MutableStateFlow(MediaSources(TEST_WORKSPACE, setOf("Clip")))
        val media =
            MixerMediaController(
                client,
                workspaces,
                MonotonicClock { testScheduler.currentTime },
                backgroundScope,
                sources,
            )
        backgroundScope.launch { media.state.collect {} }
        runCurrent()
        return Fixture(client, media, workspaces)
    }

    private data class Fixture(
        val client: MediaClient,
        val media: MixerMediaController,
        val workspaces: MutableWorkspaceContext,
    ) {
        fun reading() = media.state.value.readings.getValue("Clip")

        fun target() = reading().target
    }

    private class MediaClient : FakeObsClient() {
        var status = MediaStatus(MediaStateKind.PLAYING, 60_000, 1_000)
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        var applyWrite = true
        var failAfterWrite = false
        val writes = mutableListOf<Long>()

        override suspend fun getMediaInputStatus(inputName: String): MediaStatus {
            val captured = status
            val failure = readFailure
            readGate?.await()
            failure?.let { throw it }
            return captured
        }

        override suspend fun setMediaInputCursor(inputName: String, cursorMs: Long) {
            writeGate?.await()
            writes.add(cursorMs)
            writeFailure?.let { throw it }
            if (applyWrite) status = status.copy(cursorMs = cursorMs)
            if (failAfterWrite) readFailure = IllegalStateException("transport details")
        }
    }
}
