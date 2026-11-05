package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal class PresetExecution(
    private val source: PresetExecutionSource,
    private val progress: PresetRunProgress,
) {
    suspend fun execute(plan: PresetPlan) {
        source.validate(plan)
        progress.start()
        plan.preset.steps.forEachIndexed { index, step -> executeStep(plan, index, step) }
    }

    private suspend fun executeStep(plan: PresetPlan, index: Int, step: PresetStep) {
        progress.step(index, PresetStepResult.CHECKING)
        source.validate(plan)
        val check = source.inspect(plan, step)
        source.validate(plan)
        check.failure()?.let { throw PresetExecutionStopped(it) }
        if (check == PresetCheck.CURRENTLY_MATCHES) {
            progress.step(index, PresetStepResult.ALREADY_MATCHED)
            return
        }
        progress.step(index, PresetStepResult.WRITING)
        val write = requestResult { source.write(plan, step) }
        write.exceptionOrNull()?.let { failure ->
            val reason =
                when (failure) {
                    is ObsRequestFailedException -> {
                        progress.step(index, PresetStepResult.REJECTED)
                        PresetStopReason.WRITE_REJECTED
                    }
                    is PresetExecutionStopped -> failure.reason
                    else -> PresetStopReason.WRITE_UNKNOWN
                }
            throw PresetExecutionStopped(reason)
        }
        confirm(plan, step)
        progress.step(index, PresetStepResult.CONFIRMED)
    }

    /** Every confirmation read begins after the write acknowledged; stale events cannot advance. */
    private suspend fun confirm(plan: PresetPlan, step: PresetStep) {
        val confirmed =
            withTimeoutOrNull(4_000) {
                var matched = false
                while (!matched) {
                    source.validate(plan)
                    val check = source.inspect(plan, step)
                    source.validate(plan)
                    check.failure()?.let { throw PresetExecutionStopped(it) }
                    matched = check == PresetCheck.CURRENTLY_MATCHES
                    if (!matched) delay(250)
                }
                true
            } ?: false
        if (!confirmed) throw PresetExecutionStopped(PresetStopReason.UNCONFIRMED)
    }
}

private fun PresetCheck.failure(): PresetStopReason? =
    when (this) {
        PresetCheck.READY,
        PresetCheck.CURRENTLY_MATCHES -> null
        PresetCheck.UNSUPPORTED -> PresetStopReason.UNSUPPORTED
        PresetCheck.MISSING -> PresetStopReason.MISSING
        PresetCheck.LOCKED -> PresetStopReason.LOCKED
        PresetCheck.NOT_ALLOWED -> PresetStopReason.NOT_ALLOWED
        PresetCheck.STUDIO_OFF -> PresetStopReason.STUDIO_OFF
        PresetCheck.NOT_READY -> PresetStopReason.NOT_READY
        PresetCheck.UNKNOWN -> PresetStopReason.READ_UNKNOWN
    }
