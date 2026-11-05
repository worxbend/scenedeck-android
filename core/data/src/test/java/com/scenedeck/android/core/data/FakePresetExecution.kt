package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakePresetExecution : PresetExecutionSource {
    override val events = MutableSharedFlow<ObsSessionEvent>()
    val interruptions = MutableSharedFlow<PresetStopReason>()
    val writes = mutableListOf<PresetStep>()
    val reads = mutableListOf<PresetStep>()
    val values = mutableMapOf<String, PresetStep>()
    var beforeRead: suspend () -> Unit = {}
    var beforeWrite: suspend () -> Unit = {}
    var validate: suspend () -> Unit = {}
    var applyWrites = true
    var check: PresetCheck? = null

    override fun interruptions(plan: PresetPlan) = interruptions

    override suspend fun validate(plan: PresetPlan) = validate()

    override suspend fun inspect(plan: PresetPlan, step: PresetStep): PresetCheck {
        reads += step
        beforeRead()
        return check
            ?: if (values[key(step)] == step) PresetCheck.CURRENTLY_MATCHES else PresetCheck.READY
    }

    override suspend fun write(plan: PresetPlan, step: PresetStep) {
        writes += step
        beforeWrite()
        if (applyWrites) values[key(step)] = step
    }

    private fun key(step: PresetStep): String =
        when (step) {
            is PresetStep.Scene -> "scene:${step.destination}"
            is PresetStep.Output -> "output:${step.output}"
            else -> "${step.control}:${step.input}"
        }
}

internal class FakePresetHistory : PresetRunHistory {
    val link = HistoryBookmarkLink("observation", 1_000)
    val saved = mutableListOf<PresetRun>()
    var startStatus = PresetHistoryStatus.SAVED
    var beforeSave: suspend (PresetRun) -> Unit = {}

    override fun capture(origin: WorkspaceSession) = link

    override suspend fun start(
        workspace: WorkspaceKey,
        link: HistoryBookmarkLink?,
        run: PresetRun,
    ): PresetHistoryStatus {
        saved += run
        return startStatus
    }

    override suspend fun save(link: HistoryBookmarkLink, run: PresetRun): PresetHistoryStatus {
        check(link == this.link)
        beforeSave(run)
        run.checkedRun()
        if (saved.isNotEmpty()) run.requireSuccessor(saved.last())
        saved += run
        return PresetHistoryStatus.SAVED
    }
}

internal fun executionPlan(
    steps: List<PresetStep> = listOf(PresetStep.Mute("Mic", true), PresetStep.Mute("Mic", false))
) =
    PresetPlan(
        TEST_WORKSPACE,
        ActionPreset(TEST_WORKSPACE.key, "preset", "Interview", 0, steps),
        0,
        List(steps.size) { PresetCheck.CURRENTLY_MATCHES },
        UserSettings().presetPolicy(),
    )
