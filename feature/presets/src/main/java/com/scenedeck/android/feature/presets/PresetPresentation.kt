package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.PresetCheck
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.ui.actionLabel

internal fun PresetWorkspaceUi.unavailable(step: PresetStep, origin: WorkspaceSession): String? {
    if (ready?.origin != origin) return null
    val inputName =
        when (step) {
            is PresetStep.Mute -> step.inputName
            is PresetStep.Volume -> step.inputName
            is PresetStep.Media -> step.inputName
            else -> null
        }
    return when {
        step is PresetStep.Scene && sceneNames != null && step.name !in sceneNames ->
            "Scene unavailable: ${step.name}"
        inputName != null &&
            inputOrigin == origin &&
            inputNames != null &&
            inputName !in inputNames -> "Input unavailable: $inputName"
        else -> null
    }
}

internal fun PresetStep.displayLabel(
    workspace: PresetWorkspaceUi,
    origin: WorkspaceSession,
): String {
    val scene = this as? PresetStep.Scene
    val alias = workspace.aliases[scene?.name].takeIf { workspace.ready?.origin == origin }
    return if (scene != null && alias != null) scene.copy(name = alias).actionLabel()
    else actionLabel()
}

internal fun PresetCheck.description(): String =
    when (this) {
        PresetCheck.READY -> "Ready"
        PresetCheck.CURRENTLY_MATCHES -> "Matches now · checked again during the run"
        PresetCheck.UNSUPPORTED -> "Control unsupported by this OBS connection"
        PresetCheck.MISSING -> "Target missing in OBS"
        PresetCheck.LOCKED -> "Input locked · change it in Mixer first"
        PresetCheck.NOT_ALLOWED -> "Input outside the Mixer allow-list"
        PresetCheck.STUDIO_OFF -> "Studio mode must be enabled for a preview target"
        PresetCheck.NOT_READY -> "Target is not ready"
        PresetCheck.UNKNOWN -> "Target state could not be checked"
    }
