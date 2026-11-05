package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsRequestFailedException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** Foreground caller owns the lifetime. No queue, background launch, restoration or write retry. */
@Singleton
class ShortcutRunner
@Inject
constructor(private val source: ShortcutExecutionSource, private val clock: MonotonicClock) {
    private val gate = Mutex()
    private val active = AtomicReference<ShortcutRunControl?>(null)
    private val current = MutableStateFlow<ShortcutRunState?>(null)
    val state = current.asStateFlow()

    suspend fun run(origin: WorkspaceSession, binding: ShortcutBinding): Boolean {
        if (!gate.tryLock()) return false
        val invocation = ShortcutRunState(UUID.randomUUID().toString(), origin, binding)
        current.value = invocation
        try {
            invoke(invocation)
        } finally {
            active.set(null)
            gate.unlock()
        }
        return true
    }

    fun confirm(id: String): Boolean =
        active.get()?.takeIf { it.id == id }?.confirm(clock.nowMs()) == true

    fun cancel(id: String) {
        active.get()?.takeIf { it.id == id }?.stop(ShortcutIssue.CANCELLED)
    }

    private suspend fun invoke(invocation: ShortcutRunState) {
        val caller = currentCoroutineContext()
        var outcome = ShortcutOutcome.NOT_RUN
        var failure: ShortcutIssue? = null
        try {
            outcome =
                requestResult { observeAndExecute(invocation) }
                    .getOrElse {
                        failure = it.shortcutIssue()
                        ShortcutOutcome.NOT_RUN
                    }
        } catch (error: CancellationException) {
            failure = active.get()?.issue?.get() ?: ShortcutIssue.CANCELLED
            if (!caller.isActive) throw error
        } finally {
            val control = active.get()
            control?.close()
            if (
                control?.dispatched == true &&
                    outcome != ShortcutOutcome.APPLIED &&
                    failure != ShortcutIssue.REJECTED
            ) {
                outcome = ShortcutOutcome.UNKNOWN
            }
            current.value =
                requireNotNull(current.value)
                    .copy(phase = ShortcutPhase.FINISHED, outcome = outcome, issue = failure)
        }
    }

    private suspend fun observeAndExecute(invocation: ShortcutRunState): ShortcutOutcome =
        coroutineScope {
            val control = ShortcutRunControl(invocation.id, currentCoroutineContext().job)
            active.set(control)
            val events =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    requestResult {
                        source.events.collect { event ->
                            if (
                                event.token == invocation.origin.token &&
                                    event.event.invalidatesShortcut(
                                        invocation.binding,
                                        current.value?.target,
                                        control.dispatched,
                                    )
                            ) {
                                control.stop(ShortcutIssue.TARGET_CHANGED)
                            }
                        }
                    }
                    control.stop(ShortcutIssue.UNAVAILABLE)
                }
            val changes =
                watch(control) {
                    source.interruptions(invocation.origin, invocation.binding).collect {
                        control.stop(it)
                    }
                }
            try {
                val plan =
                    withTimeoutOrNull(20_000) {
                        source.prepare(invocation.origin, invocation.binding)
                    } ?: throw ShortcutStopped(ShortcutIssue.TIMEOUT)
                current.value = requireNotNull(current.value).copy(target = plan.target)
                val policy =
                    watch(control) {
                        source.policyChanges(plan.policy).collect { control.stop(it) }
                    }
                try {
                    execute(plan, control)
                } finally {
                    policy.cancel()
                }
            } finally {
                control.close()
                events.cancel()
                changes.cancel()
            }
        }

    private suspend fun execute(plan: ShortcutPlan, control: ShortcutRunControl): ShortcutOutcome {
        if (plan.initiallyMatches) return ShortcutOutcome.ALREADY_MATCHED
        if (plan.target.needsConfirmation(plan.policy.outputSafety)) {
            control.waiting(clock.nowMs())
            phase(ShortcutPhase.CONFIRMATION)
            withTimeoutOrNull(60_000) {
                control.awaitConsent()
                true
            } ?: throw ShortcutStopped(ShortcutIssue.EXPIRED)
        }
        phase(ShortcutPhase.CHECKING)
        return withTimeoutOrNull(20_000) {
            source.validate(plan)
            val dispatched =
                source.write(plan) {
                    control.dispatch()
                    phase(ShortcutPhase.WRITING)
                }
            if (!dispatched) ShortcutOutcome.ALREADY_MATCHED else verify(plan)
        } ?: throw ShortcutStopped(ShortcutIssue.TIMEOUT)
    }

    private suspend fun verify(plan: ShortcutPlan): ShortcutOutcome {
        phase(ShortcutPhase.VERIFYING)
        val confirmed =
            withTimeoutOrNull(4_000) {
                while (!source.confirmed(plan)) delay(100)
                true
            } == true
        return if (confirmed) ShortcutOutcome.APPLIED else ShortcutOutcome.UNKNOWN
    }

    private fun phase(phase: ShortcutPhase) {
        current.value = requireNotNull(current.value).copy(phase = phase)
    }

    private fun CoroutineScope.watch(control: ShortcutRunControl, collect: suspend () -> Unit) =
        launch(start = CoroutineStart.UNDISPATCHED) {
            requestResult { collect() }
            control.stop(ShortcutIssue.UNAVAILABLE)
        }
}

private fun Throwable.shortcutIssue(): ShortcutIssue =
    when (this) {
        is ShortcutStopped -> issue
        is WorkspaceChangedException -> ShortcutIssue.WORKSPACE_CHANGED
        is ObsRequestFailedException -> ShortcutIssue.REJECTED
        else -> ShortcutIssue.UNAVAILABLE
    }
