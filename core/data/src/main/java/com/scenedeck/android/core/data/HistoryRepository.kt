package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.HistoryPreferencesEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

data class HistoryPosition(
    val sessionId: String,
    val ownerId: String,
    val observedAtMs: Long,
    val elapsedMs: Long,
)

/** Local persistence only. The shared-state recorder and user-facing History flow are separate. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepository @Inject constructor(private val database: SceneDeckDatabase) {
    private val retries = MutableStateFlow(0)
    private val reader = HistoryReader(database, retries)
    private val writer = HistoryWriter(database)
    private val preferences = database.historyPreferencesDao()
    private val sessions = database.historySessionDao()
    val state = reader.state

    fun retry() {
        retries.update { it + 1 }
    }

    suspend fun start(seed: HistorySeed): Boolean = writer.start(seed)

    suspend fun append(event: HistoryAppend): HistoryAppendResult = writer.append(event)

    /** Heartbeat updates the last observed point without generating another event. */
    suspend fun touch(position: HistoryPosition): Boolean = writer.touch(position)

    suspend fun finish(position: HistoryPosition, reason: HistoryEndReason): Boolean =
        writer.touch(position, reason)

    /** A prior process's open observation has an unknown end, never an inferred output stop. */
    suspend fun recover(
        ownerId: String,
        reason: HistoryEndReason = HistoryEndReason.PROCESS_ENDED,
    ) = database.withTransaction {
        sessions.closeOpen(reason.name, ownerId.checkedHistoryId())
    }

    suspend fun configure(enabled: Boolean, sessionLimit: Int) = database.withTransaction {
        val current = preferences.get() ?: HistoryPreferencesEntity()
        current.decodeHistory()
        val changed =
            current.copy(
                enabled = enabled,
                sessionLimit = sessionLimit,
                generation =
                    if (enabled == current.enabled) current.generation
                    else Math.addExact(current.generation, 1),
            )
        changed.decodeHistory()
        preferences.insert(current)
        preferences.update(changed)
        if (!enabled) sessions.closeOpen(HistoryEndReason.CAPTURE_DISABLED.name)
        sessions.prune(sessionLimit)
    }

    /**
     * Null clears all observations. Append never creates a parent; old queued starts are
     * invalidated.
     */
    suspend fun delete(id: String? = null) = database.withTransaction {
        id?.checkedHistoryId()
        val current = preferences.get() ?: HistoryPreferencesEntity()
        current.decodeHistory()
        preferences.insert(current)
        preferences.update(current.copy(generation = Math.addExact(current.generation, 1)))
        sessions.delete(id)
    }

    fun detail(id: String) = retries.flatMapLatest { reader.detail(id) }

    suspend fun exportSnapshot(id: String? = null): HistorySnapshot = reader.snapshot(id)
}
