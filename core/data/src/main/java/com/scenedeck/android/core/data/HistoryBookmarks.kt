package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.BookmarkEntity
import com.scenedeck.android.core.database.HistoryBookmarkEntity
import com.scenedeck.android.core.database.HistoryBookmarkRow
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.serialization.json.Json

private val bookmarkJson = Json { ignoreUnknownKeys = true }

/** Called inside the bookmark insert transaction. Deleting history never recreates its parent. */
internal suspend fun SceneDeckDatabase.linkHistoryBookmark(bookmark: LocalBookmark) {
    val link = bookmark.history ?: return
    checkHistory(link.elapsedMs >= 0)
    val parent = historySessionDao().get(link.sessionId) ?: return
    checkHistory(
        parent.connectionProfileId == bookmark.workspace.profileId &&
            parent.sceneCollectionName == bookmark.workspace.collectionName
    )
    if (parent.endedAtMs == null || link.elapsedMs <= parent.elapsedMs)
        historyBookmarkDao()
            .insert(HistoryBookmarkEntity(bookmark.id, link.sessionId, link.elapsedMs))
}

internal fun BookmarkEntity.decodeBookmark(): LocalBookmark {
    if (times.length > 4_096) throw BookmarkEditException()
    return LocalBookmark(
        bookmarkId,
        WorkspaceKey(connectionProfileId, sceneCollectionName),
        observationId,
        createdAtMs,
        checkedBookmarkLabel(label),
        bookmarkJson.decodeFromString<BookmarkTimes>(times).checked(),
    )
}

internal fun checkedBookmarkLabel(label: String): String =
    label
        .trim()
        .ifBlank { "Bookmark" }
        .also {
            if (it.length > MAX_BOOKMARK_LABEL_LENGTH) throw BookmarkEditException()
        }

internal fun HistoryBookmarkRow.decodeHistoryBookmark(
    session: HistorySession
): HistoryLinkedBookmark {
    checkHistory(elapsedMs >= 0)
    val decoded = bookmark.decodeBookmark()
    checkHistory(decoded.workspace == session.workspace)
    checkHistory(session.endedAtMs == null || elapsedMs <= session.elapsedMs)
    return HistoryLinkedBookmark(decoded, elapsedMs)
}

internal fun HistoryDetail.withBookmarks(rows: List<HistoryBookmarkRow>): HistoryDetail {
    checkHistory(rows.size <= MAX_BOOKMARKS)
    val linked = rows.map { it.decodeHistoryBookmark(session) }
    val latest = linked.maxByOrNull { it.elapsedMs }
    // A saved moment can be later than the last heartbeat when the process ends abruptly.
    val observed =
        if (latest != null && latest.elapsedMs > session.elapsedMs)
            session.copy(
                elapsedMs = latest.elapsedMs,
                lastObservedAtMs = latest.bookmark.createdAtMs,
            )
        else session
    return copy(session = observed, bookmarks = linked)
}
