package com.scenedeck.android.core.data

import java.util.UUID

/** One serial execution owns progress. Observers can cancel it but never rewrite its results. */
internal class PresetRunProgress(
    private val plan: PresetPlan,
    private val history: PresetRunHistory,
    atMs: Long,
    private val publish: (PresetRunState) -> Unit,
) {
    private val link = history.capture(plan.origin)
    private var writable = false
    private var historyStatus = PresetHistoryStatus.NOT_RECORDED
    var run =
        PresetRun(
            "run:${UUID.randomUUID()}",
            plan.preset.id,
            plan.preset.name,
            plan.preset.revision,
            atMs,
            plan.preset.steps.map { PresetRunStep(it, PresetStepResult.PENDING) },
        )
        private set

    init {
        emit()
    }

    suspend fun start() {
        writable = link != null
        historyStatus =
            requestResult { history.start(plan.origin.key, link, run) }
                .getOrDefault(PresetHistoryStatus.FAILED)
        writable = historyStatus == PresetHistoryStatus.SAVED
        emit()
        requireHistory()
    }

    suspend fun step(index: Int, result: PresetStepResult) {
        run = run.step(index, result)
        emit()
        save()
        requireHistory()
    }

    suspend fun finish(outcome: PresetRunOutcome, reason: PresetStopReason?, atMs: Long?) {
        run = run.finish(outcome, reason, atMs)
        emit()
        save()
    }

    private suspend fun save() {
        if (writable && link != null) {
            historyStatus =
                requestResult { history.save(link, run) }.getOrDefault(PresetHistoryStatus.FAILED)
            if (historyStatus == PresetHistoryStatus.REMOVED) writable = false
        }
        emit()
    }

    fun historyFailed() {
        historyStatus = PresetHistoryStatus.FAILED
        emit()
    }

    private fun requireHistory() {
        if (historyStatus == PresetHistoryStatus.FAILED)
            throw PresetExecutionStopped(PresetStopReason.HISTORY_FAILED)
    }

    private fun emit() = publish(PresetRunState(plan.origin, run, historyStatus))
}
