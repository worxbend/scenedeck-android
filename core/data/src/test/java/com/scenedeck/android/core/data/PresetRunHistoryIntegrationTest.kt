package com.scenedeck.android.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRunHistoryIntegrationTest : HistoryTestFixture() {
    @Test
    fun disconnectDuringWriteFinalizesTheOriginalObservation(): Unit = runBlocking {
        repository.start(SEED)
        val history =
            StoredPresetRunHistory(database, LocalProcessIdentity()) {
                HistoryBookmarkLink(SEED.id, 50)
            }
        val source = FakePresetExecution()
        val entered = CompletableDeferred<Unit>()
        source.beforeWrite = {
            entered.complete(Unit)
            awaitCancellation()
        }
        val runner = PresetRunner(source, history, { 0 }, { 10_050 })
        val operation = launch { runner.run(executionPlan()) }
        withTimeout(5_000) { entered.await() }
        val saved = repository.exportSnapshot().observations.single().presetRuns.single().run
        assertEquals(PresetStepResult.WRITING, saved.steps.first().result)
        repository.finish(
            HistoryPosition(SEED.id, SEED.ownerId, 10_100, 100),
            HistoryEndReason.DISCONNECTED,
        )
        repository.start(SEED.copy(id = "replacement", workspace = WorkspaceKey(8, "Other")))
        source.interruptions.emit(PresetStopReason.WORKSPACE_CHANGED)
        withTimeout(5_000) { operation.join() }
        val observations = repository.exportSnapshot().observations
        assertTrue(observations.first().presetRuns.isEmpty())
        val original = observations.last().presetRuns.single().run
        assertEquals(saved.id, original.id)
        assertEquals(PresetRunOutcome.STOPPED, original.outcome)
        assertEquals(
            listOf(PresetStepResult.UNKNOWN, PresetStepResult.NOT_RUN),
            original.steps.map { it.result },
        )
        assertEquals(PresetHistoryStatus.SAVED, runner.state.value!!.history)
        assertEquals(1, source.writes.size)
    }

    @Test
    fun clearDuringRunLeavesNoRecordAndDisabledCaptureDoesNotInventAssociation(): Unit =
        runBlocking {
            repository.start(SEED)
            val history =
                StoredPresetRunHistory(database, LocalProcessIdentity()) {
                    HistoryBookmarkLink(SEED.id, 50)
                }
            val source = FakePresetExecution()
            source.beforeWrite = { repository.delete() }
            val runner = PresetRunner(source, history, { 0 }, { 10_050 })
            runner.run(executionPlan())
            assertEquals(PresetRunOutcome.COMPLETED, runner.state.value!!.run.outcome)
            assertEquals(PresetHistoryStatus.REMOVED, runner.state.value!!.history)
            assertTrue(repository.exportSnapshot().observations.isEmpty())
            repository.configure(false, 30)
            val unrecorded = StoredPresetRunHistory(database, LocalProcessIdentity()) { null }
            val next = PresetRunner(source, unrecorded, { 0 }, { 10_100 })
            next.run(executionPlan())
            assertEquals(PresetRunOutcome.COMPLETED, next.state.value!!.run.outcome)
            assertEquals(PresetHistoryStatus.NOT_RECORDED, next.state.value!!.history)
            assertTrue(repository.exportSnapshot().observations.isEmpty())
        }
}
