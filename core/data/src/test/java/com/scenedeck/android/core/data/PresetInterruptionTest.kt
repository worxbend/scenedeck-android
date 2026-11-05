package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetInterruptionTest {
    private val source = FakePresetExecution()
    private val history = FakePresetHistory()
    private val runner = PresetRunner(source, history, { 0 }, { 20_000 })

    @Test
    fun eventsAreSubscribedBeforeFirstReadAndRenamesCancelWithoutRetargeting() = runTest {
        source.beforeRead = {
            assertEquals(1, source.events.subscriptionCount.value)
            awaitCancellation()
        }
        val operation = launch { runner.run(executionPlan()) }
        runCurrent()
        source.events.emit(ObsSessionEvent(ObsSessionToken(99), ObsEvent.InputRemoved("Mic")))
        runCurrent()
        assertTrue(operation.isActive)
        source.events.emit(
            ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.InputNameChanged("Unrelated", "New"))
        )
        runCurrent()
        assertTrue(operation.isActive)
        source.events.emit(
            ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.InputNameChanged("Mic", "Renamed"))
        )
        operation.join()
        assertEquals(PresetStopReason.TARGET_CHANGED, runner.state.value!!.run.reason)
        assertTrue(source.writes.isEmpty())
        assertTrue(history.saved.last().steps.all { it.result == PresetStepResult.NOT_RUN })
    }

    @Test
    fun contextPolicyPresetAndBusyInterruptionsDoNotRetryDispatchedWork() = runTest {
        source.beforeWrite = { awaitCancellation() }
        listOf(
                PresetStopReason.WORKSPACE_CHANGED,
                PresetStopReason.POLICY_CHANGED,
                PresetStopReason.PRESET_CHANGED,
                PresetStopReason.BUSY,
            )
            .forEach { cause ->
                history.saved.clear()
                val operation = launch { runner.run(executionPlan()) }
                runCurrent()
                source.interruptions.emit(cause)
                operation.join()
                assertEquals(cause, runner.state.value!!.run.reason)
                assertEquals(PresetRunOutcome.STOPPED, runner.state.value!!.run.outcome)
                assertEquals(PresetStepResult.UNKNOWN, history.saved.last().steps.first().result)
            }
        assertEquals(4, source.writes.size)
    }

    @Test
    fun definiteRejectionDiffersFromUnknownAndReadFailuresSendNothing() = runTest {
        source.beforeWrite = {
            throw ObsRequestFailedException("SetInputMute", "ResourceNotFound", "sensitive")
        }
        runner.run(executionPlan())
        assertEquals(PresetStopReason.WRITE_REJECTED, runner.state.value!!.run.reason)
        assertEquals(PresetStepResult.REJECTED, runner.state.value!!.run.steps.first().result)
        history.saved.clear()
        source.check = PresetCheck.LOCKED
        runner.run(executionPlan())
        assertEquals(PresetStopReason.LOCKED, runner.state.value!!.run.reason)
        assertEquals(PresetStepResult.NOT_RUN, runner.state.value!!.run.steps.first().result)
        assertEquals(1, source.writes.size)
    }

    @Test
    fun entireRunHasDeadlineAndNeverSkipsToRemainingStep() = runTest {
        source.beforeWrite = { awaitCancellation() }
        runner.run(executionPlan())
        assertEquals(90_000L, testScheduler.currentTime)
        assertEquals(PresetStopReason.TIMEOUT, runner.state.value!!.run.reason)
        assertEquals(
            listOf(PresetStepResult.UNKNOWN, PresetStepResult.NOT_RUN),
            history.saved.last().steps.map { it.result },
        )
        assertEquals(0, source.events.subscriptionCount.value)
        assertEquals(0, source.interruptions.subscriptionCount.value)
    }
}
