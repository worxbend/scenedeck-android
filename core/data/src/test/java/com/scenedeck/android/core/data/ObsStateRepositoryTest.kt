package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun scenesWithoutRegistryEntryDefaultToPrimaryInObsOrder(): Unit = runBlocking {
        client.setReady()
        val deck = awaitDeck { it.scenes.isNotEmpty() }

        // OBS lists its top scene with the highest sceneIndex.
        assertEquals(listOf("Screen", "Cam 1", "Scene"), deck.scenes.map { it.name })
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
        registryDao.upsert(
            SceneRegistryEntity(sceneName = "Screen", role = "SECONDARY", sortOrder = 1)
        )

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

    @Test
    fun savedPositionsComeFirstAndUnplacedScenesFollowObsOrder(): Unit = runBlocking {
        registryDao.upsert(
            SceneRegistryEntity(sceneName = "Scene", role = "PRIMARY", sortOrder = 0)
        )
        registryDao.upsert(
            SceneRegistryEntity(
                sceneName = "Screen",
                role = "PRIMARY",
                accentColorArgb = 0xFF00FF00,
                sortOrder = UNPLACED_SORT_ORDER,
            )
        )

        client.setReady()
        val deck = awaitDeck { it.allScenes.size == 3 }

        assertEquals(listOf("Scene", "Screen", "Cam 1"), deck.allScenes.map { it.name })
    }

    @Test
    fun sceneRenameInObsKeepsCurationAndPosition(): Unit = runBlocking {
        RegistryRepository(registryDao).reorder(listOf("Cam 1", "Screen", "Scene"))
        registryDao.upsert(
            SceneRegistryEntity(
                sceneName = "Cam 1",
                role = "SECONDARY",
                iconName = "CAMERA",
                sortOrder = 0,
            )
        )
        client.setReady()
        awaitDeck { it.allScenes.size == 3 }

        client.emit(ObsEvent.SceneNameChanged(oldSceneName = "Cam 1", sceneName = "Camera"))

        val deck = awaitDeck { it.allScenes.firstOrNull()?.name == "Camera" }
        assertEquals(listOf("Camera", "Screen", "Scene"), deck.allScenes.map { it.name })
        assertEquals("CAMERA", deck.allScenes.first().iconName)
        assertEquals(SceneRole.SECONDARY, deck.allScenes.first().role)
        assertNull(registryDao.byName("Cam 1"))
    }

    @Test
    fun renamingProgramAndPreviewScenesKeepsTalliesOnTheNewNames(): Unit = runBlocking {
        client.studioModeEnabled = true
        client.previewScene = "Screen"
        client.setReady()
        awaitDeck { it.previewScene == "Screen" && it.allScenes.size == 3 }

        client.emit(ObsEvent.SceneNameChanged(oldSceneName = "Scene", sceneName = "Main"))
        client.emit(ObsEvent.SceneNameChanged(oldSceneName = "Screen", sceneName = "Display"))

        val deck = awaitDeck { it.previewScene == "Display" && it.currentProgramScene == "Main" }
        assertEquals(listOf("Display", "Cam 1", "Main"), deck.allScenes.map { it.name })
        assertEquals("Main", deck.allScenes.single { it.isActive }.name)
        assertEquals("Display", deck.allScenes.single { it.isPreview }.name)

        // A tap on the renamed card targets OBS by its new name.
        repository.setCurrentProgramScene(deck.allScenes.single { it.isActive }.name)
        assertEquals(listOf("Main"), client.sceneSwitchCalls)
    }

    @Test
    fun renameOntoAnExistingEntryKeepsBothEntries(): Unit = runBlocking {
        registryDao.upsert(SceneRegistryEntity("Cam 1", role = "SECONDARY", iconName = "CAMERA"))
        // Stale curation left behind by an older scene that used the new name.
        registryDao.upsert(SceneRegistryEntity("Camera", role = "ARCHIVE"))
        client.setReady()
        awaitDeck { it.allScenes.size == 3 }

        client.emit(ObsEvent.SceneNameChanged(oldSceneName = "Cam 1", sceneName = "Camera"))

        val deck = awaitDeck { deck -> deck.allScenes.any { it.name == "Camera" } }
        assertEquals(SceneRole.ARCHIVE, deck.allScenes.single { it.name == "Camera" }.role)
        assertEquals("CAMERA", registryDao.byName("Cam 1")?.iconName)
        assertEquals("ARCHIVE", registryDao.byName("Camera")?.role)
    }

    @Test
    fun renameWhileSceneListReadIsInFlightIsNotUndoneByItsResult(): Unit = runBlocking {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val slowClient =
            object :
                FakeObsClient(
                    sceneListSnapshot =
                        SceneListSnapshot(
                            currentProgramScene = "Cam 1",
                            scenes = listOf(SceneSummary("Scene", 0), SceneSummary("Cam 1", 1)),
                        )
                ) {
                var reads = 0

                override suspend fun getSceneList(): SceneListSnapshot {
                    reads++
                    val answer = super.getSceneList() // answered before the rename
                    readStarted.complete(Unit)
                    releaseRead.await()
                    return answer
                }
            }
        val slowRepository = ObsStateRepository(slowClient, RegistryRepository(registryDao), scope)
        registryDao.upsert(SceneRegistryEntity("Cam 1", role = "SECONDARY"))
        val deckJob = scope.launch { slowRepository.deckState.collect {} }
        slowClient.setReady()
        withTimeout(5_000) {
            readStarted.await()
            (slowClient.events as MutableSharedFlow<ObsEvent>).subscriptionCount.first { it > 0 }
        }

        slowClient.sceneListSnapshot =
            SceneListSnapshot(
                currentProgramScene = "Camera",
                scenes = listOf(SceneSummary("Scene", 0), SceneSummary("Camera", 1)),
            )
        slowClient.emit(ObsEvent.SceneNameChanged(oldSceneName = "Cam 1", sceneName = "Camera"))
        withTimeout(5_000) {
            registryDao.observeAll().first { it.any { e -> e.sceneName == "Camera" } }
        }
        releaseRead.complete(Unit)

        val deck =
            withTimeout(5_000) {
                slowRepository.deckState.first { it.allScenes.size == 2 }
            }
        assertEquals(setOf("Scene", "Camera"), deck.allScenes.map { it.name }.toSet())
        assertEquals("Camera", deck.currentProgramScene)
        assertEquals(SceneRole.SECONDARY, deck.allScenes.single { it.name == "Camera" }.role)
        // The raced read is repeated so the applied list is one OBS answered after the rename.
        assertEquals(2, slowClient.reads)
        deckJob.cancel()
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
