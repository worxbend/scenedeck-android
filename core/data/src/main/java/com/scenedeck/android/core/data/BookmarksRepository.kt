package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.BookmarkEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksRepository
@Inject
constructor(
    private val database: SceneDeckDatabase,
    val workspaces: WorkspaceContext,
    private val stats: StatsRepository,
    private val monotonicClock: MonotonicClock,
    private val wallClock: WallClock,
) {
    private val dao = database.bookmarkDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val retryVersion = MutableStateFlow(0)
    private var observation: Pair<WorkspaceSession, String>? = null

    /** Saved bookmarks remain readable after disconnect or deletion of their connection profile. */
    val state: Flow<BookmarksState> = retryVersion.flatMapLatest {
        flow<BookmarksState> {
                emit(BookmarksState.Loading)
                dao.observe(MAX_BOOKMARKS).collect { rows ->
                    emit(BookmarksState.Ready(rows.map(::decode)))
                }
            }
            .catch {
                if (it is CancellationException) throw it
                emit(BookmarksState.Failed)
            }
    }

    fun retry() {
        retryVersion.update { it + 1 }
        workspaces.retry()
    }

    /** Capture before any suspension or label editing; no OBS requests or writes are made. */
    @Synchronized
    fun capture(origin: WorkspaceSession): LocalBookmark {
        workspaces.requireCurrent(origin)
        val context =
            observation?.takeIf { it.first == origin }?.second ?: UUID.randomUUID().toString()
        observation = origin to context
        val times = bookmarkTimes(stats.telemetry.value, origin.token, monotonicClock.nowMs())
        val captured =
            LocalBookmark(
                UUID.randomUUID().toString(),
                origin.key,
                context,
                wallClock.nowMs(),
                "Bookmark",
                times,
            )
        workspaces.requireCurrent(origin)
        return captured
    }

    /** An immutable captured moment can finish saving after disconnect; it is never retargeted. */
    suspend fun save(bookmark: LocalBookmark) {
        val entity = encode(bookmark)
        database.withTransaction {
            val existing = dao.get(bookmark.id)
            if (existing != null && existing.copy(sequence = 0) != entity)
                throw BookmarkEditException()
            if (existing == null) dao.insert(entity)
            dao.prune(MAX_BOOKMARKS)
        }
    }

    suspend fun rename(bookmark: LocalBookmark, label: String) {
        val checked = checkedLabel(label)
        if (dao.rename(bookmark.id, bookmark.label, checked) != 1) throw BookmarkEditException()
    }

    suspend fun delete(bookmark: LocalBookmark) {
        dao.delete(bookmark.id)
    }

    private fun encode(bookmark: LocalBookmark): BookmarkEntity =
        BookmarkEntity(
            bookmarkId = bookmark.id,
            connectionProfileId = bookmark.workspace.profileId,
            sceneCollectionName = bookmark.workspace.collectionName,
            observationId = bookmark.observationId,
            createdAtMs = bookmark.createdAtMs,
            label = checkedLabel(bookmark.label),
            times = json.encodeToString(bookmark.times.checked()),
        )

    private fun decode(row: BookmarkEntity): LocalBookmark {
        if (row.times.length > 4_096) throw BookmarkEditException()
        val times = json.decodeFromString<BookmarkTimes>(row.times).checked()
        return LocalBookmark(
            row.bookmarkId,
            WorkspaceKey(row.connectionProfileId, row.sceneCollectionName),
            row.observationId,
            row.createdAtMs,
            checkedLabel(row.label),
            times,
        )
    }

    private fun checkedLabel(label: String): String =
        label
            .trim()
            .ifBlank { "Bookmark" }
            .also {
                if (it.length > MAX_BOOKMARK_LABEL_LENGTH) throw BookmarkEditException()
            }
}
