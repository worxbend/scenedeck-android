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
)

/**
 * JSON preserves unknown states and escapes names. The caller chooses where to write this snapshot.
 */
fun HistorySnapshot.exportJson(exportedAtMs: Long): String {
    checkHistory(observations.size <= MAX_HISTORY_SESSIONS)
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
        )
    }
    return historyExportJson.encodeToString(
        HistoryExport(
            exportedAtMs = exportedAtMs,
            retention = HistoryExportRetention(preferences.sessionLimit),
            observations = exported,
        )
    )
}
