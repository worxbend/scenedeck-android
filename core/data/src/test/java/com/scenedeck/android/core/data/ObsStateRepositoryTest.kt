package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObsStateRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var client: FakeObsClient
    private lateinit var registryDao: FakeRegistryDao
    private lateinit var repository: ObsStateRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        client =
            FakeObsClient(
                sceneListSnapshot =
                    SceneListSnapshot(
                        currentProgramScene = "Scene",
                        scenes =
                            listOf(
                                SceneSummary("Scene", 0),
                                SceneSummary("Cam 1", 1),
                                SceneSummary("Screen", 2),
                            ),
                    )
            )
        registryDao = FakeRegistryDao()
        repository = ObsStateRepository(client, RegistryRepository(registryDao), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun scenesWithoutRegistryEntryDefaultToPrimarySortedByName(): Unit = runBlocking {
        client.setReady()
        val deck = awaitDeck { it.scenes.isNotEmpty() }

        assertEquals(listOf("Cam 1", "Scene", "Screen"), deck.scenes.map { it.name })
        assertTrue(deck.scenes.all { it.role == SceneRole.PRIMARY })
        assertEquals("Scene", deck.currentProgramScene)
        assertEquals("Scene", deck.scenes.first { it.isActive }.name)
    }

    @Test
    fun registryJoinFiltersRolesAndAppliesMetadata(): Unit = runBlocking {
        registryDao.upsert(
            SceneRegistryEntity(
                sceneName = "Cam 1",
                role = "PRIMARY",
                accentColorArgb = 0xFF7E57C2,
                iconName = "CAMERA",
                sortOrder = 0,
            )
        )
        registryDao.upsert(SceneRegistryEntity(sceneName = "Screen", role = "SECONDARY"))

        client.setReady()
        val deck = awaitDeck { it.scenes.size == 2 }

        assertEquals(listOf("Cam 1", "Scene"), deck.scenes.map { it.name })
        val cam = deck.scenes.first { it.name == "Cam 1" }
        assertEquals(0xFF7E57C2, cam.accentColorArgb)
        assertEquals("CAMERA", cam.iconName)
        assertFalse(deck.scenes.any { it.name == "Screen" })
        assertEquals(listOf("Cam 1", "Screen", "Scene"), deck.allScenes.map { it.name })
        assertEquals(SceneRole.SECONDARY, deck.allScenes.first { it.name == "Screen" }.role)
    }

    @Test
    fun programSceneEventFlipsActiveCard(): Unit = runBlocking {
        client.setReady()
        awaitDeck { it.scenes.isNotEmpty() }

        client.emit(ObsEvent.CurrentProgramSceneChanged("Cam 1"))

        val deck = awaitDeck { it.currentProgramScene == "Cam 1" }
        assertTrue(deck.scenes.first { it.name == "Cam 1" }.isActive)
        assertFalse(deck.scenes.first { it.name == "Scene" }.isActive)
    }

    @Test
    fun sceneListChangedEventUpdatesDeckAndPreservesCuration(): Unit = runBlocking {
        registryDao.upsert(SceneRegistryEntity(sceneName = "Ghost", role = "PRIMARY"))
        client.setReady()
        awaitDeck { it.scenes.size == 3 }

        // A different host/collection may still contain this locally curated scene.
        assertEquals("PRIMARY", registryDao.byName("Ghost")?.role)

        client.emit(
            ObsEvent.SceneListChanged(
                scenes = listOf(SceneSummary("Scene", 0), SceneSummary("Cam 1", 1))
            )
        )

        val deck = awaitDeck { it.scenes.size == 2 }
        assertEquals(listOf("Cam 1", "Scene"), deck.scenes.map { it.name })
    }

    @Test
    fun reorderDeckPersistsSortOrder(): Unit = runBlocking {
        client.setReady()
        awaitDeck { it.scenes.isNotEmpty() }

        repository.reorderDeck(listOf("Screen", "Cam 1", "Scene"))

        val deck = awaitDeck { it.scenes.first().name == "Screen" }
        assertEquals(listOf("Screen", "Cam 1", "Scene"), deck.scenes.map { it.name })
    }

    @Test
    fun disconnectClearsDeck(): Unit = runBlocking {
        client.setReady()
        awaitDeck { it.scenes.isNotEmpty() }

        client.setDisconnected()

        // Scene clearing and connection-state propagation use separate flow collectors.
        val deck = awaitDeck {
            it.scenes.isEmpty() &&
                it.connectionState == com.scenedeck.android.core.model.ConnectionState.Disconnected
        }
        assertEquals(
            com.scenedeck.android.core.model.ConnectionState.Disconnected,
            deck.connectionState,
        )
    }

    @Test
    fun sceneListRefreshDoesNotRunDestructiveCleanupOrStopProgramEvents(): Unit = runBlocking {
        client.setReady()
        awaitDeck { it.scenes.isNotEmpty() }
        registryDao.failCleanup = true
        client.emit(
            ObsEvent.SceneListChanged(listOf(SceneSummary("Scene", 0), SceneSummary("Cam 1", 1)))
        )
        awaitDeck { it.allScenes.size == 2 }
        client.emit(ObsEvent.CurrentProgramSceneChanged("Cam 1"))
        assertEquals("Cam 1", awaitDeck { it.currentProgramScene == "Cam 1" }.currentProgramScene)
    }

    private suspend fun awaitDeck(condition: (DeckState) -> Boolean): DeckState =
        withTimeout(5_000) { repository.deckState.first(condition) }

    /** In-memory DAO fake with observable state. */
    private class FakeRegistryDao : SceneRegistryDao {
        var failCleanup = false
        private val entities = MutableStateFlow<Map<String, SceneRegistryEntity>>(emptyMap())

        override fun observeAll(): Flow<List<SceneRegistryEntity>> = entities.map { map ->
            map.values.sortedWith(compareBy({ it.sortOrder }, { it.sceneName }))
        }

        override suspend fun byName(sceneName: String): SceneRegistryEntity? =
            entities.value[sceneName]

        override suspend fun upsert(entity: SceneRegistryEntity) {
            entities.update { it + (entity.sceneName to entity) }
        }

        override suspend fun upsertAll(entities: List<SceneRegistryEntity>) {
            this.entities.update { current -> current + entities.associateBy { it.sceneName } }
        }

        override suspend fun deleteStale(validSceneNames: List<String>) {
            check(!failCleanup)
            entities.update { current -> current.filterKeys { it in validSceneNames } }
        }

        override suspend fun delete(sceneName: String) {
            entities.update { it - sceneName }
        }
    }
}
