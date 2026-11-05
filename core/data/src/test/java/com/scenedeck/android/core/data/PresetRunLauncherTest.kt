package com.scenedeck.android.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetRunLauncherTest {
    @Test
    fun screenCancellationKeepsRunAndDuplicateInvocationCannotReplaceIt() = runTest {
        val source = FakePresetExecution().apply { beforeWrite = { awaitCancellation() } }
        val history = FakePresetHistory()
        val launcher =
            PresetRunLauncher(PresetRunner(source, history, { 0 }, { 1_000 }), backgroundScope)
        val screen = launch { assertTrue(launcher.start(executionPlan(), emptySet())) }
        runCurrent()
        screen.cancel()
        val original = launcher.state.value!!.run
        assertTrue(launcher.busy.value)
        assertFalse(launcher.start(executionPlan(), emptySet()))
        launcher.cancel("obsolete-run")
        runCurrent()
        assertEquals(PresetRunOutcome.RUNNING, launcher.state.value!!.run.outcome)
        launcher.cancel(original.id)
        runCurrent()
        assertFalse(launcher.busy.value)
        val stopped = launcher.state.value!!.run
        assertEquals(original.id, stopped.id)
        assertEquals(PresetRunOutcome.CANCELLED, stopped.outcome)
        assertEquals(
            listOf(PresetStepResult.UNKNOWN, PresetStepResult.NOT_RUN),
            stopped.steps.map { it.result },
        )
        assertEquals(stopped, history.saved.last())
        assertEquals(1, source.writes.size)
        source.beforeWrite = {}
        assertTrue(launcher.start(executionPlan(), emptySet()))
        runCurrent()
        assertEquals(PresetRunOutcome.COMPLETED, launcher.state.value!!.run.outcome)
    }

    @Test
    fun canceledApplicationScopeCannotStartOrRetainAdmission() = runTest {
        val source = FakePresetExecution().apply { beforeRead = { awaitCancellation() } }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val launcher =
            PresetRunLauncher(PresetRunner(source, FakePresetHistory(), { 0 }, { 1_000 }), scope)
        assertTrue(launcher.start(executionPlan(), emptySet()))
        scope.cancel()
        runCurrent()
        assertFalse(launcher.busy.value)
        val result = launcher.state.value
        assertFalse(launcher.start(executionPlan(), emptySet()))
        assertEquals(result, launcher.state.value)
        assertTrue(source.writes.isEmpty())
    }
}
