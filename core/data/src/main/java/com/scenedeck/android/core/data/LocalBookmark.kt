package com.scenedeck.android.core.data

import kotlinx.serialization.Serializable

const val MAX_BOOKMARKS = 500
const val MAX_BOOKMARK_LABEL_LENGTH = 120

@Serializable
enum class BookmarkOutputState {
    UNKNOWN,
    INACTIVE,
    ACTIVE,
    PAUSED,
    RECONNECTING,
}

/** Snapshot of an OBS counter, never extrapolated from device time. */
@Serializable
data class BookmarkOutputTime(
    val state: BookmarkOutputState = BookmarkOutputState.UNKNOWN,
    val durationMs: Long? = null,
    val sampleAgeMs: Long? = null,
)

@Serializable
data class BookmarkTimes(
    val stream: BookmarkOutputTime = BookmarkOutputTime(),
    val recording: BookmarkOutputTime = BookmarkOutputTime(),
    val version: Int = 1,
)

data class LocalBookmark(
    val id: String,
    val workspace: WorkspaceKey,
    val observationId: String,
    val createdAtMs: Long,
    val label: String,
    val times: BookmarkTimes,
    /** Pending capture link only; saved labels/times are resolved from their original row. */
    val history: HistoryBookmarkLink? = null,
)

sealed interface BookmarksState {
    data object Loading : BookmarksState

    data object Failed : BookmarksState

    data class Ready(val bookmarks: List<LocalBookmark>) : BookmarksState
}

class BookmarkEditException : IllegalArgumentException("Bookmark is invalid or has changed")
