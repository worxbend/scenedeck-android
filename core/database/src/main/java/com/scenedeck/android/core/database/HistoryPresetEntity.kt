package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "history_preset_runs",
    indices = [Index("sessionId")],
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
data class HistoryPresetEntity(
    @PrimaryKey val runId: String,
    val sessionId: String,
    val processId: String,
    val elapsedMs: Long,
    val revision: Long,
    val running: Boolean,
    val payload: String,
)

@Dao
interface HistoryPresetDao {
    @Query(
        "SELECT * FROM history_preset_runs WHERE sessionId = :id ORDER BY elapsedMs, runId LIMIT :limit"
    )
    fun observe(id: String, limit: Int): Flow<List<HistoryPresetEntity>>

    @Query(
        "SELECT * FROM history_preset_runs WHERE sessionId = :id ORDER BY elapsedMs, runId LIMIT :limit"
    )
    suspend fun list(id: String, limit: Int): List<HistoryPresetEntity>

    @Query("SELECT * FROM history_preset_runs WHERE runId = :id")
    suspend fun get(id: String): HistoryPresetEntity?

    @Query(
        "SELECT * FROM history_preset_runs WHERE running = 1 AND processId != :processId LIMIT :limit"
    )
    suspend fun interrupted(processId: String, limit: Int): List<HistoryPresetEntity>

    @Insert suspend fun insert(run: HistoryPresetEntity)

    @Update suspend fun update(run: HistoryPresetEntity)
}
