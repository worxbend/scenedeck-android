package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBookmarksTest : HistoryTestFixture() {
    private val captured =
        LocalBookmark(
            "mark",
            SEED.workspace,
            "bookmark-observation",
            10_050,
            "Interview",
            BookmarkTimes(),
            HistoryBookmarkLink(SEED.id, 50),
        )

    @Test
    fun lateSaveLinksOnlyToTheOriginalClosedObservation() = runTest {
        val bookmarks = bookmarks()
        repository.start(SEED)
        repository.finish(
            HistoryPosition(SEED.id, SEED.ownerId, 10_100, 100),
            HistoryEndReason.DISCONNECTED,
        )
        repository.start(SEED.copy(id = "replacement", workspace = WorkspaceKey(8, "Studio")))
        bookmarks.save(captured)
        bookmarks.save(captured)
        val snapshot = repository.exportSnapshot()
        assertTrue(snapshot.observations.first().bookmarks.isEmpty())
        assertEquals(
            listOf(captured.id),
            snapshot.observations.last().bookmarks.map { it.bookmark.id },
        )
        assertTrue(snapshot.unlinkedBookmarks.isEmpty())
    }

    @Test
    fun deletedHistoryIsNotRecreatedAndIndependentBookmarkStillExports() = runTest {
        val bookmarks = bookmarks()
        repository.start(SEED)
        repository.delete()
        bookmarks.save(captured)
        val snapshot = repository.exportSnapshot()
        assertTrue(snapshot.observations.isEmpty())
        assertEquals(listOf(captured.copy(history = null)), snapshot.unlinkedBookmarks)
        assertTrue(snapshot.exportJson(20_000).contains("Interview"))
    }

    @Test
    fun currentLabelAndDeletionPropagateToHistoryAndExports() = runTest {
        val bookmarks = bookmarks()
        repository.start(SEED)
        bookmarks.save(captured)
        bookmarks.rename(captured, "New label")
        val renamed =
            repository.detail(SEED.id).first { it is HistoryDetailState.Ready }
                as HistoryDetailState.Ready
        assertEquals("New label", renamed.detail.bookmarks.single().bookmark.label)
        assertEquals(50L, renamed.detail.session.elapsedMs)
        assertFalse(repository.exportSnapshot().exportJson(20_000).contains("Interview"))
        bookmarks.delete(captured)
        assertTrue(repository.exportSnapshot().observations.single().bookmarks.isEmpty())
        assertFalse(repository.exportSnapshot().exportJson(20_000).contains("New label"))
    }

    @Test
    fun linkFailureRollsBackBookmarkAndRetryCreatesOneAssociation() = runTest {
        val bookmarks = bookmarks()
        repository.start(SEED)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_link BEFORE INSERT ON history_bookmarks " +
                "BEGIN SELECT RAISE(ABORT, 'private diagnostic'); END"
        )
        assertTrue(runCatching { bookmarks.save(captured) }.isFailure)
        assertNull(database.bookmarkDao().get(captured.id))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_link")
        bookmarks.save(captured)
        bookmarks.save(captured)
        assertEquals(1, repository.exportSnapshot().observations.single().bookmarks.size)
    }

    @Test
    fun bookmarkRetentionRemovesLinksAndHistoryDeletionPreservesBookmarks() = runTest {
        val bookmarks = bookmarks()
        repository.start(SEED)
        repeat(MAX_BOOKMARKS + 1) { bookmarks.save(captured.copy(id = "mark-$it")) }
        val detail = repository.exportSnapshot().observations.single()
        assertEquals(MAX_BOOKMARKS, detail.bookmarks.size)
        assertEquals("mark-1", detail.bookmarks.first().bookmark.id)
        repository.delete(SEED.id)
        assertEquals(MAX_BOOKMARKS, repository.exportSnapshot().unlinkedBookmarks.size)
    }

    @Test
    fun captureGetsLinkBeforeSuspensionAndCannotLinkAcrossWorkspaces() = runTest {
        repository.start(SEED)
        val bookmarks = bookmarks(HistoryBookmarkOrigin { captured.history })
        val mark = bookmarks.capture(TEST_WORKSPACE)
        assertEquals(captured.history, mark.history)
        assertTrue(
            runCatching { bookmarks.save(captured.copy(workspace = WorkspaceKey(8, "Other"))) }
                .isFailure
        )
        assertNull(database.bookmarkDao().get(captured.id))
    }

    private fun TestScope.bookmarks(
        origin: HistoryBookmarkOrigin = HistoryBookmarkOrigin { null }
    ): BookmarksRepository {
        val clock = MonotonicClock { 1_000 }
        val stats = StatsRepository(ObsClient(backgroundScope), backgroundScope, clock)
        return BookmarksRepository(
            database,
            MutableWorkspaceContext(),
            stats,
            clock,
            { 10_050 },
            origin,
        )
    }
}
