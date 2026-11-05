package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCaptureEngineTest {
    private val source = FakeHistorySource()
    private var mono = 1_000L
    private val queue = HistoryCaptureQueue()
    private val engine = HistoryCaptureEngine(source, { mono }, { 10_000 }, queue)

    private fun begin(): HistoryCommand.Start {
        engine.storage(HistoryState.Ready(HistoryPreferences(), emptyList()))
        engine.activate(engine.owner)
        return queue.drain().filterIsInstance<HistoryCommand.Start>().single()
    }

    @Test
    fun bookmarkLinkRequiresPersistedStartAndCurrentWorkspace() {
        val start = begin()
        assertNull(engine.link(TEST_WORKSPACE))
        engine.completed(start, true)
        mono += 500
        assertEquals(HistoryBookmarkLink(start.seed.id, 500), engine.link(TEST_WORKSPACE))
        source.inputs = HistoryInputs(null)
        assertNull(engine.link(TEST_WORKSPACE))
        engine.refresh()
        assertEquals(HistoryCapturePhase.WAITING, engine.state.value.phase)
    }

    @Test
    fun replacementClosesOriginalAndRejectsItsBufferedEvents() {
        val start = begin()
        val next = TEST_WORKSPACE.copy(token = ObsSessionToken(2, 1))
        source.inputs = HistoryInputs(next, connected = true)
        engine.refresh(
            ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.CurrentProgramSceneChanged("Obsolete"))
        )
        val commands = queue.drain()
        assertEquals(
            start.seed.id,
            commands.filterIsInstance<HistoryCommand.Finish>().single().position.sessionId,
        )
        assertEquals(
            HistoryEndReason.WORKSPACE_CHANGED,
            commands.filterIsInstance<HistoryCommand.Finish>().single().reason,
        )
        assertNotEquals(
            start.seed.id,
            commands.filterIsInstance<HistoryCommand.Start>().single().seed.id,
        )
        assertFalse(
            commands.filterIsInstance<HistoryCommand.Append>().any {
                it.write.payload == HistoryPayload.Program("Obsolete", false)
            }
        )
    }

    @Test
    fun heartbeatDoesNotRepeatUnchangedSnapshotEvents() {
        begin()
        mono += 9_999
        engine.refresh()
        assertTrue(queue.drain().isEmpty())
        mono++
        engine.refresh()
        assertTrue(queue.drain().single() is HistoryCommand.Touch)
    }

    @Test
    fun deletionOfOlderObservationKeepsCurrentButClearUsesNewGeneration() {
        val start = begin()
        engine.completed(start, true)
        engine.storage(
            HistoryState.Ready(
                HistoryPreferences(generation = 1),
                listOf(start.seed.encodeHistory().decodeHistory()),
            )
        )
        assertEquals(start.seed.id, engine.state.value.sessionId)
        assertTrue(queue.drain().isEmpty())
        engine.storage(HistoryState.Ready(HistoryPreferences(generation = 2), emptyList()))
        val replacement = queue.drain().filterIsInstance<HistoryCommand.Start>().single()
        assertNotEquals(start.seed.id, replacement.seed.id)
        assertEquals(2L, replacement.seed.generation)
    }

    @Test
    fun failurePausesUntilExplicitRetryAndOldEpochCannotRestoreIt() {
        val start = begin()
        engine.completed(start, true)
        engine.fail(engine.owner, HistoryCaptureFailure.STORAGE)
        engine.refresh(
            ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.CurrentProgramSceneChanged("Ignored"))
        )
        assertTrue(queue.drain().isEmpty())
        assertNull(engine.link(TEST_WORKSPACE))
        val restart = requireNotNull(engine.restart())
        assertNotEquals(start.owner, restart.owner)
        engine.refresh(epoch = start.owner)
        engine.activate(restart.owner)
        val writes = queue.drain().filterIsInstance<HistoryCommand.Append>()
        assertTrue(
            writes.any {
                it.write.payload == HistoryPayload.Gap(HistoryGapReason.OBSERVATION_INTERRUPTED)
            }
        )
        engine.fail(start.owner, HistoryCaptureFailure.SOURCE_ENDED)
        assertNotEquals(HistoryCapturePhase.FAILED, engine.state.value.phase)
    }

    @Test
    fun boundedQueueExposesDroppedEntriesThroughAnOrdinalGap() {
        begin()
        repeat(150) { index ->
            engine.refresh(
                ObsSessionEvent(
                    TEST_WORKSPACE.token,
                    ObsEvent.CurrentProgramSceneChanged("Scene $index"),
                )
            )
        }
        val accepted = queue.drain().filterIsInstance<HistoryCommand.Append>()
        assertEquals(128, accepted.size)
        assertTrue(engine.state.value.missedEvents > 0)
        engine.refresh()
        val gap = queue.drain().filterIsInstance<HistoryCommand.Append>().single()
        assertTrue(gap.write.ordinal > accepted.last().write.ordinal + 1)
        assertEquals(
            HistoryPayload.Gap(HistoryGapReason.OBSERVATION_INTERRUPTED),
            gap.write.payload,
        )
    }

    @Test
    fun fullQueueAtContextBoundaryPausesInsteadOfSilentlyLosingTheBoundary() {
        begin()
        repeat(128) { index ->
            engine.refresh(
                ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.CurrentProgramSceneChanged("$index"))
            )
        }
        source.inputs = HistoryInputs(null)
        engine.refresh()
        assertEquals(HistoryCaptureFailure.OVERFLOW, engine.state.value.failure)
        assertEquals(HistoryCapturePhase.FAILED, engine.state.value.phase)
        assertNotNull(engine.restart())
    }

    @Test
    fun disabledHistoryDoesNotCaptureAndUnavailableWorkspaceIsWaiting() {
        engine.storage(HistoryState.Ready(HistoryPreferences(enabled = false), emptyList()))
        engine.activate(engine.owner)
        assertEquals(HistoryCapturePhase.DISABLED, engine.state.value.phase)
        assertTrue(queue.drain().isEmpty())
        source.inputs = HistoryInputs(null)
        engine.storage(HistoryState.Ready(HistoryPreferences(generation = 1), emptyList()))
        assertEquals(HistoryCapturePhase.WAITING, engine.state.value.phase)
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun endedSourceFeedIsAnObservationInterruptionNotAStorageDiagnosis() {
        begin()
        engine.fail(engine.owner, HistoryCaptureFailure.SOURCE_ENDED)
        assertEquals(HistoryEndReason.CAPTURE_INTERRUPTED, engine.restart()?.reason)
    }
}
