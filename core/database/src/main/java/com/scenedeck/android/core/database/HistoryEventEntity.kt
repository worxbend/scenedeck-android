package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.Query

@Entity(
    tableName = "history_events",
    primaryKeys = ["sessionId", "ordinal"],
    foreignKeys =
        [
            ForeignKey(
                entity = HistorySessionEntity::class,
                parentColumns = ["sessionId"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class HistoryEventEntity(
    val sessionId: String,
    val ordinal: Long,
    val observedAtMs: Long,
    val elapsedMs: Long,
    val missingBefore: Long,
    val payload: String,
)

@Dao
interface HistoryEventDao {
    @Query("SELECT * FROM history_events WHERE sessionId = :id ORDER BY ordinal LIMIT :limit")
    suspend fun list(id: String, limit: Int): List<HistoryEventEntity>

    @Query("SELECT * FROM history_events WHERE sessionId = :id AND ordinal = :ordinal")
    suspend fun get(id: String, ordinal: Long): HistoryEventEntity?

    @Insert suspend fun insert(event: HistoryEventEntity)

    @Query(
        "DELETE FROM history_events WHERE sessionId = :id AND ordinal NOT IN " +
            "(SELECT ordinal FROM history_events WHERE sessionId = :id ORDER BY ordinal DESC LIMIT :limit)"
    )
    suspend fun prune(id: String, limit: Int): Int
}
