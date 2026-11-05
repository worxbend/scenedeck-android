package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.HistoryEventEntity
import com.scenedeck.android.core.database.HistoryPreferencesEntity
import com.scenedeck.android.core.database.HistorySessionEntity
import com.scenedeck.android.core.database.SceneDeckDatabase

/** Database transactions serialize retention, deletion, settings and capture writes. */
internal class HistoryWriter(private val database: SceneDeckDatabase) {
    private val sessions = database.historySessionDao()
    private val events = database.historyEventDao()
    private val preferences = database.historyPreferencesDao()

    suspend fun start(seed: HistorySeed): Boolean {
        val row = seed.encodeHistory()
        return database.withTransaction {
            val settings = (preferences.get() ?: HistoryPreferencesEntity()).decodeHistory()
            val existing = sessions.get(seed.id)
            if (existing != null) {
                checkHistory(existing.sameSeed(row))
                return@withTransaction settings.enabled && existing.endReason == null
            }
            if (!settings.enabled || settings.generation != seed.generation)
                return@withTransaction false
            sessions.insert(row)
            sessions.prune(settings.sessionLimit)
            true
        }
    }

    suspend fun append(write: HistoryAppend): HistoryAppendResult {
        checkHistory(write.ordinal in 1 until Long.MAX_VALUE && write.elapsedMs >= 0)
        val encoded = write.payload.encodeHistory()
        return database.withTransaction {
            val current =
                sessions.get(write.sessionId)
                    ?: return@withTransaction HistoryAppendResult.UNAVAILABLE
            current.decodeHistory()
            checkHistory(current.ownerId == write.ownerId)
            // A retained replay must match exactly; a pruned replay must never be reinserted.
            if (write.ordinal <= current.lastOrdinal) {
                val saved = events.get(write.sessionId, write.ordinal)
                checkHistory(saved == null || saved.sameWrite(write, encoded))
                return@withTransaction if (saved != null) HistoryAppendResult.ALREADY_SAVED
                else HistoryAppendResult.OBSOLETE
            }
            if (current.endReason != null) return@withTransaction HistoryAppendResult.UNAVAILABLE
            checkHistory(write.elapsedMs >= current.elapsedMs)
            val missing = write.ordinal - current.lastOrdinal - 1
            events.insert(
                HistoryEventEntity(
                    write.sessionId,
                    write.ordinal,
                    write.observedAtMs,
                    write.elapsedMs,
                    missing,
                    encoded,
                )
            )
            val omitted = events.prune(write.sessionId, MAX_HISTORY_EVENTS)
            sessions.update(
                current.copy(
                    lastObservedAtMs = write.observedAtMs,
                    elapsedMs = write.elapsedMs,
                    lastOrdinal = write.ordinal,
                    missingEvents = current.missingEvents + missing,
                    omittedEvents = current.omittedEvents + omitted,
                )
            )
            HistoryAppendResult.ADDED
        }
    }

    suspend fun touch(position: HistoryPosition, reason: HistoryEndReason? = null): Boolean =
        database.withTransaction {
            val current = sessions.get(position.sessionId) ?: return@withTransaction false
            current.decodeHistory()
            checkHistory(current.ownerId == position.ownerId)
            if (current.endReason != null) return@withTransaction false
            checkHistory(position.elapsedMs >= current.elapsedMs)
            sessions.update(
                current.copy(
                    lastObservedAtMs = position.observedAtMs,
                    elapsedMs = position.elapsedMs,
                    endedAtMs = position.observedAtMs.takeIf { reason != null },
                    endReason = reason?.name,
                )
            )
            true
        }
}

private fun HistorySessionEntity.sameSeed(other: HistorySessionEntity): Boolean =
    ownerId == other.ownerId &&
        generation == other.generation &&
        connectionProfileId == other.connectionProfileId &&
        sceneCollectionName == other.sceneCollectionName &&
        startedAtMs == other.startedAtMs

private fun HistoryEventEntity.sameWrite(write: HistoryAppend, encoded: String): Boolean =
    observedAtMs == write.observedAtMs && elapsedMs == write.elapsedMs && payload == encoded
