package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.MonotonicClock
import com.scenedeck.android.core.data.PresetExecutionSource
import com.scenedeck.android.core.data.PresetPlan
import com.scenedeck.android.core.data.PresetPreparation
import com.scenedeck.android.core.data.PresetRepository
import com.scenedeck.android.core.data.PresetRunLauncher
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.invalidatesPreset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PresetReviewPhase {
    CHECKING,
    READY,
    FAILED,
    EXPIRED,
    CHANGED,
}

internal data class PresetReview(
    val id: String = UUID.randomUUID().toString(),
    val origin: WorkspaceSession,
    val preset: ActionPreset,
    val phase: PresetReviewPhase = PresetReviewPhase.CHECKING,
    val plan: PresetPlan? = null,
    val acknowledged: Set<Int> = emptySet(),
    val message: String? = null,
) {
    val canRun: Boolean
        get() =
            phase == PresetReviewPhase.READY &&
                plan?.canRun == true &&
                acknowledged.containsAll(plan.outputConfirmations)
}

internal data class PresetReviewUi(val review: PresetReview? = null, val notice: String? = null)

internal class PresetReviewController(
    private val repository: PresetRepository,
    private val preparation: PresetPreparation,
    private val source: PresetExecutionSource,
    private val launcher: PresetRunLauncher,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow(PresetReviewUi())
    val state = current.asStateFlow()
    private var job: Job? = null

    fun prepare(origin: WorkspaceSession, preset: ActionPreset) {
        if (launcher.busy.value || !repository.workspaces.isCurrent(origin)) return
        close()
        val review = PresetReview(origin = origin, preset = preset)
        current.value = PresetReviewUi(review)
        val preparationJob = scope.launch(start = CoroutineStart.LAZY) { observeAndPrepare(review) }
        job = preparationJob
        preparationJob.start()
    }

    fun close() {
        job?.cancel()
        job = null
        current.value = PresetReviewUi()
    }

    fun acknowledge(id: String, index: Int, checked: Boolean) {
        val review =
            current.value.review?.takeIf { it.id == id && it.phase == PresetReviewPhase.READY }
                ?: return
        if (index !in review.plan!!.outputConfirmations) return
        current.value =
            PresetReviewUi(
                review.copy(
                    acknowledged =
                        if (checked) review.acknowledged + index else review.acknowledged - index
                )
            )
    }

    fun run(id: String) {
        val review = current.value.review?.takeIf { it.id == id && it.canRun } ?: return
        val plan = review.plan!!
        val valid =
            repository.workspaces.isCurrent(review.origin) &&
                clock.nowMs() - plan.preparedAtMs in 0..59_999
        if (!valid) {
            invalidate(
                id,
                PresetReviewPhase.CHANGED,
                "Preparation expired or the workspace changed. Prepare again.",
            )
            return
        }
        close() // Consume before launching. Obsolete/double-tap callbacks have no matching review.
        if (!launcher.start(plan, review.acknowledged))
            current.value = PresetReviewUi(notice = "Another preset is already running.")
    }

    private suspend fun observeAndPrepare(review: PresetReview) = coroutineScope {
        val events =
            launch(start = CoroutineStart.UNDISPATCHED) {
                coroutineResult {
                    source.events.collect { event ->
                        if (
                            event.token == review.origin.token &&
                                event.event.invalidatesPreset(review.preset)
                        )
                            invalidate(
                                review.id,
                                PresetReviewPhase.CHANGED,
                                "An OBS target changed. Prepare again.",
                            )
                    }
                }
                invalidate(
                    review.id,
                    PresetReviewPhase.CHANGED,
                    "Live updates became unavailable. Prepare again.",
                )
            }
        try {
            val result = coroutineResult { preparation.prepare(review.origin, review.preset) }
            val plan = result.getOrNull()
            if (plan == null)
                invalidate(
                    review.id,
                    PresetReviewPhase.FAILED,
                    "Couldn't prepare this preset. Check OBS and try again.",
                )
            else awaitConsent(review, plan)
        } finally {
            events.cancel()
        }
    }

    private suspend fun awaitConsent(review: PresetReview, plan: PresetPlan) = coroutineScope {
        if (
            current.value.review?.id != review.id ||
                current.value.review?.phase != PresetReviewPhase.CHECKING
        )
            return@coroutineScope
        current.value = PresetReviewUi(review.copy(phase = PresetReviewPhase.READY, plan = plan))
        val changes =
            launch(start = CoroutineStart.UNDISPATCHED) {
                coroutineResult {
                    source.interruptions(plan).collect {
                        invalidate(
                            review.id,
                            PresetReviewPhase.CHANGED,
                            "The preset, workspace or controls changed. Prepare again.",
                        )
                    }
                }
                invalidate(
                    review.id,
                    PresetReviewPhase.CHANGED,
                    "Live updates became unavailable. Prepare again.",
                )
            }
        try {
            delay((60_000 - (clock.nowMs() - plan.preparedAtMs)).coerceIn(0, 60_000))
            invalidate(
                review.id,
                PresetReviewPhase.EXPIRED,
                "Preparation expired. Prepare again before running.",
            )
        } finally {
            changes.cancel()
        }
    }

    private fun invalidate(id: String, phase: PresetReviewPhase, message: String) {
        val review = current.value.review?.takeIf { it.id == id } ?: return
        current.value =
            PresetReviewUi(
                review.copy(
                    phase = phase,
                    plan = null,
                    acknowledged = emptySet(),
                    message = message,
                )
            )
        job?.cancel()
    }
}
