package com.scenedeck.android.shortcuts

import android.view.KeyEvent
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutExecutionSource
import com.scenedeck.android.core.data.ShortcutIssue
import com.scenedeck.android.core.data.ShortcutOutcome
import com.scenedeck.android.core.data.ShortcutPlan
import com.scenedeck.android.core.data.ShortcutPolicy
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.ShortcutRunState
import com.scenedeck.android.core.data.ShortcutRunner
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.ShortcutTarget
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.ui.feedback
import com.scenedeck.android.navigation.SceneDeckDestination
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutHostTest {
    @Test
    fun longTargetCannotHideUnknownOutcomeOrLeakFailureDetailsInToast() {
        val binding =
            ShortcutBinding(
                TEST_WORKSPACE.key,
                "binding",
                0,
                ShortcutChord("F8"),
                ShortcutAction.Scene("x".repeat(1_024)),
                true,
            )
        val run =
            ShortcutRunState("run", TEST_WORKSPACE, binding, outcome = ShortcutOutcome.UNKNOWN)
        assertEquals("Shortcut result unknown. Check OBS before trying again.", run.feedback())
        val failed = run.copy(outcome = ShortcutOutcome.NOT_RUN, issue = ShortcutIssue.UNAVAILABLE)
        assertEquals("Shortcut: The target could not be checked.", failed.feedback())
    }

    @Test
    fun childTextAndNonOperatorRoutesKeepPriorityAndScopeIsCheckedSynchronously() = runTest {
        val f = Fixture(this)
        f.enable()
        assertTrue(f.host.dispatch(key(), true))
        f.release()
        val lease = f.host.textInput.enter()
        assertFalse(f.press())
        f.host.textInput.leave(lease)
        assertFalse(f.host.dispatch(key(), false, textWasEditing = true))
        SceneDeckDestination.overflow.forEach {
            f.host.admission.shell(it, false)
            assertFalse(f.press())
        }
        f.host.admission.shell(SceneDeckDestination.Live, true)
        assertFalse(f.press())
        f.host.admission.shell(SceneDeckDestination.Live, false)
        f.workspaces.select(TEST_WORKSPACE.copy(key = WorkspaceKey(8, "Studio")))
        assertFalse(f.press())
        runCurrent()
        assertEquals(0, f.writes)
    }

    @Test
    fun onePressRunsOnceAndFocusResetRejectsRepeatOnlyEvents() = runTest {
        val f = Fixture(this)
        f.enable()
        assertTrue(f.press())
        runCurrent()
        assertTrue(f.press())
        assertTrue(f.host.dispatch(key(repeat = 1), false))
        assertEquals(1, f.writes)
        f.release()
        assertTrue(f.press())
        runCurrent()
        assertEquals(2, f.writes)
        f.host.focus(false)
        assertFalse(f.press())
        f.host.focus(true)
        assertFalse(f.host.dispatch(key(repeat = 2), false))
        assertTrue(f.press())
        runCurrent()
        assertEquals(3, f.writes)
    }

    @Test
    fun ownDialogFocusLossPreservesConcreteConsentButNavigationCancelsItBeforeRecomposition() =
        runTest {
            val f = Fixture(this, output = true)
            f.enable()
            f.press()
            runCurrent()
            val first = f.host.state.value.confirmation!!
            f.host.focus(false)
            assertEquals(first, f.host.state.value.confirmation)
            f.host.confirm("obsolete")
            assertEquals(0, f.writes)
            f.host.confirm(first.id)
            f.host.confirm(first.id)
            runCurrent()
            assertEquals(1, f.writes)
            assertNull(f.host.state.value.confirmation)
            f.host.focus(true)
            f.release()
            f.press()
            runCurrent()
            val second = f.host.state.value.confirmation!!
            f.host.admission.navigating()
            assertNull(f.host.state.value.confirmation)
            f.host.confirm(second.id)
            f.host.admission.shell(SceneDeckDestination.Mixer, false)
            runCurrent()
            assertEquals(1, f.writes)
            assertNull(f.host.state.value.confirmation)
            assertEquals(ShortcutOutcome.NOT_RUN, f.runner.state.value!!.outcome)
        }

    @Test
    fun backgroundCancelsPreparationAndDispatchedWritesRemainUnknownWithoutRetry() = runTest {
        val f = Fixture(this)
        f.onPrepare = { awaitCancellation() }
        f.enable()
        f.press()
        f.host.foreground(false)
        runCurrent()
        assertEquals(0, f.writes)
        assertEquals(ShortcutOutcome.NOT_RUN, f.runner.state.value!!.outcome)
        f.onPrepare = {}
        f.onWrite = { awaitCancellation() }
        f.enable()
        f.press()
        runCurrent()
        assertEquals(1, f.writes)
        f.host.foreground(false)
        runCurrent()
        assertEquals(ShortcutOutcome.UNKNOWN, f.runner.state.value!!.outcome)
        assertTrue(f.host.state.value.notice!!.text.contains("result unknown"))
        f.enable()
        assertEquals(1, f.writes)
        assertNull(f.host.state.value.confirmation)
    }

    @Test
    fun competingKeysAreConsumedWithoutQueueAndConsentDoesNotSurviveHostReplacement() = runTest {
        val f = Fixture(this, output = true)
        f.enable()
        f.press()
        runCurrent()
        val first = f.host.state.value.confirmation!!
        f.release()
        assertTrue(f.press())
        runCurrent()
        assertEquals(first, f.host.state.value.confirmation)
        assertTrue(f.host.state.value.notice!!.text.contains("already pending"))
        f.host.foreground(false)
        runCurrent()
        val replacement = ShortcutHost(f.repository, f.runner, backgroundScope)
        replacement.confirm(first.id)
        runCurrent()
        assertNull(replacement.state.value.confirmation)
        assertEquals(0, f.writes)
    }

    private class Fixture(private val scope: TestScope, output: Boolean = false) {
        val workspaces = MutableWorkspaceContext()
        private val binding =
            ShortcutBinding(
                TEST_WORKSPACE.key,
                "binding",
                0,
                ShortcutChord("F8"),
                if (output) ShortcutAction.Stream(false) else ShortcutAction.Mute("Mic", true),
                true,
            )
        private val bindings =
            MutableStateFlow<ShortcutState>(ShortcutState.Ready(TEST_WORKSPACE, listOf(binding)))
        val repository =
            mockk<ShortcutRepository> {
                every { workspaces } returns this@Fixture.workspaces
                every { state } returns bindings
            }
        var writes = 0
        var onPrepare: suspend () -> Unit = {}
        var onWrite: suspend () -> Unit = {}
        private val source =
            mockk<ShortcutExecutionSource> {
                every { events } returns MutableSharedFlow<ObsSessionEvent>()
                every { interruptions(any(), any()) } returns MutableSharedFlow<ShortcutIssue>()
                every { policyChanges(any()) } returns MutableSharedFlow<ShortcutIssue>()
                coEvery { prepare(any(), any()) } coAnswers
                    {
                        onPrepare()
                        ShortcutPlan(
                            TEST_WORKSPACE,
                            binding,
                            ShortcutPolicy(emptySet(), emptySet(), OutputSafety()),
                            if (output) ShortcutTarget.Stream(false)
                            else ShortcutTarget.Mute("Mic", true),
                            false,
                        )
                    }
                coEvery { validate(any()) } returns Unit
                coEvery { write(any(), any()) } coAnswers
                    {
                        secondArg<() -> Unit>()()
                        writes++
                        onWrite()
                        true
                    }
                coEvery { confirmed(any()) } returns true
            }
        val runner = ShortcutRunner(source) { scope.testScheduler.currentTime }
        val host = ShortcutHost(repository, runner, scope.backgroundScope)

        fun enable() {
            host.admission.shell(SceneDeckDestination.Live, false)
            host.foreground(true)
            host.focus(true)
            scope.runCurrent()
        }

        fun press() = host.dispatch(key(), false)

        fun release() = host.dispatch(key(action = KeyEvent.ACTION_UP), false)
    }
}
