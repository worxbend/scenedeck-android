package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks", indices = [Index(value = ["bookmarkId"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val bookmarkId: String,
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val observationId: String,
    val createdAtMs: Long,
    val label: String,
    val times: String,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sequence DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookmarkId = :id")
    suspend fun get(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query(
        "DELETE FROM bookmarks WHERE sequence NOT IN " +
            "(SELECT sequence FROM bookmarks ORDER BY sequence DESC LIMIT :limit)"
    )
    suspend fun prune(limit: Int)

    @Query("UPDATE bookmarks SET label = :label WHERE bookmarkId = :id AND label = :original")
    suspend fun rename(id: String, original: String, label: String): Int

    @Query("DELETE FROM bookmarks WHERE bookmarkId = :id") suspend fun delete(id: String)
}
