package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class WorkspacePersistenceTest {

    private lateinit var database: SceneDeckDatabase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
    }

    @After fun tearDown() = database.close()

    @Test
    fun sameSceneNameIsIndependentAcrossConnectionsAndCollections(): Unit = runBlocking {
        val dao = database.workspaceSceneDao()
        val home = entry(1, "Show", "Camera", "PRIMARY", 2)
        val studio = entry(2, "Show", "Camera", "DEBUG", 5)
        val rehearsal = entry(1, "Rehearsal", "Camera", "SECONDARY", 0)
        dao.upsertAll(listOf(home, studio, rehearsal))

        assertEquals(home, dao.byName(1, "Show", "Camera"))
        assertEquals(listOf(studio), dao.observeAll(2, "Show").first())
        assertEquals(listOf(rehearsal), dao.observeAll(1, "Rehearsal").first())
        assertEquals(
            emptyList<SceneRegistryEntity>(),
            database.sceneRegistryDao().observeAll().first(),
        )

        dao.upsert(home.copy(role = "ARCHIVE", sortOrder = 0))
        assertEquals("ARCHIVE", dao.byName(1, "Show", "Camera")?.role)
        assertEquals(studio, dao.byName(2, "Show", "Camera"))
        assertEquals(rehearsal, dao.byName(1, "Rehearsal", "Camera"))
    }

    @Test
    fun scopedCleanupAndRenamePreserveOtherWorkspaces(): Unit = runBlocking {
        val dao = database.workspaceSceneDao()
        val camera = entry(1, "Show", "Camera", "PRIMARY", 1)
        val other = entry(2, "Show", "Host", "SECONDARY", 3)
        val otherGhost = entry(2, "Show", "Ghost", "DEBUG", 4)
        dao.upsertAll(listOf(camera, other, otherGhost, camera.copy(sceneName = "Ghost")))

        assertTrue(dao.rename(1, "Show", "Camera", "Host"))
        assertNull(dao.byName(1, "Show", "Camera"))
        assertEquals(camera.copy(sceneName = "Host"), dao.byName(1, "Show", "Host"))
        dao.deleteStale(1, "Show", listOf("Host"))
        assertNull(dao.byName(1, "Show", "Ghost"))
        assertEquals(otherGhost, dao.byName(2, "Show", "Ghost"))
        dao.delete(1, "Show", "Host")
        assertEquals(other, dao.byName(2, "Show", "Host"))
    }

    @Test
    fun renameConflictKeepsBothEntriesWithinTheWorkspace(): Unit = runBlocking {
        val dao = database.workspaceSceneDao()
        val old = entry(1, "Show", "Camera", "PRIMARY", 0)
        val destination = entry(1, "Show", "Host", "ARCHIVE", 1)
        dao.upsertAll(listOf(old, destination))

        assertFalse(dao.rename(1, "Show", "Camera", "Host"))
        assertEquals(listOf(old, destination), dao.observeAll(1, "Show").first())
    }

    @Test
    fun legacyOperationsCannotReadRenameOrDeleteScopedCuration(): Unit = runBlocking {
        val legacy = database.sceneRegistryDao()
        val scoped = entry(1, "Show", "Camera", "PRIMARY", 0)
        database.workspaceSceneDao().upsert(scoped)
        assertNull(legacy.byName("Camera"))
        assertFalse(legacy.rename("Camera", "Host"))

        legacy.upsert(SceneRegistryEntity("Camera", "DEBUG"))
        assertTrue(legacy.rename("Camera", "Host"))
        legacy.delete("Camera")
        legacy.deleteStale(emptyList())
        assertTrue(legacy.observeAll().first().isEmpty())
        assertEquals(scoped, database.workspaceSceneDao().byName(1, "Show", "Camera"))
    }

    private fun entry(
        profileId: Long,
        collection: String,
        name: String,
        role: String,
        order: Int,
    ) =
        SceneRegistryEntity(
            sceneName = name,
            role = role,
            accentColorArgb = 0xFF7E57C2,
            iconName = "CAMERA",
            sortOrder = order,
            connectionProfileId = profileId,
            sceneCollectionName = collection,
        )
}
