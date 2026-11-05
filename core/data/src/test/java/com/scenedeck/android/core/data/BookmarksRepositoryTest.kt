package com.scenedeck.android.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.BookmarkEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.RecordStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookmarksRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: SceneDeckDatabase
    private val workspaces = MutableWorkspaceContext()
    private var recordReads = 0

    @Before
    fun setup() {
        context.deleteDatabase("bookmarks-test.db")
        database =
            Room.databaseBuilder(context, SceneDeckDatabase::class.java, "bookmarks-test.db")
                .build()
    }

    @After
    fun teardown() {
        database.close()
        context.deleteDatabase("bookmarks-test.db")
    }

    @Test
    fun captureNeedsNoCapabilityOrExtraRequestAndSavesOriginalWorkspaceAfterDisconnect() = runTest {
        val repository = repository()
        val before = recordReads
        assertEquals(1, before)
        val captured = repository.capture(TEST_WORKSPACE)
        assertEquals(before, recordReads)
        workspaces.unavailable()
        repository.save(captured)
        assertEquals(listOf(captured), repository.saved())
        assertEquals(BookmarkOutputState.PAUSED, captured.times.recording.state)
        assertEquals(20_000L, captured.times.recording.durationMs)
        assertEquals(1_000_000L, captured.createdAtMs)
    }

    @Test
    fun retryIsIdempotentAndCannotOverwriteLaterLabelEdit() = runTest {
        val repository = repository()
        val captured = repository.capture(TEST_WORKSPACE)
        repository.save(captured)
        repository.save(captured)
        assertEquals(1, repository.saved().size)
        repository.rename(captured, "  Interview  ")
        assertTrue(
            runCatching { repository.save(captured) }.exceptionOrNull() is BookmarkEditException
        )
        assertEquals("Interview", repository.saved().single().label)
    }

    @Test
    fun staleRenameCannotRestoreOldLabelAndDeleteWorksOffline() = runTest {
        val repository = repository()
        val captured = repository.capture(TEST_WORKSPACE)
        repository.save(captured)
        repository.rename(captured, "First")
        assertTrue(runCatching { repository.rename(captured, "Second") }.isFailure)
        workspaces.unavailable()
        val saved = repository.saved().single()
        repository.delete(saved)
        assertTrue(repository.saved().isEmpty())
    }

    @Test
    fun sameNamesInDifferentScopesStaySeparateAndOldCaptureCannotRetarget() = runTest {
        val repository = repository()
        val first = repository.capture(TEST_WORKSPACE)
        val next = TEST_WORKSPACE.copy(key = WorkspaceKey(8, TEST_WORKSPACE.key.collectionName))
        workspaces.select(next)
        assertTrue(runCatching { repository.capture(TEST_WORKSPACE) }.isFailure)
        val second = repository.capture(next)
        repository.save(first)
        repository.save(second)
        assertEquals(listOf(next.key, TEST_WORKSPACE.key), repository.saved().map { it.workspace })
        assertNotEquals(first.observationId, second.observationId)
    }

    @Test
    fun observationsChangeAfterReconnectAndRepositoryRecreation() = runTest {
        val repository = repository()
        val first = repository.capture(TEST_WORKSPACE)
        assertEquals(first.observationId, repository.capture(TEST_WORKSPACE).observationId)
        val next = TEST_WORKSPACE.copy(token = ObsSessionToken(9, 0))
        workspaces.select(next)
        val reconnected = repository.capture(next)
        assertNotEquals(first.observationId, reconnected.observationId)
        assertNotEquals(reconnected.observationId, repository().capture(next).observationId)
    }

    @Test
    fun retentionKeepsNewestInsertionsEvenWhenDeviceClockMovesBackward() = runTest {
        val repository = repository()
        val captured = repository.capture(TEST_WORKSPACE)
        repeat(MAX_BOOKMARKS + 1) { index ->
            repository.save(captured.copy(id = "mark-$index", createdAtMs = 1_000_000L - index))
        }
        val saved = repository.saved()
        assertEquals(MAX_BOOKMARKS, saved.size)
        assertEquals("mark-500", saved.first().id)
        assertEquals("mark-1", saved.last().id)
        repository.save(saved.first())
        assertEquals(saved, repository.saved())
    }

    @Test
    fun savedBookmarksSurviveDatabaseAndRepositoryRecreation() = runTest {
        val repository = repository()
        val bookmark = repository.capture(TEST_WORKSPACE)
        repository.save(bookmark)
        database.close()
        database =
            Room.databaseBuilder(context, SceneDeckDatabase::class.java, "bookmarks-test.db")
                .build()
        workspaces.unavailable()
        assertEquals(listOf(bookmark), repository().saved())
    }

    @Test
    fun corruptOrUnsupportedSavedPayloadIsFailureNotAnEmptySuccess() = runTest {
        val repository = repository()
        listOf("{bad", "{\"version\":2}").forEach { payload ->
            database
                .bookmarkDao()
                .insert(
                    BookmarkEntity(
                        bookmarkId = "bad",
                        connectionProfileId = 7,
                        sceneCollectionName = "Studio",
                        observationId = "observation",
                        createdAtMs = 0,
                        label = "Bookmark",
                        times = payload,
                    )
                )
            assertEquals(
                BookmarksState.Failed,
                repository.state.first { it !is BookmarksState.Loading },
            )
            database.bookmarkDao().delete("bad")
            repository.retry()
        }
        assertTrue(repository.saved().isEmpty())
    }

    @Test
    fun closedDatabaseSaveFailsWithoutLosingCapturedMoment() = runTest {
        val repository = repository()
        val captured = repository.capture(TEST_WORKSPACE)
        database.close()
        assertTrue(runCatching { repository.save(captured) }.isFailure)
        database =
            Room.databaseBuilder(context, SceneDeckDatabase::class.java, "bookmarks-test.db")
                .build()
        repository().save(captured)
        assertEquals(listOf(captured), repository().saved())
    }

    private fun TestScope.repository(): BookmarksRepository {
        val client =
            object : FakeObsClient(recordStatusResponse = RecordStatus(true, true, "", 20_000, 0)) {
                override suspend fun getRecordStatus(): RecordStatus {
                    recordReads++
                    return super.getRecordStatus()
                }
            }
        client.setReady()
        val clock = MonotonicClock { testScheduler.currentTime }
        val stats = StatsRepository(client, backgroundScope, clock)
        runCurrent()
        return BookmarksRepository(database, workspaces, stats, clock, WallClock { 1_000_000 })
    }

    private suspend fun BookmarksRepository.saved(): List<LocalBookmark> =
        (state.first { it is BookmarksState.Ready } as BookmarksState.Ready).bookmarks
}
