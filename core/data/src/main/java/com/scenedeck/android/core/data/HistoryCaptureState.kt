package com.scenedeck.android.core.data

enum class HistoryCapturePhase {
    STARTING,
    WAITING,
    CAPTURING,
    DISABLED,
    FAILED,
}

enum class HistoryCaptureFailure {
    STORAGE,
    OVERFLOW,
    SOURCE_ENDED,
}

data class HistoryCaptureState(
    val phase: HistoryCapturePhase = HistoryCapturePhase.STARTING,
    val sessionId: String? = null,
    val workspace: WorkspaceKey? = null,
    val missedEvents: Long = 0,
    val failure: HistoryCaptureFailure? = null,
)

data class HistoryBookmarkLink(val sessionId: String, val elapsedMs: Long)

fun interface HistoryBookmarkOrigin {
    fun capture(origin: WorkspaceSession): HistoryBookmarkLink?
}
