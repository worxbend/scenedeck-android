package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecorderTest : HistoryTestFixture() {
    @Test
    fun recordsRapidChangesAndReconnectionAsSeparateObservations(): Unit = runBlocking {
        withRecorder { recorder, source ->
            val initial = recorder.state.first { it.phase == HistoryCapturePhase.CAPTURING }
            val id = requireNotNull(initial.sessionId)
            listOf("Camera", "Talk", "Camera").forEach { name ->
                source.events.emit(
                    ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.CurrentProgramSceneChanged(name))
                )
            }
            val detail =
                repository.detail(id).first {
                    it is HistoryDetailState.Ready &&
                        it.detail.events.count { event ->
                            event.payload is HistoryPayload.Program
                        } == 4
                } as HistoryDetailState.Ready
            assertEquals(
                listOf(null, "Camera", "Talk", "Camera"),
                detail.detail.events
                    .mapNotNull { it.payload as? HistoryPayload.Program }
                    .map { it.sceneName },
            )
            source.inputs = HistoryInputs(null)
            source.changes.emit(Unit)
            recorder.state.first { it.phase == HistoryCapturePhase.WAITING }
            val closed =
                repository.detail(id).first {
                    it is HistoryDetailState.Ready && it.detail.session.endReason != null
                } as HistoryDetailState.Ready
            assertEquals(HistoryEndReason.DISCONNECTED, closed.detail.session.endReason)
            val next = TEST_WORKSPACE.copy(token = ObsSessionToken(2))
            source.inputs = HistoryInputs(next, connected = true)
            source.changes.emit(Unit)
            val reconnected =
                recorder.state.first {
                    it.phase == HistoryCapturePhase.CAPTURING && it.sessionId != id
                }
            assertNotEquals(id, reconnected.sessionId)
            assertNull(recorder.capture(TEST_WORKSPACE))
        }
    }

    @Test
    fun storageFailureStopsCaptureAndExplicitRetryStartsNewObservationWithGap(): Unit =
        runBlocking {
            withRecorder { recorder, source ->
                val first = recorder.state.first { it.phase == HistoryCapturePhase.CAPTURING }
                database.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER fail_history_capture BEFORE INSERT ON history_events " +
                        "BEGIN SELECT RAISE(ABORT, 'private failure'); END"
                )
                source.events.emit(
                    ObsSessionEvent(
                        TEST_WORKSPACE.token,
                        ObsEvent.CurrentProgramSceneChanged("Trigger failure"),
                    )
                )
                val failed = recorder.state.first { it.phase == HistoryCapturePhase.FAILED }
                assertEquals(HistoryCaptureFailure.STORAGE, failed.failure)
                assertNull(recorder.capture(TEST_WORKSPACE))
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_history_capture")
                recorder.retry()
                recorder.retry()
                val next = recorder.state.first { it.phase == HistoryCapturePhase.CAPTURING }
                assertNotEquals(first.sessionId, next.sessionId)
                val detail =
                    repository.detail(requireNotNull(next.sessionId)).first {
                        it is HistoryDetailState.Ready &&
                            it.detail.events.any { event -> event.payload is HistoryPayload.Gap }
                    } as HistoryDetailState.Ready
                assertTrue(
                    detail.detail.events.any {
                        it.payload == HistoryPayload.Gap(HistoryGapReason.OBSERVATION_INTERRUPTED)
                    }
                )
                val old =
                    repository
                        .exportSnapshot(requireNotNull(first.sessionId))
                        .observations
                        .single()
                        .session
                assertEquals(HistoryEndReason.STORAGE_FAILURE, old.endReason)
                assertNull(old.endedAtMs)
            }
        }

    @Test
    fun clearContinuesFreshWhileDisableStopsAutomaticCapture(): Unit = runBlocking {
        withRecorder { recorder, _ ->
            val first = recorder.state.first { it.phase == HistoryCapturePhase.CAPTURING }
            repository.delete()
            val next =
                recorder.state.first {
                    it.phase == HistoryCapturePhase.CAPTURING && it.sessionId != first.sessionId
                }
            assertEquals(
                listOf(next.sessionId),
                repository.exportSnapshot().observations.map { it.session.id },
            )
            repository.configure(false, 10)
            recorder.state.first { it.phase == HistoryCapturePhase.DISABLED }
            assertNull(recorder.capture(TEST_WORKSPACE))
            assertEquals(
                HistoryEndReason.CAPTURE_DISABLED,
                repository.exportSnapshot().observations.single().session.endReason,
            )
        }
    }

    private suspend fun withRecorder(block: suspend (HistoryRecorder, FakeHistorySource) -> Unit) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val source = FakeHistorySource()
        val recorder = HistoryRecorder(repository, source, { 1_000 }, { 10_000 }, scope)
        try {
            recorder.start()
            recorder.start()
            withTimeout(10_000) { block(recorder, source) }
        } finally {
            job.cancelAndJoin()
        }
    }
}
