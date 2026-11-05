package com.scenedeck.android.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val historyExportJson = Json {
    encodeDefaults = true
    prettyPrint = true
}

@Serializable
private data class HistoryExport(
    val format: String = "scenedeck-history",
    val version: Int = 1,
    val exportedAtMs: Long,
    val retention: HistoryExportRetention,
    val observations: List<HistoryExportObservation>,
    val unlinkedBookmarks: List<HistoryExportBookmark>,
)

@Serializable
private data class HistoryExportRetention(
    val sessionLimit: Int,
    val eventsPerObservation: Int = MAX_HISTORY_EVENTS,
)

/**
 * An explicit export boundary: process ownership, DB sequence and deletion generation stay local.
 */
@Serializable
private data class HistoryExportObservation(
    val id: String,
    val profileId: Long,
    val collectionName: String,
    val startedAtMs: Long,
    val lastObservedAtMs: Long,
    val elapsedMs: Long,
    val endedAtMs: Long?,
    val endReason: HistoryEndReason?,
    val lastOrdinal: Long,
    val omittedEvents: Long,
    val missingEvents: Long,
    val events: List<HistoryEvent>,
    val bookmarks: List<HistoryExportBookmark>,
)

@Serializable
private data class HistoryExportBookmark(
    val id: String,
    val profileId: Long,
    val collectionName: String,
    val createdAtMs: Long,
    val label: String,
    val times: BookmarkTimes,
    val observationElapsedMs: Long?,
)

private fun LocalBookmark.exportBookmark(elapsedMs: Long?) =
    HistoryExportBookmark(
        id,
        workspace.profileId,
        workspace.collectionName,
        createdAtMs,
        label,
        times,
        elapsedMs,
    )

/**
 * JSON preserves unknown states and escapes names. The caller chooses where to write this snapshot.
 */
fun HistorySnapshot.exportJson(exportedAtMs: Long): String {
    checkHistory(observations.size <= MAX_HISTORY_SESSIONS)
    checkHistory(observations.sumOf { it.bookmarks.size } + unlinkedBookmarks.size <= MAX_BOOKMARKS)
    val exported = observations.map { detail ->
        checkHistory(detail.events.size <= MAX_HISTORY_EVENTS)
        val session = detail.session
        HistoryExportObservation(
            session.id,
            session.workspace.profileId,
            session.workspace.collectionName,
            session.startedAtMs,
            session.lastObservedAtMs,
            session.elapsedMs,
            session.endedAtMs,
            session.endReason,
            session.lastOrdinal,
            session.omittedEvents,
            session.missingEvents,
            detail.events,
            detail.bookmarks.map { it.bookmark.exportBookmark(it.elapsedMs) },
        )
    }
    return historyExportJson.encodeToString(
        HistoryExport(
            exportedAtMs = exportedAtMs,
            retention = HistoryExportRetention(preferences.sessionLimit),
            observations = exported,
            unlinkedBookmarks = unlinkedBookmarks.map { it.exportBookmark(null) },
        )
    )
}
