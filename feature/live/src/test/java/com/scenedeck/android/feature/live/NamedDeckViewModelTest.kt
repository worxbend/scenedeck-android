package com.scenedeck.android.feature.live

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.NamedDeckRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NamedDeckViewModelTest {
    private lateinit var database: SceneDeckDatabase
    private lateinit var repository: NamedDeckRepository
    private lateinit var viewModel: NamedDeckViewModel
    private val context = MutableWorkspaceContext()
    private val store = ViewModelStore()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        repository = NamedDeckRepository(database, context)
        viewModel =
            NamedDeckViewModel(repository, ProfileRepository(database.connectionProfileDao()))
        store.put("decks", viewModel)
    }

    @After
    fun close() {
        store.clear()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun duplicateSaveIsGatedAndFailedRenameRetainsDraft(): Unit = runBlocking {
        viewModel.open(TEST_WORKSPACE)
        viewModel.show(DeckEditorPage.Name(null, "Interview"))
        viewModel.save()
        viewModel.save()
        withTimeout(5_000) { viewModel.busy.first { !it } }
        assertEquals(1, repository.snapshot(TEST_WORKSPACE).decks.size)
        repository.create(TEST_WORKSPACE, "Guest")
        val target = repository.snapshot(TEST_WORKSPACE).decks.first { it.name == "Interview" }
        viewModel.show(DeckEditorPage.Name(target, "Guest"))
        viewModel.save()
        withTimeout(5_000) { viewModel.busy.first { !it } }
        assertNotNull(viewModel.manager.value?.error)
        assertEquals("Guest", (viewModel.manager.value?.page as DeckEditorPage.Name).draft)
        assertEquals(
            listOf("Guest", "Interview"),
            repository.snapshot(TEST_WORKSPACE).decks.map { it.name },
        )
    }

    @Test
    fun workspaceReplacementClosesDraftWithoutSavingIt() {
        viewModel.open(TEST_WORKSPACE)
        viewModel.show(DeckEditorPage.Name(null, "Unsaved"))
        context.select(TEST_WORKSPACE.copy(targetGeneration = 2))
        assertNull(viewModel.manager.value)
    }
}
