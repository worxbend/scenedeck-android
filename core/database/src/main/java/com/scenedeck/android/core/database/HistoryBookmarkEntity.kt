package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "history_bookmarks",
    indices = [Index("sessionId")],
    foreignKeys =
        [
            ForeignKey(
                entity = BookmarkEntity::class,
                parentColumns = ["bookmarkId"],
                childColumns = ["bookmarkId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = HistorySessionEntity::class,
                parentColumns = ["sessionId"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class HistoryBookmarkEntity(
    @PrimaryKey val bookmarkId: String,
    val sessionId: String,
    val elapsedMs: Long,
)

data class HistoryBookmarkRow(@Embedded val bookmark: BookmarkEntity, val elapsedMs: Long)

private const val HISTORY_BOOKMARK_QUERY =
    "SELECT b.*, h.elapsedMs FROM bookmarks b " +
        "JOIN history_bookmarks h ON b.bookmarkId = h.bookmarkId WHERE h.sessionId = :id " +
        "ORDER BY h.elapsedMs, b.sequence LIMIT :limit"

@Dao
interface HistoryBookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(link: HistoryBookmarkEntity)

    @Query(HISTORY_BOOKMARK_QUERY)
    fun observe(id: String, limit: Int): Flow<List<HistoryBookmarkRow>>

    @Query(HISTORY_BOOKMARK_QUERY)
    suspend fun list(id: String, limit: Int): List<HistoryBookmarkRow>

    @Query(
        "SELECT * FROM bookmarks WHERE bookmarkId NOT IN " +
            "(SELECT bookmarkId FROM history_bookmarks) ORDER BY sequence DESC LIMIT :limit"
    )
    suspend fun unlinked(limit: Int): List<BookmarkEntity>
}
