package com.scenedeck.android.feature.history

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.BookmarksRepository
import com.scenedeck.android.core.data.BookmarksState
import com.scenedeck.android.core.data.MonotonicClock
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.WallClock
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    @Test
    fun failedSaveKeepsMomentAndDuplicateRetryWritesOnlyOneBookmark() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        try {
            var now = 1_000_000L
            val workspaces = MutableWorkspaceContext()
            val clock = MonotonicClock { testScheduler.currentTime }
            val client = ObsClient(backgroundScope)
            val stats = StatsRepository(client, backgroundScope, clock)
            val repository =
                BookmarksRepository(database, workspaces, stats, clock, WallClock { now })
            val model =
                BookmarksViewModel(repository, ProfileRepository(database.connectionProfileDao()))
            store.put("bookmarks", model)
            backgroundScope.launch { model.uiState.collect() }
            model.uiState.first { it.saved is BookmarksState.Ready }
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_bookmark BEFORE INSERT ON bookmarks " +
                    "BEGIN SELECT RAISE(ABORT, 'private storage diagnostic'); END"
            )
            model.mark(TEST_WORKSPACE)
            model.mark(TEST_WORKSPACE)
            val failed =
                model.uiState.first { !it.operation.saving && it.operation.unsaved != null }
            assertNotNull(failed.operation.message)
            assertFalse(failed.operation.message!!.contains("private storage"))
            val captured = failed.operation.unsaved!!
            workspaces.unavailable()
            now = 2_000_000L
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_bookmark")
            model.retrySave()
            model.retrySave()
            val saved =
                repository.state.first { it is BookmarksState.Ready && it.bookmarks.isNotEmpty() }
                    as BookmarksState.Ready
            assertEquals(listOf(captured), saved.bookmarks)
            assertEquals(1_000_000L, saved.bookmarks.single().createdAtMs)
        } finally {
            store.clear()
            database.close()
            Dispatchers.resetMain()
        }
    }
}
