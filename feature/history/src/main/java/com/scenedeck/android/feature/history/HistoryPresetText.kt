package com.scenedeck.android.feature.history

import com.scenedeck.android.core.data.HistoryPresetRun
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetStepResult
import com.scenedeck.android.core.ui.actionLabel
import com.scenedeck.android.core.ui.explanation
import com.scenedeck.android.core.ui.resultLabel

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
            run.reason?.let { add(it.explanation()) }
            run.finishedAtMs?.let { add("Finished · ${bookmarkDate(it)}") }
            run.steps.forEachIndexed { index, step ->
                add("${index + 1}. ${step.target.actionLabel()} · ${step.result.resultLabel()}")
            }
            if (run.steps.any { it.result == PresetStepResult.UNKNOWN })
                add("An unknown write may have taken effect. Check OBS before running again.")
        },
        Long.MAX_VALUE - 1,
    )
