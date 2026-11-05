package com.scenedeck.android.feature.history

import com.scenedeck.android.core.data.HistoryPresetRun
import com.scenedeck.android.core.data.PresetMediaState
import com.scenedeck.android.core.data.PresetOutput
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetSceneDestination
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.PresetStepResult
import com.scenedeck.android.core.data.PresetStopReason
import java.util.Locale

internal fun HistoryPresetRun.timelineItem() =
    HistoryTimelineItem(
        "preset-${run.id}",
        elapsedMs,
        run.startedAtMs,
        "Preset · ${run.presetName}",
        buildList {
            add(
                when (run.outcome) {
                    PresetRunOutcome.RUNNING ->
                        "Saved during execution · final result not yet saved."
                    PresetRunOutcome.COMPLETED ->
                        "Completed · every target was confirmed or already matched."
                    PresetRunOutcome.STOPPED -> "Stopped · completed steps were kept."
                    PresetRunOutcome.CANCELLED -> "Cancelled · completed steps were kept."
                    PresetRunOutcome.INTERRUPTED -> "App interrupted · finish time unknown."
                }
            )
            run.reason?.let { add(stopLabels.getValue(it)) }
            run.finishedAtMs?.let { add("Finished · ${bookmarkDate(it)}") }
            run.steps.forEachIndexed { index, step ->
                add("${index + 1}. ${step.target.targetText()} · ${step.result.resultText()}")
            }
            if (run.steps.any { it.result == PresetStepResult.UNKNOWN })
                add("An unknown write may have taken effect. Check OBS before running again.")
        },
        Long.MAX_VALUE - 1,
    )

private fun PresetStep.targetText(): String =
    when (this) {
        is PresetStep.Scene ->
            "${if (destination == PresetSceneDestination.PROGRAM) "Program" else "Preview"}: $name"
        is PresetStep.Mute -> "${if (muted) "Mute" else "Unmute"}: $inputName"
        is PresetStep.Volume ->
            "Volume: $inputName → ${String.format(Locale.ROOT, "%.1f", multiplier * 100)}%"
        is PresetStep.Media -> "${state.actionText()}: $inputName"
        is PresetStep.Output ->
            "${if (active) "Start" else "Stop"} ${if (output == PresetOutput.STREAM) "stream" else "recording"}"
    }

private fun PresetMediaState.actionText(): String =
    when (this) {
        PresetMediaState.PLAYING -> "Play"
        PresetMediaState.PAUSED -> "Pause"
        PresetMediaState.STOPPED -> "Stop"
    }

private fun PresetStepResult.resultText(): String =
    when (this) {
        PresetStepResult.PENDING -> "waiting"
        PresetStepResult.CHECKING -> "checking"
        PresetStepResult.WRITING -> "awaiting confirmation"
        PresetStepResult.CONFIRMED -> "confirmed"
        PresetStepResult.ALREADY_MATCHED -> "already matched"
        PresetStepResult.REJECTED -> "rejected"
        PresetStepResult.UNKNOWN -> "unknown"
        PresetStepResult.NOT_RUN -> "not run"
    }

private val stopLabels =
    mapOf(
        PresetStopReason.INVALID_PLAN to "Preparation is unavailable.",
        PresetStopReason.EXPIRED to "Preparation expired.",
        PresetStopReason.CONSENT_REQUIRED to "Output confirmation was required.",
        PresetStopReason.WORKSPACE_CHANGED to "Connection or collection changed.",
        PresetStopReason.PRESET_CHANGED to "The saved preset changed.",
        PresetStopReason.POLICY_CHANGED to "Input locks, allowed inputs or Output Safety changed.",
        PresetStopReason.TARGET_CHANGED to "An OBS target was renamed or removed.",
        PresetStopReason.UNSUPPORTED to "A required control is unsupported.",
        PresetStopReason.MISSING to "An OBS target is missing.",
        PresetStopReason.LOCKED to "An input is locked.",
        PresetStopReason.NOT_ALLOWED to "An input is outside the allowed list.",
        PresetStopReason.STUDIO_OFF to "Studio mode is off.",
        PresetStopReason.NOT_READY to "A target is not ready.",
        PresetStopReason.READ_UNKNOWN to "A target could not be checked.",
        PresetStopReason.BUSY to "A media or recording tool is busy.",
        PresetStopReason.WRITE_REJECTED to "OBS rejected a step.",
        PresetStopReason.WRITE_UNKNOWN to "A write result is unknown.",
        PresetStopReason.UNCONFIRMED to "OBS did not confirm the target in time.",
        PresetStopReason.TIMEOUT to "The run reached its time limit.",
        PresetStopReason.CANCELLED to "The run was cancelled.",
        PresetStopReason.PROCESS_ENDED to "The previous run was not resumed.",
        PresetStopReason.HISTORY_FAILED to "Progress could not be saved to History.",
        PresetStopReason.SOURCE_UNAVAILABLE to "Required state became unavailable.",
    )
