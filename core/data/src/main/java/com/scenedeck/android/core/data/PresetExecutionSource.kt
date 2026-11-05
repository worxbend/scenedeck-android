package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

interface PresetExecutionSource {
    val events: Flow<ObsSessionEvent>

    fun interruptions(plan: PresetPlan): Flow<PresetStopReason>

    suspend fun validate(plan: PresetPlan)

    suspend fun inspect(plan: PresetPlan, step: PresetStep): PresetCheck

    suspend fun write(plan: PresetPlan, step: PresetStep)
}

internal class PresetExecutionStopped(val reason: PresetStopReason) :
    IllegalStateException("Preset execution stopped")

@Singleton
class SharedPresetExecutionSource
@Inject
constructor(
    private val client: ObsClient,
    private val presets: PresetRepository,
    private val settings: SettingsRepository,
    private val mixer: MixerRepository,
    private val recording: RecordingToolsRepository,
) : PresetExecutionSource {
    private val probe = PresetStepProbe(client, presets.workspaces)
    override val events = client.eventsWithContext

    override fun interruptions(plan: PresetPlan): Flow<PresetStopReason> =
        merge(
                presets.workspaces.state.map { contextFailure(plan) },
                client.connectionState.map { contextFailure(plan) },
                settings.settings.map {
                    if (it.presetPolicy() != plan.policy) PresetStopReason.POLICY_CHANGED else null
                },
                presets.state.map { it.interruption(plan) },
                mixer.mediaStatus.map { if (busy(plan)) PresetStopReason.BUSY else null },
                recording.state.map { if (busy(plan)) PresetStopReason.BUSY else null },
            )
            .filterNotNull()

    override suspend fun validate(plan: PresetPlan) {
        contextFailure(plan)?.let { throw PresetExecutionStopped(it) }
        if (presets.snapshot(plan.origin).find { it.id == plan.preset.id } != plan.preset)
            throw PresetExecutionStopped(PresetStopReason.PRESET_CHANGED)
        if (settings.settings.first().presetPolicy() != plan.policy)
            throw PresetExecutionStopped(PresetStopReason.POLICY_CHANGED)
        if (busy(plan)) throw PresetExecutionStopped(PresetStopReason.BUSY)
        contextFailure(plan)?.let { throw PresetExecutionStopped(it) }
    }

    override suspend fun inspect(plan: PresetPlan, step: PresetStep) =
        probe.inspect(plan.origin, step, plan.policy)

    override suspend fun write(plan: PresetPlan, step: PresetStep) {
        validate(plan)
        client.withSession(plan.origin.token) { client.applyPresetStep(step) }
        contextFailure(plan)?.let { throw PresetExecutionStopped(it) }
    }

    private fun contextFailure(plan: PresetPlan): PresetStopReason? {
        val ready = client.connectionState.value as? ConnectionState.Ready
        return if (
            !presets.workspaces.isCurrent(plan.origin) ||
                ready?.token != plan.origin.token ||
                ready.collectionChanging
        )
            PresetStopReason.WORKSPACE_CHANGED
        else null
    }

    private fun busy(plan: PresetPlan): Boolean =
        plan.preset.steps.any { step ->
            when (step) {
                is PresetStep.Media ->
                    mixer.mediaStatus.value.let {
                        it.workspace == plan.origin && it.readings[step.inputName]?.seeking == true
                    }
                is PresetStep.Output ->
                    step.output == PresetOutput.RECORDING && recording.state.value.busy
                else -> false
            }
        }
}

private fun PresetState.interruption(plan: PresetPlan): PresetStopReason? =
    when (this) {
        is PresetState.Ready ->
            if (origin != plan.origin || presets.find { it.id == plan.preset.id } != plan.preset)
                PresetStopReason.PRESET_CHANGED
            else null
        is PresetState.Failed -> PresetStopReason.SOURCE_UNAVAILABLE
        else -> null
    }

private suspend fun ObsClient.applyPresetStep(step: PresetStep) {
    when (step) {
        is PresetStep.Scene ->
            if (step.destination == PresetSceneDestination.PROGRAM)
                setCurrentProgramScene(step.name)
            else setCurrentPreviewScene(step.name)
        is PresetStep.Mute -> setInputMute(step.inputName, step.muted)
        is PresetStep.Volume -> setInputVolume(step.inputName, step.multiplier)
        is PresetStep.Media ->
            triggerMediaInputAction(
                step.inputName,
                when (step.state) {
                    PresetMediaState.PLAYING -> MediaActionKind.PLAY
                    PresetMediaState.PAUSED -> MediaActionKind.PAUSE
                    PresetMediaState.STOPPED -> MediaActionKind.STOP
                },
            )
        is PresetStep.Output -> applyPresetOutput(step)
    }
}

private suspend fun ObsClient.applyPresetOutput(step: PresetStep.Output) {
    when (step.output) {
        PresetOutput.STREAM -> if (step.active) startStream() else stopStream()
        PresetOutput.RECORDING -> if (step.active) startRecord() else stopRecord()
    }
}

fun ObsEvent.invalidatesPreset(preset: ActionPreset): Boolean {
    val scenes = preset.steps.filterIsInstance<PresetStep.Scene>().map { it.name }
    val inputs = preset.steps.mapNotNull { it.input }
    return when (this) {
        is ObsEvent.SceneNameChanged -> oldSceneName in scenes
        is ObsEvent.SceneRemoved -> sceneName in scenes
        is ObsEvent.InputNameChanged -> oldInputName in inputs
        is ObsEvent.InputRemoved -> inputName in inputs
        else -> false
    }
}
