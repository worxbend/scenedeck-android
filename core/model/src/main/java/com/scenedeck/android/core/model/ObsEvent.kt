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

    data class InputAudioBalanceChanged(val inputName: String, val balance: Double) : ObsEvent

    data class InputAudioSyncOffsetChanged(val inputName: String, val offsetMs: Int) : ObsEvent

    data class InputAudioMonitorTypeChanged(val inputName: String, val monitorType: MonitorTypeKind) : ObsEvent

    // ── Media inputs ────────────────────────────────────────────────────────
    data class MediaInputPlaybackStarted(val inputName: String) : ObsEvent

    data class MediaInputPlaybackEnded(val inputName: String) : ObsEvent

    // ── Outputs ─────────────────────────────────────────────────────────────
    /** [state] is the raw OBS state (e.g. `OBS_WEBSOCKET_OUTPUT_STARTED`). */
    data class StreamStateChanged(val active: Boolean, val state: String) : ObsEvent

    data class VirtualcamStateChanged(val active: Boolean, val state: String) : ObsEvent

    data class ReplayBufferStateChanged(val active: Boolean, val state: String) : ObsEvent

    data class ReplayBufferSaved(val outputPath: String) : ObsEvent

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

    // ── Studio mode & transitions (M6) ──────────────────────────────────────
    data class StudioModeStateChanged(val enabled: Boolean) : ObsEvent

    data class CurrentPreviewSceneChanged(val sceneName: String) : ObsEvent

    data class SceneTransitionStarted(val transitionName: String) : ObsEvent

    data class SceneTransitionEnded(val transitionName: String) : ObsEvent

    data class CurrentSceneTransitionChanged(val transitionName: String) : ObsEvent

    data class CurrentSceneTransitionDurationChanged(val durationMs: Int) : ObsEvent
}
