package com.scenedeck.android.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetRunnerTest {
    private val source = FakePresetExecution()
    private val history = FakePresetHistory()

    private fun runner(clock: MonotonicClock = MonotonicClock { 0 }) =
        PresetRunner(source, history, clock) { 20_000 }

    @Test
    fun orderedTargetsAreReadAgainAndConfirmedAfterEveryWrite() = runTest {
        val runner = runner()
        val plan = executionPlan()
        assertTrue(runner.run(plan))
        assertEquals(plan.preset.steps, source.writes)
        assertEquals(
            listOf(
                plan.preset.steps[0],
                plan.preset.steps[0],
                plan.preset.steps[1],
                plan.preset.steps[1],
            ),
            source.reads,
        )
        val result = runner.state.value!!.run
        assertEquals(PresetRunOutcome.COMPLETED, result.outcome)
        assertEquals(
            listOf(PresetStepResult.CONFIRMED, PresetStepResult.CONFIRMED),
            result.steps.map { it.result },
        )
        assertEquals(result, history.saved.last())
        assertEquals(
            2,
            history.saved.count {
                it.steps.any { step -> step.result == PresetStepResult.WRITING }
            },
        )
    }

    @Test
    fun currentlyMatchingTargetIsReadAndNeverWritten() = runTest {
        source.check = PresetCheck.CURRENTLY_MATCHES
        val runner = runner()
        runner.run(executionPlan())
        assertEquals(2, source.reads.size)
        assertTrue(source.writes.isEmpty())
        assertTrue(
            runner.state.value!!.run.steps.all { it.result == PresetStepResult.ALREADY_MATCHED }
        )
    }

    @Test
    fun consentExpiryAndMalformedChecksPreventAnyExecution() = runTest {
        val plan = executionPlan(listOf(PresetStep.Output(PresetOutput.STREAM, false)))
        val runner = runner()
        runner.run(plan)
        assertEquals(PresetStopReason.CONSENT_REQUIRED, runner.state.value!!.run.reason)
        runner.run(plan.copy(preparedAtMs = -60_001), setOf(0))
        assertEquals(PresetStopReason.EXPIRED, runner.state.value!!.run.reason)
        runner.run(plan.copy(checks = emptyList()), setOf(0))
        assertEquals(PresetStopReason.INVALID_PLAN, runner.state.value!!.run.reason)
        assertTrue(source.reads.isEmpty())
        assertTrue(source.writes.isEmpty())
        assertTrue(history.saved.isEmpty())
        runner.run(plan, setOf(0))
        assertEquals(PresetRunOutcome.COMPLETED, runner.state.value!!.run.outcome)
    }

    @Test
    fun unknownWriteStopsWithoutRetryAndPreservesEarlierConfirmation() = runTest {
        source.beforeWrite = { if (source.writes.size == 2) error("private server password") }
        val runner = runner()
        runner.run(executionPlan(executionPlan().preset.steps + PresetStep.Mute("Other", true)))
        val result = runner.state.value!!.run
        assertEquals(
            listOf(PresetStepResult.CONFIRMED, PresetStepResult.UNKNOWN, PresetStepResult.NOT_RUN),
            result.steps.map { it.result },
        )
        assertEquals(PresetStopReason.WRITE_UNKNOWN, result.reason)
        assertEquals(2, source.writes.size)
        assertFalse(result.toString().contains("password"))
    }

    @Test
    fun acknowledgementWithoutTargetConfirmationStopsAtFourSeconds() = runTest {
        source.applyWrites = false
        val runner = runner { testScheduler.currentTime }
        runner.run(executionPlan())
        assertEquals(4_000, testScheduler.currentTime)
        assertEquals(1, source.writes.size)
        assertEquals(PresetStopReason.UNCONFIRMED, runner.state.value!!.run.reason)
        assertEquals(PresetStepResult.UNKNOWN, runner.state.value!!.run.steps.first().result)
    }

    @Test
    fun singleFlightAndExplicitCancellationKeepTheOriginalRun() = runTest {
        source.beforeWrite = { awaitCancellation() }
        val runner = runner()
        val job = launch { assertTrue(runner.run(executionPlan())) }
        runCurrent()
        val id = runner.state.value!!.run.id
        assertFalse(runner.run(executionPlan()))
        runner.cancel("obsolete")
        assertTrue(job.isActive)
        runner.cancel(id)
        job.join()
        assertFalse(job.isCancelled)
        assertEquals(PresetRunOutcome.CANCELLED, runner.state.value!!.run.outcome)
        assertEquals(PresetStepResult.UNKNOWN, runner.state.value!!.run.steps.first().result)
        assertEquals(id, history.saved.last().id)
    }

    @Test
    fun callerCancellationPropagatesAfterSavingConservativeResult() = runTest {
        source.beforeRead = { awaitCancellation() }
        val runner = runner()
        val job = launch {
            runner.run(executionPlan())
            fail("caller cancellation was swallowed")
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(source.writes.isEmpty())
        assertEquals(PresetRunOutcome.CANCELLED, history.saved.last().outcome)
        assertTrue(history.saved.last().steps.all { it.result == PresetStepResult.NOT_RUN })
    }

    @Test
    fun historyFailureBeforeDispatchStopsAndFinalSaveIsBounded() = runTest {
        history.beforeSave = {
            if (it.steps.any { step -> step.result == PresetStepResult.WRITING })
                error("disk details")
        }
        val runner = runner()
        runner.run(executionPlan())
        assertTrue(source.writes.isEmpty())
        assertEquals(PresetStopReason.HISTORY_FAILED, runner.state.value!!.run.reason)
        assertEquals(PresetHistoryStatus.SAVED, runner.state.value!!.history)
        history.beforeSave = { awaitCancellation() }
        val job = launch { runner.run(executionPlan()) }
        runCurrent()
        job.cancel()
        advanceTimeBy(2_001)
        job.join()
        assertEquals(PresetHistoryStatus.FAILED, runner.state.value!!.history)
    }
}
