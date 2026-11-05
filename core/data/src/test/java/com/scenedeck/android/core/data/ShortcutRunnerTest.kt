package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutRunnerTest {
    private val source = FakeShortcutSource()
    private var now = 0L
    private val runner = ShortcutRunner(source) { now }
    private val origin = TEST_WORKSPACE

    @Test
    fun concreteConsentIsSingleUseRejectsOtherInvocationsAndNeverQueuesThem() = runTest {
        val binding = shortcut(ShortcutAction.Stream(false))
        val run = async { runner.run(origin, binding) }
        runCurrent()
        val pending = runner.state.value!!
        assertEquals(ShortcutPhase.CONFIRMATION, pending.phase)
        assertEquals(ShortcutTarget.Stream(false), pending.target)
        assertEquals(0, source.writes)
        assertFalse(runner.run(origin, shortcut()))
        assertFalse(runner.confirm("old-id"))
        assertTrue(runner.confirm(pending.id))
        assertFalse(runner.confirm(pending.id))
        assertTrue(run.await())
        assertEquals(ShortcutOutcome.APPLIED, runner.state.value!!.outcome)
        assertFalse(runner.confirm(pending.id))
        assertEquals(1, source.writes)
    }

    @Test
    fun consentExpiresWithoutWritesAndOldCallbacksCannotConfirmTheNextInvocation() = runTest {
        val binding = shortcut(ShortcutAction.Recording(false))
        val first = async { runner.run(origin, binding) }
        runCurrent()
        val oldId = runner.state.value!!.id
        advanceTimeBy(60_001)
        first.await()
        assertEquals(ShortcutIssue.EXPIRED, runner.state.value!!.issue)
        assertEquals(ShortcutOutcome.NOT_RUN, runner.state.value!!.outcome)
        val second = async { runner.run(origin, binding) }
        runCurrent()
        assertFalse(runner.confirm(oldId))
        assertEquals(0, source.writes)
        runner.cancel(runner.state.value!!.id)
        second.await()
        assertEquals(ShortcutIssue.CANCELLED, runner.state.value!!.issue)
    }

    @Test
    fun elapsedClockExpiryRejectsAConfirmationEvenBeforeTheTimerResumes() = runTest {
        val running = async { runner.run(origin, shortcut(ShortcutAction.Stream(false))) }
        runCurrent()
        val id = runner.state.value!!.id
        now = 60_001
        assertFalse(runner.confirm(id))
        running.await()
        assertEquals(ShortcutIssue.EXPIRED, runner.state.value!!.issue)
        assertEquals(0, source.writes)
    }

    @Test
    fun initialAndFreshMatchingTargetsDoNotToggleOrWrite() = runTest {
        source.initialMatches = true
        runner.run(origin, shortcut(ShortcutAction.Stream(false)))
        assertEquals(ShortcutOutcome.ALREADY_MATCHED, runner.state.value!!.outcome)
        source.initialMatches = false
        source.matchesBeforeWrite = true
        runner.run(origin, shortcut())
        assertEquals(ShortcutOutcome.ALREADY_MATCHED, runner.state.value!!.outcome)
        assertEquals(0, source.writes)
        assertEquals(0, source.confirmations)
    }

    @Test
    fun contextBindingAndPolicyChangesCancelPendingConsent() = runTest {
        listOf(
                ShortcutIssue.WORKSPACE_CHANGED,
                ShortcutIssue.BINDING_CHANGED,
                ShortcutIssue.POLICY_CHANGED,
            )
            .forEach { issue ->
                val run = async { runner.run(origin, shortcut(ShortcutAction.Stream(false))) }
                runCurrent()
                val id = runner.state.value!!.id
                if (issue == ShortcutIssue.POLICY_CHANGED) source.policies.emit(issue)
                else source.changes.emit(issue)
                run.await()
                assertEquals(issue, runner.state.value!!.issue)
                assertFalse(runner.confirm(id))
            }
        assertEquals(0, source.writes)
    }

    @Test
    fun subscribesBeforePreparationAndIgnoresEventsFromOtherSessions() = runTest {
        source.onPrepare = { awaitCancellation() }
        val run = async { runner.run(origin, shortcut(ShortcutAction.Scene("Camera"))) }
        runCurrent()
        source.events.emit(ObsSessionEvent(ObsSessionToken(9), ObsEvent.SceneRemoved("Camera")))
        runCurrent()
        assertFalse(run.isCompleted)
        source.events.emit(
            ObsSessionEvent(origin.token, ObsEvent.SceneNameChanged("Camera", "New"))
        )
        run.await()
        assertEquals(ShortcutIssue.TARGET_CHANGED, runner.state.value!!.issue)
        assertEquals(ShortcutOutcome.NOT_RUN, runner.state.value!!.outcome)
    }

    @Test
    fun preparationTimeoutAndUnreadableSourceNeverDispatch() = runTest {
        source.onPrepare = { awaitCancellation() }
        runner.run(origin, shortcut())
        assertEquals(ShortcutIssue.TIMEOUT, runner.state.value!!.issue)
        source.onPrepare = { error("private transport details") }
        runner.run(origin, shortcut())
        assertEquals(ShortcutIssue.UNAVAILABLE, runner.state.value!!.issue)
        assertFalse(runner.state.value.toString().contains("private"))
        assertEquals(0, source.writes)
    }

    @Test
    fun writeTimeoutIsUnknownAndIsNeverRetried() = runTest {
        source.onWrite = { awaitCancellation() }
        runner.run(origin, shortcut())
        assertEquals(ShortcutIssue.TIMEOUT, runner.state.value!!.issue)
        assertEquals(ShortcutOutcome.UNKNOWN, runner.state.value!!.outcome)
        assertEquals(1, source.writes)
        assertEquals(0, source.confirmations)
    }

    @Test
    fun explicitWriteRejectionIsSanitizedAndDoesNotRetry() = runTest {
        source.onWrite = {
            throw ObsRequestFailedException("SetInputMute", "600", "private comment")
        }
        runner.run(origin, shortcut())
        assertEquals(ShortcutIssue.REJECTED, runner.state.value!!.issue)
        assertEquals(ShortcutOutcome.NOT_RUN, runner.state.value!!.outcome)
        assertFalse(runner.state.value.toString().contains("private"))
        assertEquals(1, source.writes)
    }

    @Test
    fun callerCancellationPropagatesAndPreservesUncertaintyWhileReleasingAdmission() = runTest {
        val entered = CompletableDeferred<Unit>()
        source.onWrite = {
            entered.complete(Unit)
            awaitCancellation()
        }
        val job = launch { runner.run(origin, shortcut()) }
        entered.await()
        assertFalse(runner.run(origin, shortcut()))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(ShortcutOutcome.UNKNOWN, runner.state.value!!.outcome)
        source.onWrite = {}
        assertTrue(runner.run(origin, shortcut()))
        assertEquals(ShortcutOutcome.APPLIED, runner.state.value!!.outcome)
        assertEquals(2, source.writes)
    }

    @Test
    fun cancellationDuringReadPreparationPropagatesWithoutAnUnknownWrite() = runTest {
        source.onPrepare = { awaitCancellation() }
        val job = launch { runner.run(origin, shortcut()) }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(ShortcutOutcome.NOT_RUN, runner.state.value!!.outcome)
        assertEquals(ShortcutIssue.CANCELLED, runner.state.value!!.issue)
        assertEquals(0, source.writes)
    }

    @Test
    fun acknowledgementRequiresAFreshConfirmedTargetAndMismatchStaysUnknown() = runTest {
        var reads = 0
        source.onConfirm = { ++reads >= 3 }
        runner.run(origin, shortcut())
        assertEquals(ShortcutOutcome.APPLIED, runner.state.value!!.outcome)
        assertEquals(3, source.confirmations)
        source.onConfirm = { false }
        runner.run(origin, shortcut())
        assertEquals(ShortcutOutcome.UNKNOWN, runner.state.value!!.outcome)
        assertNull(runner.state.value!!.issue)
        assertEquals(2, source.writes)
    }

    @Test
    fun takePreviewChangeBeforeDispatchStopsButOwnTransitionExchangeDoesNot() = runTest {
        val binding = shortcut(ShortcutAction.TakePreview)
        source.onPrepare = {
            source.events.emit(
                ObsSessionEvent(origin.token, ObsEvent.CurrentPreviewSceneChanged("Other"))
            )
            delay(1)
        }
        runner.run(origin, binding)
        assertEquals(ShortcutIssue.TARGET_CHANGED, runner.state.value!!.issue)
        assertEquals(0, source.writes)
        source.onPrepare = {}
        source.onWrite = {
            source.events.emit(
                ObsSessionEvent(origin.token, ObsEvent.CurrentPreviewSceneChanged("Old program"))
            )
            delay(1)
        }
        runner.run(origin, binding)
        assertEquals(ShortcutOutcome.APPLIED, runner.state.value!!.outcome)
        assertEquals(1, source.writes)
    }

    @Test
    fun policyChangeDuringSuspendedWriteStopsWithUnknownOutcome() = runTest {
        val entered = CompletableDeferred<Unit>()
        source.onWrite = {
            entered.complete(Unit)
            awaitCancellation()
        }
        val run = async { runner.run(origin, shortcut()) }
        entered.await()
        source.policies.emit(ShortcutIssue.POLICY_CHANGED)
        run.await()
        assertEquals(ShortcutIssue.POLICY_CHANGED, runner.state.value!!.issue)
        assertEquals(ShortcutOutcome.UNKNOWN, runner.state.value!!.outcome)
        assertEquals(1, source.writes)
    }
}
