package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history_preferences")
data class HistoryPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean = true,
    val sessionLimit: Int = 30,
    val generation: Long = 0,
)

@Dao
interface HistoryPreferencesDao {
    @Query("SELECT * FROM history_preferences WHERE id = 0")
    fun observe(): Flow<HistoryPreferencesEntity?>

    @Query("SELECT * FROM history_preferences WHERE id = 0")
    suspend fun get(): HistoryPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preferences: HistoryPreferencesEntity)

    @Update suspend fun update(preferences: HistoryPreferencesEntity)
}
