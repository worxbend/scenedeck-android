package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.PresetMediaState
import com.scenedeck.android.core.data.PresetOutput
import com.scenedeck.android.core.data.PresetSceneDestination
import com.scenedeck.android.core.data.PresetStep
import java.util.UUID

internal enum class PresetStepKind(val label: String) {
    PROGRAM("Program scene"),
    PREVIEW("Preview scene"),
    MUTE("Mute"),
    VOLUME("Volume"),
    MEDIA("Media"),
    STREAM("Stream"),
    RECORDING("Recording");

    val scene: Boolean
        get() = this == PROGRAM || this == PREVIEW

    val input: Boolean
        get() = this == MUTE || this == VOLUME || this == MEDIA
}

internal data class PresetStepEditor(
    val id: String = UUID.randomUUID().toString(),
    val existingId: String? = null,
    val kind: PresetStepKind = PresetStepKind.PROGRAM,
    val target: String = "",
    val enabled: Boolean = true,
    val volumePercent: String = "100",
    val media: PresetMediaState = PresetMediaState.PLAYING,
) {
    fun changeKind(value: PresetStepKind) =
        copy(
            kind = value,
            target = if (kind.scene == value.scene && kind.input == value.input) target else "",
        )

    fun step(): PresetStep? {
        val needsTarget = kind.scene || kind.input
        if (needsTarget && (target.isBlank() || target.length > 1_024)) return null
        return when (kind) {
            PresetStepKind.PROGRAM -> PresetStep.Scene(target, PresetSceneDestination.PROGRAM)
            PresetStepKind.PREVIEW -> PresetStep.Scene(target, PresetSceneDestination.PREVIEW)
            PresetStepKind.MUTE -> PresetStep.Mute(target, enabled)
            PresetStepKind.VOLUME ->
                volumePercent
                    .replace(',', '.')
                    .toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it in 0.0..100.0 }
                    ?.let { PresetStep.Volume(target, it / 100) }
            PresetStepKind.MEDIA -> PresetStep.Media(target, media)
            PresetStepKind.STREAM -> PresetStep.Output(PresetOutput.STREAM, enabled)
            PresetStepKind.RECORDING -> PresetStep.Output(PresetOutput.RECORDING, enabled)
        }
    }
}

internal fun PresetEditorStep.editor(): PresetStepEditor =
    when (val value = step) {
        is PresetStep.Scene ->
            PresetStepEditor(
                existingId = id,
                kind =
                    if (value.destination == PresetSceneDestination.PROGRAM) PresetStepKind.PROGRAM
                    else PresetStepKind.PREVIEW,
                target = value.name,
            )
        is PresetStep.Mute ->
            PresetStepEditor(
                existingId = id,
                kind = PresetStepKind.MUTE,
                target = value.inputName,
                enabled = value.muted,
            )
        is PresetStep.Volume ->
            PresetStepEditor(
                existingId = id,
                kind = PresetStepKind.VOLUME,
                target = value.inputName,
                volumePercent = (value.multiplier * 100).toString(),
            )
        is PresetStep.Media ->
            PresetStepEditor(
                existingId = id,
                kind = PresetStepKind.MEDIA,
                target = value.inputName,
                media = value.state,
            )
        is PresetStep.Output ->
            PresetStepEditor(
                existingId = id,
                kind =
                    if (value.output == PresetOutput.STREAM) PresetStepKind.STREAM
                    else PresetStepKind.RECORDING,
                enabled = value.active,
            )
    }
