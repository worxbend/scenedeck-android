package com.scenedeck.android.feature.inventory

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ScreenshotRequest
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InventoryViewModelTest {

    private lateinit var registry: RegistryRepository
    private lateinit var client: FakeObsClient
    private lateinit var viewModel: InventoryViewModel
    private lateinit var holderScope: CoroutineScope

    @Before
    fun setUp() {
        registry = RegistryRepository(FakeRegistryDao())
        client = FakeObsClient()
        holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings =
            SettingsRepository(
                SceneDeckSettingsStore.forTesting(
                    PreferenceDataStoreFactory.create(
                        produceFile = { File.createTempFile("inventory_test", ".preferences_pb") }
                    )
                )
            )
        val mixer = MixerRepository(client, settings, holderScope)
        viewModel = InventoryViewModel(registry, mixer, client)
    }

    @After
    fun tearDown() {
        holderScope.cancel()
    }

    @Test
    fun listsObsScenesAndStaleEntriesWithUnassignedCount(): Unit = runBlocking {
        registry.update("Cam 1", SceneRole.PRIMARY, null, null)
        registry.update("Ghost", SceneRole.MODULE, null, null)
        client.setReady()

        val state = awaitState { it.scenes.size == 4 }
        assertEquals(
            setOf("Cam 1", "Screen", "Quiet A", "Ghost"),
            state.scenes.map { it.name }.toSet(),
        )
        assertTrue(state.scenes.single { it.name == "Ghost" }.stale)
        assertEquals(2, state.unassignedCount) // Screen + Quiet A have no entry
    }

    @Test
    fun setRolePersistsThroughRepository(): Unit = runBlocking {
        client.setReady()
        awaitState { it.scenes.count { scene -> !scene.stale } == 3 }

        viewModel.setRole("Quiet A", SceneRole.ARCHIVE)
        awaitState { state ->
            state.scenes.single { it.name == "Quiet A" }.role == SceneRole.ARCHIVE
        }

        assertEquals(SceneRole.ARCHIVE, registry.byName("Quiet A")?.role)
    }

    @Test
    fun bulkAssignCreatesSecondaryEntriesOnlyForUnassigned(): Unit = runBlocking {
        registry.update("Cam 1", SceneRole.PRIMARY, null, null)
        client.setReady()
        awaitState { it.scenes.count { scene -> !scene.stale } == 3 }

        viewModel.bulkAssignUnassigned()
        awaitState { it.unassignedCount == 0 }

        assertEquals(SceneRole.PRIMARY, registry.byName("Cam 1")?.role) // untouched
        assertEquals(SceneRole.SECONDARY, registry.byName("Screen")?.role)
        assertEquals(SceneRole.SECONDARY, registry.byName("Quiet A")?.role)
    }

    @Test
    fun exportImportRoundTripsThroughViewModel(): Unit = runBlocking {
        registry.update("Cam 1", SceneRole.PRIMARY, 0xFF7E57C2, "CAMERA")
        registry.update("Quiet A", SceneRole.ARCHIVE, null, null)

        val yaml = viewModel.transfer.encode()
        assertTrue(yaml.contains("Cam 1"))

        registry.remove("Cam 1")
        registry.remove("Quiet A")
        viewModel.transfer.stage(yaml)
        viewModel.importPreview.first { it != null }
        viewModel.transfer.confirmImport()
        withTimeout(5_000) {
            while (registry.byName("Cam 1")?.role != SceneRole.PRIMARY) {
                kotlinx.coroutines.delay(25)
            }
        }

        assertEquals(SceneRole.PRIMARY, registry.byName("Cam 1")?.role)
        assertEquals(0xFF7E57C2, registry.byName("Cam 1")?.accentColorArgb)
        assertEquals("CAMERA", registry.byName("Cam 1")?.iconName)
        assertEquals(SceneRole.ARCHIVE, registry.byName("Quiet A")?.role)
    }

    @Test
    fun malformedImportEmitsFailureMessage(): Unit = runBlocking {
        val received = async { viewModel.messages.first() }
        yield()

        viewModel.transfer.stage("not: [a registry")

        assertTrue(withTimeout(5_000) { received.await() }.startsWith("Import failed"))
    }

    private suspend fun awaitState(condition: (InventoryUiState) -> Boolean): InventoryUiState =
        withTimeout(5_000) { viewModel.uiState.first(condition) }

    /** Map-backed registry DAO (fixtures can't cross module boundaries). */
    private class FakeRegistryDao : SceneRegistryDao {
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
            entities.update { current -> current.filterKeys { it in validSceneNames } }
        }

        override suspend fun delete(sceneName: String) {
            entities.update { it - sceneName }
        }
    }

    /** Slim scene-list-only fake (fixtures can't cross module boundaries). */
    @Suppress("TooManyFunctions")
    private class FakeObsClient : ObsClient {
        private val _connectionState =
            MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val events: SharedFlow<ObsEvent> = MutableSharedFlow(extraBufferCapacity = 4)
        override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

        fun setReady() {
            _connectionState.value =
                ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "test"))
        }

        override suspend fun connect(host: String, port: Int, password: String?) = Unit

        override suspend fun disconnect() = Unit

        override suspend fun getSceneList(): SceneListSnapshot =
            SceneListSnapshot(
                currentProgramScene = "Cam 1",
                scenes =
                    listOf(
                        SceneSummary("Cam 1", 0),
                        SceneSummary("Screen", 1),
                        SceneSummary("Quiet A", 2),
                    ),
            )

        override suspend fun getStudioModeEnabled(): Boolean = false

        override suspend fun setStudioModeEnabled(enabled: Boolean) = Unit

        override suspend fun getCurrentPreviewScene(): String = unused()

        override suspend fun setCurrentPreviewScene(sceneName: String) = unused()

        override suspend fun triggerStudioModeTransition() = unused()

        override suspend fun getSceneTransitionList() = unused()

        override suspend fun getCurrentSceneTransition() = unused()

        override suspend fun setCurrentSceneTransition(transitionName: String) = unused()

        override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = unused()

        override suspend fun getSourceScreenshot(request: ScreenshotRequest): ByteArray = unused()

        private fun unused(): Nothing = throw NotImplementedError("not needed by these tests")

        override suspend fun getVersion() = unused()

        override suspend fun getStats() = unused()

        override suspend fun getCurrentProgramScene(): String = unused()

        override suspend fun setCurrentProgramScene(sceneName: String) = unused()

        override suspend fun getSceneItemList(
            sceneName: String
        ): List<com.scenedeck.android.core.model.SceneItemInfo> = emptyList()

        override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = unused()

        override suspend fun getSpecialInputs() = com.scenedeck.android.core.model.SpecialInputs()

        override suspend fun getInputMute(inputName: String) = unused()

        override suspend fun setInputMute(inputName: String, muted: Boolean) = unused()

        override suspend fun getInputVolume(inputName: String) = unused()

        override suspend fun setInputVolume(inputName: String, volumeMul: Double) = unused()

        override suspend fun getStreamStatus() = unused()

        override suspend fun startStream() = unused()

        override suspend fun stopStream() = unused()

        override suspend fun getRecordStatus() = unused()

        override suspend fun startRecord() = unused()

        override suspend fun stopRecord() = unused()

        override suspend fun getProfileList() = unused()

        override suspend fun setCurrentProfile(profileName: String) = unused()

        override suspend fun getSceneCollectionList() = unused()

        override suspend fun setCurrentSceneCollection(collectionName: String) = unused()
    }
}
