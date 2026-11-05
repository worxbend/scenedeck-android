package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SceneAliasTest {
    private lateinit var database: SceneDeckDatabase
    private lateinit var repository: RegistryRepository
    private val workspaces = MutableWorkspaceContext()
    private val origin = TEST_WORKSPACE

    @Before
    fun setup() {
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

    @After fun close() = database.close()

    @Test
    fun labelEditsKeepIdentityRoleAndOrderAndBlankRemovesOnlyAlias(): Unit = runBlocking {
        repository.setAlias(origin, "Camera", "  Host  ")
        val first = repository.snapshot(origin).single()
        assertEquals("Camera", first.sceneName)
        assertEquals("Host", first.localAlias)
        assertNull(first.role)
        assertEquals(UNPLACED_SORT_ORDER, first.sortOrder)
        repository.edit(origin, "Camera") { it.copy(role = SceneRole.ARCHIVE, sortOrder = 8) }
        repository.setAlias(origin, "Camera", "")
        assertEquals(
            first.copy(role = SceneRole.ARCHIVE, sortOrder = 8, localAlias = null),
            repository.snapshot(origin).single(),
        )
        listOf("x".repeat(81), "Secret\nName").forEach { bad ->
            assertTrue(
                runCatching { repository.setAlias(origin, "Camera", bad) }.exceptionOrNull()
                    is SceneAliasException
            )
        }
        assertNull(repository.snapshot(origin).single().localAlias)
    }

    @Test
    fun exportImportPreservesAliasesAndLegacyMergeRetainsDestinationLabels(): Unit = runBlocking {
        repository.setAlias(origin, "Camera", "Host: Марія")
        repository.setAlias(origin, "Remote", "Host: Марія")
        val original = repository.snapshot(origin)
        val payload = RegistryExportCodec.encode(original)
        assertEquals(original, RegistryExportCodec.decode(payload))
        val other = origin.copy(key = WorkspaceKey(9, "Travel"), targetGeneration = 2)
        workspaces.select(other)
        repository.importMerge(other, RegistryExportCodec.decode(payload))
        assertEquals(original, repository.snapshot(other))
        val legacy =
            """{"format":"scenedeck-registry","version":1,"entries":[{"sceneName":"Camera","role":"MODULE"}]}"""
        repository.importMerge(other, RegistryExportCodec.decode(legacy))
        assertEquals(
            "Host: Марія",
            repository.snapshot(other).first { it.sceneName == "Camera" }.localAlias,
        )
        assertEquals(
            SceneRole.MODULE,
            repository.snapshot(other).first { it.sceneName == "Camera" }.role,
        )
        assertTrue(
            runCatching { repository.setAlias(origin, "Camera", "Wrong workspace") }
                .exceptionOrNull() is WorkspaceChangedException
        )
    }

    @Test
    fun renameDestinationConflictAndUndoKeepLabelsInTheirNamespace(): Unit = runBlocking {
        repository.setAlias(origin, "Old", "Host")
        repository.rename(origin, "Old", "New")
        assertEquals("Host", repository.snapshot(origin).single().localAlias)
        assertEquals("New", repository.snapshot(origin).single().sceneName)
        repository.setAlias(origin, "Existing", "Guest")
        repository.rename(origin, "New", "Existing")
        assertEquals(
            mapOf("New" to "Host", "Existing" to "Guest"),
            repository.snapshot(origin).associate { it.sceneName to it.localAlias },
        )
        val removed = requireNotNull(repository.remove(origin, "New"))
        repository.restore(origin, removed)
        assertEquals("Host", repository.snapshot(origin).first { it.sceneName == "New" }.localAlias)
    }
}
