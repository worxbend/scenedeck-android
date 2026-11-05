package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.HistoryPresetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val runJson = Json { encodeDefaults = true }
private const val MAX_RUN_PAYLOAD = 40_960

@Serializable private data class PresetRunPayload(val version: Int = 1, val run: PresetRun)

internal fun PresetRun.checkedRun(): PresetRun {
    id.checkedHistoryId()
    checkHistory(presetId.isNotBlank() && presetId.length <= 80)
    checkHistory(
        presetName.isNotBlank() &&
            presetName.trim() == presetName &&
            presetName.length <= MAX_PRESET_NAME_LENGTH
    )
    checkHistory(presetRevision >= 0 && revision >= 0 && startedAtMs >= 0)
    checkHistory(steps.size in 1..MAX_PRESET_STEPS)
    steps.forEach { it.target.checkPresetStep() }
    checkOutcome()
    val remaining = steps.dropWhile { it.result in completedResults }.drop(1)
    val pending =
        if (outcome == PresetRunOutcome.RUNNING) PresetStepResult.PENDING
        else PresetStepResult.NOT_RUN
    checkHistory(remaining.all { it.result == pending })
    return this
}

private fun PresetRun.checkOutcome() {
    val running = outcome == PresetRunOutcome.RUNNING
    checkHistory(!running || (reason == null && finishedAtMs == null))
    checkHistory(finishedAtMs == null || finishedAtMs >= 0)
    checkHistory(running || outcome == PresetRunOutcome.INTERRUPTED || finishedAtMs != null)
    checkHistory(running || steps.none { it.result in unfinishedResults })
    if (outcome == PresetRunOutcome.COMPLETED) {
        checkHistory(
            reason == null && finishedAtMs != null && steps.all { it.result in completedResults }
        )
    } else if (!running) checkHistory(reason != null)
    checkHistory(
        outcome != PresetRunOutcome.INTERRUPTED ||
            (reason == PresetStopReason.PROCESS_ENDED && finishedAtMs == null)
    )
}

internal val completedResults = setOf(PresetStepResult.CONFIRMED, PresetStepResult.ALREADY_MATCHED)
private val unfinishedResults =
    setOf(PresetStepResult.PENDING, PresetStepResult.CHECKING, PresetStepResult.WRITING)

internal fun PresetRun.encodeRun(): String =
    runJson.encodeToString(PresetRunPayload(run = checkedRun())).also {
        checkHistory(it.length <= MAX_RUN_PAYLOAD)
    }

internal fun HistoryPresetEntity.decodeRun(): HistoryPresetRun {
    checkHistory(payload.length <= MAX_RUN_PAYLOAD && elapsedMs >= 0)
    sessionId.checkedHistoryId()
    processId.checkedHistoryId()
    val decoded = runJson.decodeFromString<PresetRunPayload>(payload)
    checkHistory(decoded.version == 1)
    val run = decoded.run.checkedRun()
    checkHistory(
        run.id == runId &&
            run.revision == revision &&
            running == (run.outcome == PresetRunOutcome.RUNNING)
    )
    return HistoryPresetRun(elapsedMs, run)
}

internal fun PresetRun.requireSuccessor(previous: PresetRun) {
    checkHistory(previous.outcome == PresetRunOutcome.RUNNING && revision > previous.revision)
    checkHistory(
        copy(
            steps = previous.steps,
            revision = previous.revision,
            outcome = previous.outcome,
            reason = previous.reason,
            finishedAtMs = previous.finishedAtMs,
        ) == previous
    )
    checkHistory(steps.map { it.target } == previous.steps.map { it.target })
    previous.steps.zip(steps).forEach { (old, new) ->
        if (old.result in completedResults) checkHistory(old.result == new.result)
        if (old.result == PresetStepResult.WRITING)
            checkHistory(
                new.result in
                    setOf(
                        PresetStepResult.WRITING,
                        PresetStepResult.CONFIRMED,
                        PresetStepResult.REJECTED,
                        PresetStepResult.UNKNOWN,
                    )
            )
    }
}
