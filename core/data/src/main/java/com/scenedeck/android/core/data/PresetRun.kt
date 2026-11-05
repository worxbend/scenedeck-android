package com.scenedeck.android.core.data

import kotlinx.serialization.Serializable

@Serializable
enum class PresetStepResult {
    PENDING,
    CHECKING,
    WRITING,
    CONFIRMED,
    ALREADY_MATCHED,
    REJECTED,
    UNKNOWN,
    NOT_RUN,
}

@Serializable
enum class PresetRunOutcome {
    RUNNING,
    COMPLETED,
    STOPPED,
    CANCELLED,
    INTERRUPTED,
}

/** Closed vocabulary: no server comments, transport details, paths or credentials. */
@Serializable
enum class PresetStopReason {
    INVALID_PLAN,
    EXPIRED,
    CONSENT_REQUIRED,
    WORKSPACE_CHANGED,
    PRESET_CHANGED,
    POLICY_CHANGED,
    TARGET_CHANGED,
    UNSUPPORTED,
    MISSING,
    LOCKED,
    NOT_ALLOWED,
    STUDIO_OFF,
    NOT_READY,
    READ_UNKNOWN,
    BUSY,
    WRITE_REJECTED,
    WRITE_UNKNOWN,
    UNCONFIRMED,
    TIMEOUT,
    CANCELLED,
    PROCESS_ENDED,
    HISTORY_FAILED,
    SOURCE_UNAVAILABLE,
}

@Serializable data class PresetRunStep(val target: PresetStep, val result: PresetStepResult)

/** A saved definition snapshot and actual progress, independent of later preset edits. */
@Serializable
data class PresetRun(
    val id: String,
    val presetId: String,
    val presetName: String,
    val presetRevision: Long,
    val startedAtMs: Long,
    val steps: List<PresetRunStep>,
    val revision: Long = 0,
    val outcome: PresetRunOutcome = PresetRunOutcome.RUNNING,
    val reason: PresetStopReason? = null,
    val finishedAtMs: Long? = null,
)

enum class PresetHistoryStatus {
    SAVED,
    NOT_RECORDED,
    LIMIT_REACHED,
    REMOVED,
    FAILED,
}

data class PresetRunState(
    val origin: WorkspaceSession,
    val run: PresetRun,
    val history: PresetHistoryStatus = PresetHistoryStatus.NOT_RECORDED,
)

/** Completed actions are retained. A possibly dispatched write cannot be called unsent. */
internal fun PresetRun.finish(
    outcome: PresetRunOutcome,
    reason: PresetStopReason?,
    atMs: Long?,
): PresetRun =
    copy(
        revision = Math.addExact(revision, 1),
        outcome = outcome,
        reason = reason,
        finishedAtMs = atMs,
        steps =
            steps.map {
                it.copy(
                    result =
                        when (it.result) {
                            PresetStepResult.WRITING -> PresetStepResult.UNKNOWN
                            PresetStepResult.PENDING,
                            PresetStepResult.CHECKING -> PresetStepResult.NOT_RUN
                            else -> it.result
                        }
                )
            },
    )

internal fun PresetRun.step(index: Int, result: PresetStepResult): PresetRun =
    copy(
        revision = Math.addExact(revision, 1),
        steps =
            steps.mapIndexed { position, step ->
                if (position == index) step.copy(result = result) else step
            },
    )
