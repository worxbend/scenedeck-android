package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history_sessions", indices = [Index(value = ["sessionId"], unique = true)])
data class HistorySessionEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val sessionId: String,
    val ownerId: String,
    val generation: Long,
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val startedAtMs: Long,
    val lastObservedAtMs: Long,
    val elapsedMs: Long = 0,
    val endedAtMs: Long? = null,
    val endReason: String? = null,
    val lastOrdinal: Long = 0,
    val omittedEvents: Long = 0,
    val missingEvents: Long = 0,
)

data class HistoryDetailEntity(
    @Embedded val session: HistorySessionEntity,
    @Relation(parentColumn = "sessionId", entityColumn = "sessionId")
    val events: List<HistoryEventEntity>,
)

@Dao
interface HistorySessionDao {
    @Query("SELECT * FROM history_sessions ORDER BY sequence DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<HistorySessionEntity>>

    @Query("SELECT * FROM history_sessions ORDER BY sequence DESC LIMIT :limit")
    suspend fun list(limit: Int): List<HistorySessionEntity>

    @Query("SELECT * FROM history_sessions WHERE sessionId = :id")
    suspend fun get(id: String): HistorySessionEntity?

    @Transaction
    @Query("SELECT * FROM history_sessions WHERE sessionId = :id")
    fun detail(id: String): Flow<HistoryDetailEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: HistorySessionEntity): Long

    @Update suspend fun update(session: HistorySessionEntity)

    @Query("DELETE FROM history_sessions WHERE :id IS NULL OR sessionId = :id")
    suspend fun delete(id: String?)

    @Query(
        "DELETE FROM history_sessions WHERE sequence NOT IN " +
            "(SELECT sequence FROM history_sessions ORDER BY sequence DESC LIMIT :limit)"
    )
    suspend fun prune(limit: Int)

    @Query(
        "UPDATE history_sessions SET endReason = :reason " +
            "WHERE endReason IS NULL AND (:exceptOwner IS NULL OR ownerId != :exceptOwner)"
    )
    suspend fun closeOpen(reason: String, exceptOwner: String? = null)
}
