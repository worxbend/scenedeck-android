package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegistryRepositoryTest {

    private lateinit var database: SceneDeckDatabase
    private lateinit var repository: RegistryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SceneDeckDatabase::class.java,
        ).build()
        repository = RegistryRepository(database.sceneRegistryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updateUpsertsAndObserves(): Unit = runBlocking {
        repository.entries.test {
            assertEquals(0, awaitItem().size)

            repository.update("Cam 1", SceneRole.PRIMARY, 0xFF7E57C2, "CAMERA")
            val entry = awaitItem().single()
            assertEquals("Cam 1", entry.sceneName)
            assertEquals(SceneRole.PRIMARY, entry.role)
            assertEquals(0xFF7E57C2, entry.accentColorArgb)
            assertEquals("CAMERA", entry.iconName)

            // Upsert again (role change) — no duplicate.
            repository.update("Cam 1", SceneRole.SECONDARY, null, null)
            val updated = awaitItem().single()
            assertEquals(SceneRole.SECONDARY, updated.role)
            assertNull(updated.accentColorArgb)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reorderAssignsSequentialSortOrder(): Unit = runBlocking {
        repository.reorder(listOf("B", "A", "C"))

        assertEquals(0, repository.byName("B")?.sortOrder)
        assertEquals(1, repository.byName("A")?.sortOrder)
        assertEquals(2, repository.byName("C")?.sortOrder)
    }

    @Test
    fun reorderPreservesMetadataOfExistingEntries(): Unit = runBlocking {
        repository.update("Cam 1", SceneRole.PRIMARY, 0xFF000000, "CAMERA")

        repository.reorder(listOf("Cam 1"))

        val entry = repository.byName("Cam 1")!!
        assertEquals(0, entry.sortOrder)
        assertEquals(0xFF000000, entry.accentColorArgb)
        assertEquals("CAMERA", entry.iconName)
    }

    @Test
    fun deleteStaleRemovesOnlyGoneScenes(): Unit = runBlocking {
        repository.update("Scene", SceneRole.PRIMARY, null, null)
        repository.update("Ghost", SceneRole.PRIMARY, null, null)

        repository.deleteStale(listOf("Scene"))

        assertEquals("Scene", repository.byName("Scene")?.sceneName)
        assertNull(repository.byName("Ghost"))
    }

    @Test
    fun deleteStaleWithEmptyListIsANoOp(): Unit = runBlocking {
        repository.update("Scene", SceneRole.PRIMARY, null, null)
        repository.deleteStale(emptyList())
        assertEquals("Scene", repository.byName("Scene")?.sceneName)
    }
}
