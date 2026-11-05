package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.database.WorkspaceSceneDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegistryRepositoryTest {

    private val origin = TEST_WORKSPACE
    private val workspaces = MutableWorkspaceContext()
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
        repository =
            RegistryRepository(
                database.workspaceSceneDao(),
                workspaces,
                database.sceneRegistryDao(),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updateUpsertsAndObserves(): Unit = runBlocking {
        repository.state
            .filterIsInstance<RegistryState.Ready>()
            .map { it.entries }
            .test {
                assertEquals(0, awaitItem().size)

                repository.update(origin, "Cam 1", SceneRole.PRIMARY, 0xFF7E57C2, "CAMERA")
                val entry = awaitItem().single()
                assertEquals("Cam 1", entry.sceneName)
                assertEquals(SceneRole.PRIMARY, entry.role)
                assertEquals(0xFF7E57C2, entry.accentColorArgb)
                assertEquals("CAMERA", entry.iconName)

                // Upsert again (role change) — no duplicate.
                repository.update(origin, "Cam 1", SceneRole.SECONDARY, null, null)
                val updated = awaitItem().single()
                assertEquals(SceneRole.SECONDARY, updated.role)
                assertNull(updated.accentColorArgb)

                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun restoreReinsertsRemovedEntryWithoutOverwritingNewerCuration(): Unit = runBlocking {
        repository.update(origin, "Ghost", SceneRole.ARCHIVE, 0xFF7E57C2, "FILM")
        repository.reorder(origin, listOf("Ghost"))
        val removed = repository.byName(origin, "Ghost")!!
        repository.remove(origin, "Ghost")
        assertNull(repository.byName(origin, "Ghost"))

        repository.restore(origin, removed)
        assertEquals(removed, repository.byName(origin, "Ghost"))

        repository.update(origin, "Ghost", SceneRole.DEBUG, null, null)
        repository.restore(origin, removed)
        assertEquals(SceneRole.DEBUG, repository.byName(origin, "Ghost")?.role)
    }

    @Test
    fun reorderAssignsSequentialSortOrder(): Unit = runBlocking {
        repository.reorder(origin, listOf("B", "A", "C"))

        assertEquals(0, repository.byName(origin, "B")?.sortOrder)
        assertEquals(1, repository.byName(origin, "A")?.sortOrder)
        assertEquals(2, repository.byName(origin, "C")?.sortOrder)
    }

    @Test
    fun reorderPreservesMetadataOfExistingEntries(): Unit = runBlocking {
        repository.update(origin, "Cam 1", SceneRole.PRIMARY, 0xFF000000, "CAMERA")

        repository.reorder(origin, listOf("Cam 1"))

        val entry = repository.byName(origin, "Cam 1")!!
        assertEquals(0, entry.sortOrder)
        assertEquals(0xFF000000, entry.accentColorArgb)
        assertEquals("CAMERA", entry.iconName)
    }

    @Test
    fun deleteStaleRemovesOnlyGoneScenes(): Unit = runBlocking {
        repository.update(origin, "Scene", SceneRole.PRIMARY, null, null)
        repository.update(origin, "Ghost", SceneRole.PRIMARY, null, null)

        repository.deleteStale(origin, listOf("Scene"))

        assertEquals("Scene", repository.byName(origin, "Scene")?.sceneName)
        assertNull(repository.byName(origin, "Ghost"))
    }

    @Test
    fun deleteStaleWithEmptyListIsANoOp(): Unit = runBlocking {
        repository.update(origin, "Scene", SceneRole.PRIMARY, null, null)
        repository.deleteStale(origin, emptyList())
        assertEquals("Scene", repository.byName(origin, "Scene")?.sceneName)
    }

    @Test
    fun interleavedMutationsCannotClobberEachOther(): Unit = runBlocking {
        val gated = GatedRegistryDao(database.workspaceSceneDao())
        val repo = RegistryRepository(gated, workspaces, database.sceneRegistryDao())
        repo.update(origin, "A", SceneRole.PRIMARY, null, null)

        // Hold reorder() after its snapshot read, then queue update() behind it.
        // Without serialization the update would land and then be overwritten by
        // reorder()'s stale read.
        gated.gateNextSnapshot = true
        val reorder = async { repo.reorder(origin, listOf("A")) }
        gated.snapshotEntered.await()
        val update = async { repo.update(origin, "A", SceneRole.SECONDARY, null, null) }
        gated.releaseSnapshot.complete(Unit)
        reorder.await()
        update.await()

        assertEquals(SceneRole.SECONDARY, repo.byName(origin, "A")?.role)
        assertEquals(0, repo.byName(origin, "A")?.sortOrder)
    }

    @Test
    fun laterDeletesCannotBeResurrectedByReorder(): Unit = runBlocking {
        for (staleCleanup in listOf(false, true)) {
            val gated = GatedRegistryDao(database.workspaceSceneDao())
            val repo = RegistryRepository(gated, workspaces, database.sceneRegistryDao())
            repo.update(origin, "A", SceneRole.PRIMARY, null, null)
            repo.update(origin, "Keep", SceneRole.SECONDARY, null, null)
            gated.gateNextSnapshot = true
            val reorder = async { repo.reorder(origin, listOf("A", "Keep")) }
            gated.snapshotEntered.await()
            val deletion = async {
                if (staleCleanup) repo.deleteStale(origin, listOf("Keep"))
                else repo.remove(origin, "A")
            }
            yield()
            gated.releaseSnapshot.complete(Unit)
            reorder.await()
            deletion.await()
            assertNull(repo.byName(origin, "A"))
            assertEquals(SceneRole.SECONDARY, repo.byName(origin, "Keep")?.role)
        }
    }

    @Test
    fun bulkMutationsUseOneSnapshotAndOneWriteForLargeCollections(): Unit = runBlocking {
        val counted = GatedRegistryDao(database.workspaceSceneDao())
        val repo = RegistryRepository(counted, workspaces, database.sceneRegistryDao())
        val names = (0 until 1000).map { "Scene$it" }
        repo.reorder(origin, names)
        assertEquals(1, counted.snapshotReads)
        assertEquals(0, counted.byNameReads)
        assertEquals(1, counted.bulkWrites)
        repo.assignRoleToUnassigned(origin, names + "New", SceneRole.SECONDARY)
        assertEquals(2, counted.snapshotReads)
        assertEquals(0, counted.byNameReads)
        assertEquals(2, counted.bulkWrites)
        repo.importMerge(origin, names.map { SceneRegistryEntry(it, SceneRole.ARCHIVE) })
        assertEquals(3, counted.snapshotReads)
        assertEquals(0, counted.byNameReads)
        assertEquals(3, counted.bulkWrites)
    }

    @Test
    fun duplicateImportsPreserveSequentialCountsNullMergesAndFileOrder(): Unit = runBlocking {
        repository.update(origin, "Existing", SceneRole.PRIMARY, 123L, "CAMERA")
        repository.reorder(origin, listOf("Existing"))
        val counts =
            repository.importMerge(
                origin,
                listOf(
                    SceneRegistryEntry("New", SceneRole.SECONDARY, 456L, "MIC", 7),
                    SceneRegistryEntry("New", SceneRole.MODULE, null, null, 99),
                    SceneRegistryEntry("Existing", SceneRole.ARCHIVE, null, null, 50),
                ),
            )
        assertEquals(1 to 2, counts)
        // The first occurrence fixes a name's file position; positions are renumbered densely.
        assertEquals(
            SceneRegistryEntry("New", SceneRole.MODULE, 456L, "MIC", 0),
            repository.byName(origin, "New"),
        )
        assertEquals(
            SceneRegistryEntry("Existing", SceneRole.ARCHIVE, 123L, "CAMERA", 1),
            repository.byName(origin, "Existing"),
        )
    }

    @Test
    fun bulkRoleAssignmentFillsUnassignedRolesAndKeepsOrder(): Unit = runBlocking {
        repository.reorder(origin, listOf("Curated", "Kept"))
        repository.update(origin, "Kept", SceneRole.PRIMARY, null, null)
        repository.assignRoleToUnassigned(
            origin,
            listOf("Curated", "Kept", "New", "New"),
            SceneRole.SECONDARY,
        )
        assertEquals(0, repository.byName(origin, "Curated")?.sortOrder)
        assertEquals(SceneRole.SECONDARY, repository.byName(origin, "Curated")?.role)
        assertEquals(1, repository.byName(origin, "Kept")?.sortOrder)
        assertEquals(SceneRole.PRIMARY, repository.byName(origin, "Kept")?.role)
        assertEquals(UNPLACED_SORT_ORDER, repository.byName(origin, "New")?.sortOrder)
        assertEquals(SceneRole.SECONDARY, repository.byName(origin, "New")?.role)
    }

    @Test
    fun reorderNeverAssignsRolesAndRenumbersEveryPlacedEntryDensely(): Unit = runBlocking {
        repository.update(origin, "Stale", SceneRole.MODULE, null, null)
        repository.reorder(origin, listOf("Stale", "X"))
        repository.update(origin, "Colour only", null, 0xFF00FF00, null)

        repository.reorder(origin, listOf("B", "A"))

        val saved = repository.snapshot(origin)
        assertEquals(listOf("B", "A", "Stale", "X", "Colour only"), saved.map { it.sceneName })
        assertEquals(listOf(0, 1, 2, 3), saved.take(4).map { it.sortOrder })
        assertNull(repository.byName(origin, "B")?.role)
        assertNull(repository.byName(origin, "X")?.role)
        assertEquals(SceneRole.MODULE, repository.byName(origin, "Stale")?.role)
        // Editing a colour neither places the scene nor assigns it a role.
        assertEquals(UNPLACED_SORT_ORDER, repository.byName(origin, "Colour only")?.sortOrder)
        assertNull(repository.byName(origin, "Colour only")?.role)
    }

    @Test
    fun renameMovesCurationAndPosition(): Unit = runBlocking {
        repository.reorder(origin, listOf("Intro", "Cam 1"))
        repository.update(origin, "Cam 1", SceneRole.SECONDARY, 0xFF7E57C2, "CAMERA")

        assertTrue(repository.rename(origin, "Cam 1", "Camera"))

        assertNull(repository.byName(origin, "Cam 1"))
        assertEquals(
            SceneRegistryEntry("Camera", SceneRole.SECONDARY, 0xFF7E57C2, "CAMERA", 1),
            repository.byName(origin, "Camera"),
        )
    }

    @Test
    fun renameKeepsCurationAlreadyMadeForTheNewName(): Unit = runBlocking {
        repository.update(origin, "Old", SceneRole.ARCHIVE, null, null)
        repository.update(origin, "New", SceneRole.PRIMARY, null, "STAR")

        assertFalse(repository.rename(origin, "Old", "New"))

        assertEquals(SceneRole.ARCHIVE, repository.byName(origin, "Old")?.role)
        assertEquals("STAR", repository.byName(origin, "New")?.iconName)
    }

    @Test
    fun renameWithoutCurationOrToItselfChangesNothing(): Unit = runBlocking {
        repository.update(origin, "Cam 1", SceneRole.SECONDARY, null, null)

        assertFalse(repository.rename(origin, "Unknown", "Other"))
        assertFalse(repository.rename(origin, "Cam 1", "Cam 1"))

        assertEquals(listOf("Cam 1"), repository.snapshot(origin).map { it.sceneName })
    }

    @Test
    fun renameIsOneAtomicChangeForObservers(): Unit = runBlocking {
        repository.reorder(origin, listOf("Intro", "Cam 1", "Outro"))

        repository.state
            .filterIsInstance<RegistryState.Ready>()
            .map { it.entries }
            .map { list -> list.map { it.sceneName } }
            .test {
                assertEquals(listOf("Intro", "Cam 1", "Outro"), awaitItem())
                repository.rename(origin, "Cam 1", "Camera")
                // Never both rows, never neither: the move lands in one transaction.
                assertEquals(listOf("Intro", "Camera", "Outro"), awaitItem())
                expectNoEvents()
            }
    }

    @Test
    fun exportImportRoundTripRestoresOrderRolesAndUnassignedScenes(): Unit = runBlocking {
        repository.reorder(origin, listOf("Intro", "Camera", "Screen", "BRB"))
        repository.update(origin, "Camera", SceneRole.SECONDARY, 0xFF7E57C2, "CAMERA")
        repository.update(origin, "Unplaced", SceneRole.ARCHIVE, null, null)
        val exported = repository.snapshot(origin)
        val yaml = RegistryExportCodec.encode(exported)

        // Another device with its own, different curation.
        val otherDatabase =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        try {
            val other =
                RegistryRepository(
                    otherDatabase.workspaceSceneDao(),
                    workspaces,
                    otherDatabase.sceneRegistryDao(),
                )
            other.reorder(origin, listOf("BRB", "Local only", "Screen", "Intro"))

            other.importMerge(origin, RegistryExportCodec.decode(yaml))

            val restored = other.snapshot(origin)
            assertEquals(
                listOf("Intro", "Camera", "Screen", "BRB", "Local only", "Unplaced"),
                restored.map { it.sceneName },
            )
            assertEquals(exported, restored.filter { it.sceneName != "Local only" })
            assertNull(other.byName(origin, "Intro")?.role)
        } finally {
            otherDatabase.close()
        }
    }

    @Test
    fun importWithoutSavedPositionsKeepsFileOrder(): Unit = runBlocking {
        val yaml =
            """
            format: scenedeck-registry
            entries:
              - sceneName: Zeta
                role: PRIMARY
              - sceneName: Alpha
                role: SECONDARY
            """
                .trimIndent()

        repository.importMerge(origin, RegistryExportCodec.decode(yaml))

        assertEquals(listOf("Zeta", "Alpha"), repository.snapshot(origin).map { it.sceneName })
    }

    @Test
    fun sameNamesRemainIndependentAcrossProfilesAndCollections(): Unit = runBlocking {
        val otherHost = origin.copy(key = WorkspaceKey(8, "Studio"), targetGeneration = 2)
        val otherCollection = origin.copy(key = WorkspaceKey(7, "Other"), targetGeneration = 3)
        repository.update(origin, "Camera", SceneRole.PRIMARY, 123L, "CAMERA")
        repository.reorder(origin, listOf("Camera", "Screen"))
        workspaces.select(otherHost)
        repository.update(otherHost, "Camera", SceneRole.ARCHIVE, 456L, "FILM")
        repository.reorder(otherHost, listOf("Screen", "Camera"))
        workspaces.select(otherCollection)
        assertTrue(repository.snapshot(otherCollection).isEmpty())
        repository.update(otherCollection, "Camera", SceneRole.DEBUG, null, null)
        workspaces.select(origin)
        assertEquals(SceneRole.PRIMARY, repository.byName(origin, "Camera")?.role)
        assertEquals(123L, repository.byName(origin, "Camera")?.accentColorArgb)
        assertEquals(listOf("Camera", "Screen"), repository.snapshot(origin).map { it.sceneName })
        workspaces.select(otherHost)
        assertEquals(SceneRole.ARCHIVE, repository.byName(otherHost, "Camera")?.role)
        assertEquals(
            listOf("Screen", "Camera"),
            repository.snapshot(otherHost).map { it.sceneName },
        )
        workspaces.select(otherCollection)
        assertEquals(SceneRole.DEBUG, repository.byName(otherCollection, "Camera")?.role)
    }

    @Test
    fun queuedAndSuspendedMutationsCannotFollowWorkspaceReplacement(): Unit = runBlocking {
        val gated = GatedRegistryDao(database.workspaceSceneDao())
        val repo = RegistryRepository(gated, workspaces, database.sceneRegistryDao())
        repo.update(origin, "Camera", SceneRole.PRIMARY, null, null)
        gated.gateNextSnapshot = true
        val reorder = async { runCatching { repo.reorder(origin, listOf("Camera")) } }
        withTimeout(5_000) { gated.snapshotEntered.await() }
        val edit =
            async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repo.update(origin, "Camera", SceneRole.ARCHIVE, null, null) }
            }
        val replacement = origin.copy(key = WorkspaceKey(8, "Studio"), targetGeneration = 2)
        workspaces.select(replacement)
        gated.releaseSnapshot.complete(Unit)
        assertTrue(reorder.await().exceptionOrNull() is WorkspaceChangedException)
        assertTrue(edit.await().exceptionOrNull() is WorkspaceChangedException)
        assertTrue(repo.snapshot(replacement).isEmpty())
        workspaces.select(origin)
        assertEquals(SceneRole.PRIMARY, repo.byName(origin, "Camera")?.role)
        assertEquals(UNPLACED_SORT_ORDER, repo.byName(origin, "Camera")?.sortOrder)
    }

    @Test
    fun legacyCurationNeedsExplicitCopyAndKeepsItsSource(): Unit = runBlocking {
        database.sceneRegistryDao().upsert(SceneRegistryEntity("Camera", "ARCHIVE", sortOrder = 4))
        assertTrue(repository.snapshot(origin).isEmpty())
        val previous = repository.legacyEntries.first()
        repository.importMerge(origin, previous)
        assertEquals(SceneRole.ARCHIVE, repository.byName(origin, "Camera")?.role)
        assertEquals(previous, repository.legacyEntries.first())
        val other = origin.copy(key = WorkspaceKey(8, "Studio"), targetGeneration = 2)
        workspaces.select(other)
        assertTrue(repository.snapshot(other).isEmpty())
        assertTrue(
            runCatching { repository.importMerge(origin, previous) }.exceptionOrNull()
                is WorkspaceChangedException
        )
        assertTrue(repository.snapshot(other).isEmpty())
    }

    /** Freezes a snapshot after reading, before a mutation can write it back. */
    private class GatedRegistryDao(private val delegate: WorkspaceSceneDao) : WorkspaceSceneDao {
        val snapshotEntered = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        var gateNextSnapshot = false
        var snapshotReads = 0
        var byNameReads = 0
        var bulkWrites = 0

        override fun observeAll(profileId: Long, collectionName: String) =
            delegate.observeAll(profileId, collectionName).map { snapshot ->
                snapshotReads++
                if (gateNextSnapshot) {
                    gateNextSnapshot = false
                    snapshotEntered.complete(Unit)
                    releaseSnapshot.await()
                }
                snapshot
            }

        override suspend fun byName(
            profileId: Long,
            collectionName: String,
            sceneName: String,
        ): SceneRegistryEntity? {
            byNameReads++
            return delegate.byName(profileId, collectionName, sceneName)
        }

        override suspend fun upsert(entity: SceneRegistryEntity) = delegate.upsert(entity)

        override suspend fun upsertAll(entities: List<SceneRegistryEntity>) {
            bulkWrites++
            delegate.upsertAll(entities)
        }

        override suspend fun deleteStale(
            profileId: Long,
            collectionName: String,
            validSceneNames: List<String>,
        ) = delegate.deleteStale(profileId, collectionName, validSceneNames)

        override suspend fun delete(profileId: Long, collectionName: String, sceneName: String) =
            delegate.delete(profileId, collectionName, sceneName)
    }
}
