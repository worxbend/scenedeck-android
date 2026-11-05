package com.scenedeck.android.core.data

import kotlinx.serialization.Serializable

const val MAX_PREFLIGHT_SCENES = 100
const val MAX_MANUAL_CHECKS = 30
const val MAX_MANUAL_CHECK_LABEL = 120

@Serializable
data class ManualPreflightCheck(val id: String, val label: String, val complete: Boolean = false)

@Serializable
data class PreflightConfig(
    val expectedCollection: String,
    val requiredScenes: List<String> = emptyList(),
    val microphone: String? = null,
    val minimumDiskMb: Long = 2_048,
    val manualChecks: List<ManualPreflightCheck> = emptyList(),
    val version: Int = 1,
)

class PreflightEditException : IllegalArgumentException("Preflight setup is invalid or changed")

internal fun PreflightConfig.checked(previous: PreflightConfig? = null): PreflightConfig {
    validSetup(version == 1)
    validSetup(validReference(expectedCollection))
    validSetup(requiredScenes.size <= MAX_PREFLIGHT_SCENES)
    validSetup(requiredScenes.all(::validReference))
    validSetup(microphone == null || validReference(microphone))
    validSetup(minimumDiskMb in 1..1_000_000)
    validSetup(manualChecks.size <= MAX_MANUAL_CHECKS)
    validSetup(manualChecks.map { it.id }.distinct().size == manualChecks.size)
    val before = previous?.manualChecks?.associateBy { it.id }.orEmpty()
    val manual = manualChecks.map { it.checked(before[it.id]) }
    return copy(requiredScenes = requiredScenes.distinct(), manualChecks = manual)
}

private fun ManualPreflightCheck.checked(previous: ManualPreflightCheck?): ManualPreflightCheck {
    val trimmed = label.trim()
    validSetup(id.isNotBlank() && id.length <= 80)
    validSetup(trimmed.isNotBlank() && trimmed.length <= MAX_MANUAL_CHECK_LABEL)
    validSetup(trimmed.none(Char::isISOControl))
    return copy(
        label = trimmed,
        complete = complete && (previous == null || previous.label == trimmed),
    )
}

private fun validSetup(valid: Boolean) {
    if (!valid) throw PreflightEditException()
}

private fun validReference(value: String) = value.isNotBlank() && value.length <= 4_096

sealed interface PreflightSetupState {
    data class Unavailable(val workspace: WorkspaceState) : PreflightSetupState

    data class Loading(val origin: WorkspaceSession) : PreflightSetupState

    data class Failed(val origin: WorkspaceSession) : PreflightSetupState

    data class Ready(val origin: WorkspaceSession, val config: PreflightConfig) :
        PreflightSetupState
}
