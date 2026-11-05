package com.scenedeck.android.core.data

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Individually invoked, single-flight runtime controls. Never resumes or retries a write. */
@Singleton
class PresetRunner
@Inject
constructor(
    private val source: PresetExecutionSource,
    private val history: PresetRunHistory,
    private val clock: MonotonicClock,
    private val wall: WallClock,
) {
    private val gate = Mutex()
    private val active = AtomicReference<PresetCancellation?>(null)
    private val current = MutableStateFlow<PresetRunState?>(null)
    val state = current.asStateFlow()

    /** False means another invocation owns the runner; it does not replace that run's state. */
    suspend fun run(plan: PresetPlan, acknowledgedOutputs: Set<Int> = emptySet()): Boolean {
        if (!gate.tryLock()) return false
        try {
            invoke(plan.frozen(), acknowledgedOutputs.toSet())
        } finally {
            active.set(null)
            gate.unlock()
        }
        return true
    }

    fun cancel(runId: String) {
        active.get()?.takeIf { it.id == runId }?.stop(PresetStopReason.CANCELLED)
    }

    private suspend fun invoke(plan: PresetPlan, consent: Set<Int>) {
        val caller = currentCoroutineContext()
        val progress = PresetRunProgress(plan, history, wall.nowMs()) { current.value = it }
        val reason = AtomicReference<PresetStopReason?>(null)
        var outcome = PresetRunOutcome.STOPPED
        try {
            val result = requestResult {
                validatePlan(plan, consent, clock.nowMs())
                withTimeoutOrNull(90_000) {
                    observeAndExecute(plan, progress, reason)
                    true
                } ?: false
            }
            if (result.getOrNull() == true) outcome = PresetRunOutcome.COMPLETED
            else
                reason.compareAndSet(
                    null,
                    result.exceptionOrNull()?.stopReason() ?: PresetStopReason.TIMEOUT,
                )
        } catch (error: CancellationException) {
            reason.compareAndSet(null, PresetStopReason.CANCELLED)
            if (reason.get() == PresetStopReason.CANCELLED) outcome = PresetRunOutcome.CANCELLED
            if (!caller.isActive) throw error
        } finally {
            // A bounded final save preserves cancellation semantics and never dispatches OBS work.
            withContext(NonCancellable) {
                val saved =
                    withTimeoutOrNull(2_000) {
                        progress.finish(outcome, reason.get(), wall.nowMs())
                        true
                    } ?: false
                if (!saved) progress.historyFailed()
            }
        }
    }

    private suspend fun observeAndExecute(
        plan: PresetPlan,
        progress: PresetRunProgress,
        reason: AtomicReference<PresetStopReason?>,
    ) = coroutineScope {
        val cancellation =
            PresetCancellation(progress.run.id, currentCoroutineContext().job, reason)
        active.set(cancellation)
        // Subscribe directly before any read or persistence suspends. Do not use onStart.
        val events =
            launch(start = CoroutineStart.UNDISPATCHED) {
                requestResult {
                    source.events.collect { event ->
                        if (
                            event.token == plan.origin.token &&
                                event.event.invalidatesPreset(plan.preset)
                        )
                            cancellation.stop(PresetStopReason.TARGET_CHANGED)
                    }
                }
                cancellation.stop(PresetStopReason.SOURCE_UNAVAILABLE)
            }
        val changes =
            launch(start = CoroutineStart.UNDISPATCHED) {
                requestResult { source.interruptions(plan).collect { cancellation.stop(it) } }
                cancellation.stop(PresetStopReason.SOURCE_UNAVAILABLE)
            }
        try {
            PresetExecution(source, progress).execute(plan)
        } finally {
            cancellation.close()
            events.cancel()
            changes.cancel()
        }
    }
}

private class PresetCancellation(
    val id: String,
    private val job: Job,
    private val reason: AtomicReference<PresetStopReason?>,
) {
    private var open = true

    @Synchronized
    fun stop(cause: PresetStopReason) {
        if (!open) return
        reason.compareAndSet(null, cause)
        job.cancel(CancellationException("Preset execution interrupted"))
    }

    @Synchronized
    fun close() {
        open = false
    }
}

private fun validatePlan(plan: PresetPlan, consent: Set<Int>, now: Long) {
    val failure =
        when {
            !requestResultPlan(plan) -> PresetStopReason.INVALID_PLAN
            now < plan.preparedAtMs || now - plan.preparedAtMs > 60_000 -> PresetStopReason.EXPIRED
            !consent.containsAll(plan.outputConfirmations) -> PresetStopReason.CONSENT_REQUIRED
            else -> null
        }
    failure?.let { throw PresetExecutionStopped(it) }
}

private fun Throwable.stopReason() =
    when (this) {
        is PresetExecutionStopped -> reason
        is WorkspaceChangedException -> PresetStopReason.WORKSPACE_CHANGED
        else -> PresetStopReason.SOURCE_UNAVAILABLE
    }

private fun requestResultPlan(plan: PresetPlan): Boolean =
    try {
        plan.preset.encodePreset()
        plan.canRun && plan.preset.workspace == plan.origin.key
    } catch (_: Exception) {
        false
    }

private fun PresetPlan.frozen() =
    copy(
        preset = preset.copy(steps = preset.steps.toList()),
        checks = checks.toList(),
        policy =
            policy.copy(
                lockedInputs = policy.lockedInputs.toSet(),
                allowedInputs = policy.allowedInputs.toSet(),
            ),
    )
