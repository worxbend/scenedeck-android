package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {

    @Query("SELECT * FROM connection_profiles ORDER BY lastUsedAt IS NULL, lastUsedAt DESC, createdAt ASC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun byId(id: Long): ConnectionProfileEntity?

    @Query("SELECT * FROM connection_profiles WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun lastUsed(): ConnectionProfileEntity?

    @Insert
    suspend fun insert(entity: ConnectionProfileEntity): Long

    @Update
    suspend fun update(entity: ConnectionProfileEntity)

    @Query("UPDATE connection_profiles SET lastUsedAt = :usedAt WHERE id = :id")
    suspend fun markUsed(id: Long, usedAt: Long)

    @Delete
    suspend fun delete(entity: ConnectionProfileEntity)

    @Query("DELETE FROM connection_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
