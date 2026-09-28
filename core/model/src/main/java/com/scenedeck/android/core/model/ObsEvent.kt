package com.scenedeck.android.core.model

/**
 * Domain events pushed by OBS over the WebSocket (op 5). Volume meters are NOT
 * part of this hierarchy — they stream on their own high-volume flow.
 */
sealed interface ObsEvent {

    // ── Scenes ──────────────────────────────────────────────────────────────
    data class CurrentProgramSceneChanged(val sceneName: String) : ObsEvent

    data class SceneListChanged(val scenes: List<SceneSummary>) : ObsEvent

    data class SceneCreated(val sceneName: String, val isGroup: Boolean) : ObsEvent

    data class SceneRemoved(val sceneName: String) : ObsEvent

    data class SceneNameChanged(val oldSceneName: String, val sceneName: String) : ObsEvent

    // ── Scene items ─────────────────────────────────────────────────────────
    data class SceneItemEnableStateChanged(
        val sceneName: String,
        val sceneItemId: Int,
        val enabled: Boolean,
    ) : ObsEvent

    // ── Inputs ──────────────────────────────────────────────────────────────
    data class InputCreated(val inputName: String, val inputKind: String) : ObsEvent

    data class InputRemoved(val inputName: String) : ObsEvent

    data class InputNameChanged(val oldInputName: String, val inputName: String) : ObsEvent

    data class InputMuteStateChanged(val inputName: String, val muted: Boolean) : ObsEvent

    data class InputVolumeChanged(
        val inputName: String,
        val volumeMul: Double,
        val volumeDb: Double,
    ) : ObsEvent

    // ── Outputs ─────────────────────────────────────────────────────────────
    /** [state] is the raw OBS state (e.g. `OBS_WEBSOCKET_OUTPUT_STARTED`). */
    data class StreamStateChanged(val active: Boolean, val state: String) : ObsEvent

    data class RecordStateChanged(
        val active: Boolean,
        val state: String,
        val outputPath: String?,
    ) : ObsEvent

    // ── Config ──────────────────────────────────────────────────────────────
    data class CurrentProfileChanged(val profileName: String) : ObsEvent

    data class ProfileListChanged(val profiles: List<String>) : ObsEvent

    data class CurrentSceneCollectionChanged(val collectionName: String) : ObsEvent

    data class SceneCollectionListChanged(val collections: List<String>) : ObsEvent

    // ── Studio mode (M6) ────────────────────────────────────────────────────
    data class StudioModeStateChanged(val enabled: Boolean) : ObsEvent
}
