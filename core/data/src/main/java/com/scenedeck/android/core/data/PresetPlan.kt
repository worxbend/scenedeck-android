package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsControl

enum class PresetCheck {
    READY,
    CURRENTLY_MATCHES,
    UNSUPPORTED,
    MISSING,
    LOCKED,
    NOT_ALLOWED,
    STUDIO_OFF,
    NOT_READY,
    UNKNOWN;

    val eligible: Boolean
        get() = this == READY || this == CURRENTLY_MATCHES
}

data class PresetPolicy(
    val lockedInputs: Set<String>,
    val allowedInputs: Set<String>,
    val outputSafety: OutputSafety,
)

/**
 * Read-only preparation. Current matches are observations, never instructions to skip a later step.
 */
data class PresetPlan(
    val origin: WorkspaceSession,
    val preset: ActionPreset,
    val preparedAtMs: Long,
    val checks: List<PresetCheck>,
    val policy: PresetPolicy,
) {
    val canRun: Boolean
        get() =
            checks.size == preset.steps.size && checks.isNotEmpty() && checks.all { it.eligible }

    /** Zero-based step positions; consent must describe the target even if it currently matches. */
    val outputConfirmations: List<Int>
        get() =
            preset.steps.mapIndexedNotNull { index, step ->
                (step as? PresetStep.Output)
                    ?.takeIf { it.needsConfirmation(policy.outputSafety) }
                    ?.let { index }
            }
}

internal fun UserSettings.presetPolicy() =
    PresetPolicy(lockedInputs.toSet(), audioAllowList.toSet(), outputSafety())

internal fun PresetStep.Output.needsConfirmation(safety: OutputSafety): Boolean =
    when (output) {
        PresetOutput.STREAM -> OutputSafetyGate.streamAction(!active, safety)
        PresetOutput.RECORDING -> OutputSafetyGate.recordAction(!active, safety)
    } == OutputAction.REQUIRE_CONFIRMATION

internal val PresetStep.control: ObsControl
    get() =
        when (this) {
            is PresetStep.Scene ->
                if (destination == PresetSceneDestination.PROGRAM) ObsControl.PROGRAM_SCENE
                else ObsControl.PREVIEW_SCENE
            is PresetStep.Mute -> ObsControl.INPUT_MUTE
            is PresetStep.Volume -> ObsControl.INPUT_VOLUME
            is PresetStep.Media -> ObsControl.MEDIA_STATE
            is PresetStep.Output ->
                if (output == PresetOutput.STREAM) ObsControl.STREAM else ObsControl.RECORDING
        }

internal val PresetStep.input: String?
    get() =
        when (this) {
            is PresetStep.Mute -> inputName
            is PresetStep.Volume -> inputName
            is PresetStep.Media -> inputName
            else -> null
        }
