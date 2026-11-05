package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

enum class PresetPreparationFailure {
    CHANGED,
    UNAVAILABLE,
    TIMEOUT,
}

class PresetPreparationException(val reason: PresetPreparationFailure) :
    IllegalStateException("Preset preparation is unavailable or changed")

/** Read-only preparation in saved order, bounded to three probes and 30 seconds in total. */
class PresetPreparation
@Inject
constructor(
    client: ObsClient,
    private val presets: PresetRepository,
    private val settings: SettingsRepository,
    private val clock: MonotonicClock,
) {
    private val probe = PresetStepProbe(client, presets.workspaces)

    suspend fun prepare(origin: WorkspaceSession, preset: ActionPreset): PresetPlan =
        requestResult {
            withTimeoutOrNull(30_000) { prepareCurrent(origin, preset) }
                ?: throw PresetPreparationException(PresetPreparationFailure.TIMEOUT)
        }
        .getOrElse { failure ->
            when (failure) {
                is WorkspaceChangedException,
                is PresetPreparationException -> throw failure
                else -> throw PresetPreparationException(PresetPreparationFailure.UNAVAILABLE)
            }
        }

    private suspend fun prepareCurrent(origin: WorkspaceSession, preset: ActionPreset): PresetPlan {
        val selected = preset.copy(steps = preset.steps.toList())
        requireSaved(origin, selected)
        val policy = settings.settings.first().presetPolicy()
        val permits = Semaphore(3)
        val checks = coroutineScope {
            selected.steps
                .map { step ->
                    async { permits.withPermit { probe.inspect(origin, step, policy) } }
                }
                .awaitAll()
        }
        requireSaved(origin, selected)
        if (settings.settings.first().presetPolicy() != policy)
            throw PresetPreparationException(PresetPreparationFailure.CHANGED)
        presets.workspaces.requireCurrent(origin)
        return PresetPlan(origin, selected, clock.nowMs(), checks, policy)
    }

    private suspend fun requireSaved(origin: WorkspaceSession, preset: ActionPreset) {
        if (
            preset.workspace != origin.key ||
                presets.snapshot(origin).find { it.id == preset.id } != preset
        )
            throw PresetPreparationException(PresetPreparationFailure.CHANGED)
    }
}
