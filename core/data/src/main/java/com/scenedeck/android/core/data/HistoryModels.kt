package com.scenedeck.android.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MAX_HISTORY_SESSIONS = 100
const val MAX_HISTORY_EVENTS = 500

data class HistoryPreferences(
    val enabled: Boolean = true,
    val sessionLimit: Int = 30,
    val generation: Long = 0,
)

/** Captured before enqueueing a start. A deletion or capture-setting change invalidates it. */
data class HistorySeed(
    val id: String,
    val ownerId: String,
    val generation: Long,
    val workspace: WorkspaceKey,
    val startedAtMs: Long,
)

/** One continuous local observation, not an OBS stream or recording run. */
data class HistorySession(
    val id: String,
    val workspace: WorkspaceKey,
    val startedAtMs: Long,
    val lastObservedAtMs: Long,
    val elapsedMs: Long,
    val endedAtMs: Long?,
    val endReason: HistoryEndReason?,
    val lastOrdinal: Long,
    val omittedEvents: Long,
    val missingEvents: Long,
)

@Serializable
enum class HistoryEndReason {
    DISCONNECTED,
    WORKSPACE_CHANGED,
    CAPTURE_DISABLED,
    PROCESS_ENDED,
    STORAGE_FAILURE,
    CAPTURE_OVERFLOW,
    CAPTURE_INTERRUPTED,
}

@Serializable
enum class HistoryOutput {
    STREAM,
    RECORDING,
    VIRTUAL_CAMERA,
}

@Serializable
enum class HistoryOutputState {
    UNKNOWN,
    INACTIVE,
    ACTIVE,
    PAUSED,
    RECONNECTING,
}

@Serializable
enum class HistoryWarning {
    CONNECTION,
    DROPPED_FRAMES,
    RENDER_LOAD,
    DISK_SPACE,
    MICROPHONE,
}

@Serializable
enum class HistoryWarningPhase {
    SHOWN,
    UNCERTAIN,
    CLEARED_OR_DISMISSED,
}

@Serializable
enum class HistoryGapReason {
    CONNECTION_UNAVAILABLE,
    WORKSPACE_UNAVAILABLE,
    OBSERVATION_INTERRUPTED,
}

/** Allow-list only. There is deliberately no arbitrary error, protocol JSON, or path payload. */
@Serializable
sealed interface HistoryPayload {
    @Serializable
    @SerialName("output")
    data class Output(
        val output: HistoryOutput,
        val state: HistoryOutputState,
        val initial: Boolean,
        val durationMs: Long? = null,
    ) : HistoryPayload

    @Serializable
    @SerialName("program")
    data class Program(val sceneName: String?, val initial: Boolean) : HistoryPayload

    @Serializable
    @SerialName("warning")
    data class Warning(
        val warning: HistoryWarning,
        val phase: HistoryWarningPhase,
        val occurrence: Long,
    ) : HistoryPayload

    @Serializable @SerialName("gap") data class Gap(val reason: HistoryGapReason) : HistoryPayload
}

data class HistoryAppend(
    val sessionId: String,
    val ownerId: String,
    val ordinal: Long,
    val observedAtMs: Long,
    val elapsedMs: Long,
    val payload: HistoryPayload,
)

@Serializable
data class HistoryEvent(
    val ordinal: Long,
    val observedAtMs: Long,
    val elapsedMs: Long,
    val missingBefore: Long,
    val payload: HistoryPayload,
)

enum class HistoryAppendResult {
    ADDED,
    ALREADY_SAVED,
    OBSOLETE,
    UNAVAILABLE,
}

data class HistoryLinkedBookmark(val bookmark: LocalBookmark, val elapsedMs: Long)

data class HistoryDetail(
    val session: HistorySession,
    val events: List<HistoryEvent>,
    val bookmarks: List<HistoryLinkedBookmark> = emptyList(),
    val presetRuns: List<HistoryPresetRun> = emptyList(),
)

data class HistorySnapshot(
    val preferences: HistoryPreferences,
    val observations: List<HistoryDetail>,
    val unlinkedBookmarks: List<LocalBookmark> = emptyList(),
)

sealed interface HistoryState {
    data object Loading : HistoryState

    data object Failed : HistoryState

    data class Ready(val preferences: HistoryPreferences, val sessions: List<HistorySession>) :
        HistoryState
}

sealed interface HistoryDetailState {
    data object Loading : HistoryDetailState

    data object Missing : HistoryDetailState

    data object Failed : HistoryDetailState

    data class Ready(val detail: HistoryDetail) : HistoryDetailState
}

class HistoryStorageException : IllegalStateException("History is unavailable or has changed")
