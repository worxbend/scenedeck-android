package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
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

    @Test
    fun interleavedMutationsCannotClobberEachOther(): Unit = runBlocking {
        val gated = GatedRegistryDao(database.sceneRegistryDao())
        val repo = RegistryRepository(gated)
        repo.update("A", SceneRole.PRIMARY, null, null)

        // Hold reorder() after its snapshot read, then queue update() behind it.
        // Without serialization the update would land and then be overwritten by
        // reorder()'s stale read.
        gated.gateNextSnapshot = true
        val reorder = async { repo.reorder(listOf("A")) }
        gated.snapshotEntered.await()
        val update = async { repo.update("A", SceneRole.SECONDARY, null, null) }
        gated.releaseSnapshot.complete(Unit)
        reorder.await()
        update.await()

        assertEquals(SceneRole.SECONDARY, repo.byName("A")?.role)
        assertEquals(0, repo.byName("A")?.sortOrder)
    }

    @Test
    fun laterDeletesCannotBeResurrectedByReorder(): Unit = runBlocking {
        for (staleCleanup in listOf(false, true)) {
            val gated = GatedRegistryDao(database.sceneRegistryDao())
            val repo = RegistryRepository(gated)
            repo.update("A", SceneRole.PRIMARY, null, null)
            repo.update("Keep", SceneRole.SECONDARY, null, null)
            gated.gateNextSnapshot = true
            val reorder = async { repo.reorder(listOf("A", "Keep")) }
            gated.snapshotEntered.await()
            val deletion = async {
                if (staleCleanup) repo.deleteStale(listOf("Keep")) else repo.remove("A")
            }
            yield()
            gated.releaseSnapshot.complete(Unit)
            reorder.await()
            deletion.await()
            assertNull(repo.byName("A"))
            assertEquals(SceneRole.SECONDARY, repo.byName("Keep")?.role)
        }
    }

    @Test
    fun bulkMutationsUseOneSnapshotAndOneWriteForLargeCollections(): Unit = runBlocking {
        val counted = GatedRegistryDao(database.sceneRegistryDao())
        val repo = RegistryRepository(counted)
        val names = (0 until 1000).map { "Scene$it" }
        repo.reorder(names)
        assertEquals(1, counted.snapshotReads)
        assertEquals(0, counted.byNameReads)
        assertEquals(1, counted.bulkWrites)
        repo.assignRoleToUnassigned(names + "New", SceneRole.SECONDARY)
        assertEquals(2, counted.snapshotReads)
        assertEquals(0, counted.byNameReads)
        assertEquals(2, counted.bulkWrites)
        repo.importMerge(names.map { SceneRegistryEntry(it, SceneRole.ARCHIVE) })
        assertEquals(3, counted.snapshotReads)
        assertEquals(0, counted.byNameReads)
        assertEquals(3, counted.bulkWrites)
    }

    @Test
    fun duplicateImportsPreserveSequentialCountsNullMergesAndExistingOrder(): Unit = runBlocking {
        repository.update("Existing", SceneRole.PRIMARY, 123L, "CAMERA")
        repository.reorder(listOf("Existing"))
        val counts =
            repository.importMerge(
                listOf(
                    SceneRegistryEntry("New", SceneRole.SECONDARY, 456L, "MIC", 7),
                    SceneRegistryEntry("New", SceneRole.MODULE, null, null, 99),
                    SceneRegistryEntry("Existing", SceneRole.ARCHIVE, null, null, 50),
                )
            )
        assertEquals(1 to 2, counts)
        assertEquals(
            SceneRegistryEntry("New", SceneRole.MODULE, 456L, "MIC", 7),
            repository.byName("New"),
        )
        assertEquals(
            SceneRegistryEntry("Existing", SceneRole.ARCHIVE, 123L, "CAMERA", 0),
            repository.byName("Existing"),
        )
    }

    @Test
    fun bulkRoleAssignmentRetainsExistingOrderAndAppendsNewScenes(): Unit = runBlocking {
        repository.reorder(listOf("Curated"))
        repository.assignRoleToUnassigned(listOf("Curated", "New", "New"), SceneRole.SECONDARY)
        assertEquals(0, repository.byName("Curated")?.sortOrder)
        assertEquals(SceneRole.PRIMARY, repository.byName("Curated")?.role)
        assertEquals(Int.MAX_VALUE, repository.byName("New")?.sortOrder)
        assertEquals(SceneRole.SECONDARY, repository.byName("New")?.role)
    }

    /** Freezes a snapshot after reading, before a mutation can write it back. */
    private class GatedRegistryDao(private val delegate: SceneRegistryDao) : SceneRegistryDao {
        val snapshotEntered = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        var gateNextSnapshot = false
        var snapshotReads = 0
        var byNameReads = 0
        var bulkWrites = 0

        override fun observeAll() =
            delegate.observeAll().map { snapshot ->
                snapshotReads++
                if (gateNextSnapshot) {
                    gateNextSnapshot = false
                    snapshotEntered.complete(Unit)
                    releaseSnapshot.await()
                }
                snapshot
            }

        override suspend fun byName(sceneName: String): SceneRegistryEntity? {
            byNameReads++
            return delegate.byName(sceneName)
        }

        override suspend fun upsert(entity: SceneRegistryEntity) = delegate.upsert(entity)

        override suspend fun upsertAll(entities: List<SceneRegistryEntity>) {
            bulkWrites++
            delegate.upsertAll(entities)
        }

        override suspend fun deleteStale(validSceneNames: List<String>) =
            delegate.deleteStale(validSceneNames)

        override suspend fun delete(sceneName: String) = delegate.delete(sceneName)
    }
}
