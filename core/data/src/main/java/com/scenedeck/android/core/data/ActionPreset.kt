package com.scenedeck.android.core.data

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MAX_ACTION_PRESETS = 32
const val MAX_PRESET_STEPS = 12
const val MAX_PRESET_NAME_LENGTH = 80

/** A fixed sequence of target-value runtime controls; never a cue list or collection edit. */
@Serializable
sealed interface PresetStep {
    @Serializable
    @SerialName("scene")
    data class Scene(val name: String, val destination: PresetSceneDestination) : PresetStep

    @Serializable
    @SerialName("mute")
    data class Mute(val inputName: String, val muted: Boolean) : PresetStep

    @Serializable
    @SerialName("volume")
    data class Volume(val inputName: String, val multiplier: Double) : PresetStep

    @Serializable
    @SerialName("media")
    data class Media(val inputName: String, val state: PresetMediaState) : PresetStep

    @Serializable
    @SerialName("output")
    data class Output(val output: PresetOutput, val active: Boolean) : PresetStep
}

@Serializable
enum class PresetSceneDestination {
    PROGRAM,
    PREVIEW,
}

@Serializable
enum class PresetMediaState {
    PLAYING,
    PAUSED,
    STOPPED,
}

@Serializable
enum class PresetOutput {
    STREAM,
    RECORDING,
}

/** Scope/revision travel with the displayed value; an editor cannot silently retarget it. */
data class ActionPreset(
    val workspace: WorkspaceKey,
    val id: String,
    val name: String,
    val revision: Long,
    val steps: List<PresetStep>,
) {
    fun draft() = PresetDraft(id, name, steps.toList())
}

/** Keep one ID for an unsaved draft, including retries after a committed save. */
data class PresetDraft(
    val id: String = "preset:${UUID.randomUUID()}",
    val name: String = "",
    val steps: List<PresetStep> = emptyList(),
)

sealed interface PresetState {
    data class Unavailable(val workspace: WorkspaceState) : PresetState

    data class Loading(val origin: WorkspaceSession) : PresetState

    data class Failed(val origin: WorkspaceSession) : PresetState

    data class Ready(val origin: WorkspaceSession, val presets: List<ActionPreset>) : PresetState
}

enum class PresetEditFailure {
    INVALID,
    CHANGED,
    DUPLICATE_NAME,
    LIMIT,
    STORAGE,
}

class PresetEditException(val reason: PresetEditFailure) :
    IllegalStateException("Preset is unavailable or changed")
