package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.HistoryDetailEntity
import com.scenedeck.android.core.database.HistoryPreferencesEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
                sessions.detail(id.checkedHistoryId()).collect { row ->
                    emit(
                        row?.let { HistoryDetailState.Ready(it.decodeHistory()) }
                            ?: HistoryDetailState.Missing
                    )
                }
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
        val details = selected.map { session ->
            HistoryDetailEntity(
                    session,
                    database.historyEventDao().list(session.sessionId, MAX_HISTORY_EVENTS + 1),
                )
                .decodeHistory()
        }
        HistorySnapshot(preferences, details)
    }
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
