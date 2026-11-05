package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.ActionPresetEntity
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_PRESET_PAYLOAD_LENGTH = 32_768
private val presetJson = Json { encodeDefaults = true }

@Serializable private data class PresetPayload(val version: Int = 1, val steps: List<PresetStep>)

internal fun String.presetNameKey() = lowercase(Locale.ROOT)

internal fun PresetDraft.checked(): PresetDraft {
    val trimmed = name.trim()
    requirePreset(id.isNotBlank() && id.length <= 80)
    requirePreset(trimmed.isNotEmpty() && trimmed.length <= MAX_PRESET_NAME_LENGTH)
    requirePreset(steps.size in 1..MAX_PRESET_STEPS)
    steps.forEach { it.checkPresetStep() }
    return copy(name = trimmed, steps = steps.toList())
}

internal fun PresetStep.checkPresetStep() {
    when (this) {
        is PresetStep.Scene -> checkPresetTarget(name)
        is PresetStep.Mute -> checkPresetTarget(inputName)
        is PresetStep.Media -> checkPresetTarget(inputName)
        is PresetStep.Volume -> {
            checkPresetTarget(inputName)
            // Matches the existing Mixer fader's zero-to-unity range; never amplify a preset.
            requirePreset(multiplier.isFinite() && multiplier in 0.0..1.0)
        }
        is PresetStep.Output -> Unit
    }
}

private fun checkPresetTarget(name: String) {
    requirePreset(name.isNotBlank() && name.length <= 1_024)
}

internal fun requirePreset(value: Boolean, reason: PresetEditFailure = PresetEditFailure.INVALID) {
    if (!value) throw PresetEditException(reason)
}

internal fun ActionPreset.encodePreset(): ActionPresetEntity {
    val checked = draft().checked()
    requirePreset(revision >= 0 && workspace.profileId > 0 && workspace.collectionName.isNotBlank())
    val payload = presetJson.encodeToString(PresetPayload(steps = checked.steps))
    requirePreset(payload.length <= MAX_PRESET_PAYLOAD_LENGTH)
    return ActionPresetEntity(
        workspace.profileId,
        workspace.collectionName,
        id,
        checked.name,
        checked.name.presetNameKey(),
        revision,
        payload,
    )
}

internal fun ActionPresetEntity.decodePreset(): ActionPreset {
    requirePreset(payload.length <= MAX_PRESET_PAYLOAD_LENGTH, PresetEditFailure.STORAGE)
    val decoded = presetJson.decodeFromString<PresetPayload>(payload)
    requirePreset(decoded.version == 1, PresetEditFailure.STORAGE)
    val preset =
        ActionPreset(
            WorkspaceKey(connectionProfileId, sceneCollectionName),
            presetId,
            name,
            revision,
            decoded.steps,
        )
    val canonical = preset.encodePreset()
    requirePreset(canonical.name == name && canonical.nameKey == nameKey, PresetEditFailure.STORAGE)
    return preset
}

internal fun List<ActionPresetEntity>.decodePresets(): List<ActionPreset> {
    requirePreset(size <= MAX_ACTION_PRESETS, PresetEditFailure.STORAGE)
    return map { it.decodePreset() }
}
