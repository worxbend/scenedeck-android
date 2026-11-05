package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.HistoryDetailEntity
import com.scenedeck.android.core.database.HistoryPreferencesEntity
import com.scenedeck.android.core.database.HistorySessionEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryReader(private val database: SceneDeckDatabase, retries: Flow<Int>) {
    private val sessions = database.historySessionDao()

    val state: Flow<HistoryState> = retries.flatMapLatest {
        flow<HistoryState> {
                emit(HistoryState.Loading)
                emitAll(
                    database.historyPreferencesDao().observe().flatMapLatest { row ->
                        val preferences = (row ?: HistoryPreferencesEntity()).decodeHistory()
                        sessions.observe(preferences.sessionLimit).map { rows ->
                            HistoryState.Ready(preferences, rows.map { it.decodeHistory() })
                        }
                    }
                )
            }
            .catch {
                if (it is CancellationException) throw it
                emit(HistoryState.Failed)
            }
    }

    fun detail(id: String): Flow<HistoryDetailState> =
        flow<HistoryDetailState> {
                emit(HistoryDetailState.Loading)
                id.checkedHistoryId()
                emitAll(
                    combine(
                        sessions.detail(id),
                        database.historyBookmarkDao().observe(id, MAX_BOOKMARKS + 1),
                    ) { _, _ ->
                        database.withTransaction {
                            val row = sessions.get(id)
                            if (row == null) HistoryDetailState.Missing
                            else HistoryDetailState.Ready(readDetail(row))
                        }
                    }
                )
            }
            .catch {
                if (it is CancellationException) throw it
                emit(HistoryDetailState.Failed)
            }

    suspend fun snapshot(id: String?): HistorySnapshot = database.withTransaction {
        val preferences =
            (database.historyPreferencesDao().get() ?: HistoryPreferencesEntity()).decodeHistory()
        val selected =
            if (id == null) sessions.list(preferences.sessionLimit)
            else listOf(sessions.get(id.checkedHistoryId()) ?: throw HistoryStorageException())
        val details = selected.map { readDetail(it) }
        val unlinked =
            if (id == null) database.historyBookmarkDao().unlinked(MAX_BOOKMARKS + 1)
            else emptyList()
        checkHistory(unlinked.size <= MAX_BOOKMARKS)
        HistorySnapshot(preferences, details, unlinked.map { it.decodeBookmark() })
    }

    private suspend fun readDetail(row: HistorySessionEntity): HistoryDetail =
        HistoryDetailEntity(
                row,
                database.historyEventDao().list(row.sessionId, MAX_HISTORY_EVENTS + 1),
            )
            .decodeHistory()
            .withBookmarks(database.historyBookmarkDao().list(row.sessionId, MAX_BOOKMARKS + 1))
}

private fun HistoryDetailEntity.decodeHistory(): HistoryDetail {
    val decoded = session.decodeHistory()
    checkHistory(events.size <= MAX_HISTORY_EVENTS)
    checkHistory(
        decoded.lastOrdinal - decoded.missingEvents - decoded.omittedEvents == events.size.toLong()
    )
    val ordered = events.sortedBy { it.ordinal }.map { it.decodeHistory() }
    ordered.forEach {
        checkHistory(it.ordinal <= decoded.lastOrdinal && it.elapsedMs <= decoded.elapsedMs)
    }
    ordered.zipWithNext().forEach { (previous, next) ->
        checkHistory(
            next.ordinal - previous.ordinal - 1 == next.missingBefore &&
                next.elapsedMs >= previous.elapsedMs
        )
    }
    return HistoryDetail(decoded, ordered)
}
