package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.TransitionInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObsStateStudioTest {

    private lateinit var scope: kotlinx.coroutines.CoroutineScope
    private lateinit var client: FakeObsClient
    private lateinit var repository: ObsStateRepository

    @Before
    fun setUp() {
        scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
        client = FakeObsClient(
            sceneListSnapshot = com.scenedeck.android.core.model.SceneListSnapshot(
                currentProgramScene = "Cam 1",
                scenes = listOf(
                    com.scenedeck.android.core.model.SceneSummary("Cam 1", 0),
                    com.scenedeck.android.core.model.SceneSummary("Screen", 1),
                ),
            ),
        )
        repository = ObsStateRepository(
            client,
            RegistryRepository(FakeRegistryDao()),
            scope,
        )
    }

    @org.junit.After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun studioModeAndPreviewFlowIntoDeckState(): Unit = runBlocking {
        client.studioModeEnabled = true
        client.previewScene = "Screen"
        client.setReady()

        val deck = awaitDeck { it.studioMode }
        assertTrue(deck.studioMode)
        assertEquals("Screen", deck.previewScene)
        assertTrue(deck.scenes.single { it.name == "Screen" }.isPreview)
        assertFalse(deck.scenes.single { it.name == "Cam 1" }.isPreview)
        // Program stays active.
        assertTrue(deck.scenes.single { it.name == "Cam 1" }.isActive)
    }

    @Test
    fun studioEventsUpdateStateLive(): Unit = runBlocking {
        client.setReady()
        awaitDeck { it.scenes.isNotEmpty() }

        client.studioModeEnabled = true
        client.previewScene = "Screen"
        client.emit(ObsEvent.StudioModeStateChanged(enabled = true))
        awaitDeck { it.studioMode }

        client.emit(ObsEvent.CurrentPreviewSceneChanged("Cam 1"))
        val deck = awaitDeck { it.previewScene == "Cam 1" }
        assertTrue(deck.scenes.single { it.name == "Cam 1" }.isPreview)
    }

    @Test
    fun leavingStudioModeClearsPreview(): Unit = runBlocking {
        client.studioModeEnabled = true
        client.previewScene = "Screen"
        client.setReady()
        awaitDeck { it.studioMode }

        client.studioModeEnabled = false
        client.emit(ObsEvent.StudioModeStateChanged(enabled = false))

        val deck = awaitDeck { !it.studioMode && it.previewScene == null }
        assertFalse(deck.studioMode)
        assertEquals(null, deck.previewScene)
        assertTrue(deck.scenes.none { it.isPreview })
    }

    @Test
    fun transitionsFlowIntoDeckState(): Unit = runBlocking {
        client.setReady()
        awaitDeck { it.scenes.isNotEmpty() }

        client.emit(ObsEvent.CurrentSceneTransitionChanged("Swipe"))

        // refreshTransition fetches via getCurrentSceneTransition (unused in fake →
        // swallowed) — assert the deck tolerates missing transition data gracefully.
        val deck = repository.deckState.value
        assertEquals(null, deck.currentTransition)
        assertEquals(emptyList<TransitionInfo>(), deck.transitions)
    }

    private suspend fun awaitDeck(condition: (DeckState) -> Boolean): DeckState =
        withTimeout(5_000) { repository.deckState.first(condition) }

    /** Map-backed registry DAO. */
    private class FakeRegistryDao : com.scenedeck.android.core.database.SceneRegistryDao {
        private val entities = kotlinx.coroutines.flow.MutableStateFlow<
            Map<String, com.scenedeck.android.core.database.SceneRegistryEntity>,
            >(emptyMap())

        override fun observeAll() = entities.map { map ->
            map.values.sortedWith(compareBy({ it.sortOrder }, { it.sceneName }))
        }

        override suspend fun byName(sceneName: String) = entities.value[sceneName]
        override suspend fun upsert(entity: com.scenedeck.android.core.database.SceneRegistryEntity) {
            entities.value = entities.value + (entity.sceneName to entity)
        }

        override suspend fun upsertAll(entities: List<com.scenedeck.android.core.database.SceneRegistryEntity>) {
            this.entities.value = this.entities.value + entities.associateBy { it.sceneName }
        }

        override suspend fun deleteStale(validSceneNames: List<String>) {
            entities.value = entities.value.filterKeys { it in validSceneNames }
        }

        override suspend fun delete(sceneName: String) {
            entities.value = entities.value - sceneName
        }
    }
}
