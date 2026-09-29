package com.scenedeck.android.feature.graph

import com.scenedeck.android.core.data.EdgeVerdict
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphViewModelTest {

    @Test
    fun buildsGraphWithRoleRuleVerdicts(): Unit = runBlocking {
        val registryDao = FakeRegistryDao()
        registryDao.upsert(SceneRegistryEntity("Show", SceneRole.PRIMARY.name))
        registryDao.upsert(SceneRegistryEntity("LowerThird", SceneRole.MODULE.name))
        registryDao.upsert(SceneRegistryEntity("Scratch", SceneRole.DEBUG.name))

        val client = FakeObsClient()
        client.setReady()
        val viewModel = GraphViewModel(RegistryRepository(registryDao), client)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.graph != null } }
        val graph = state.graph!!

        assertEquals(
            setOf("Show", "Cam 1", "LowerThird", "Scratch"),
            graph.nodes.map { it.name }.toSet(),
        )
        assertEquals(
            EdgeVerdict.OK,
            graph.edges.single { it.from == "Show" && it.to == "LowerThird" }.verdict,
        )
        assertEquals(
            EdgeVerdict.FORBIDDEN,
            graph.edges.single { it.from == "Show" && it.to == "Scratch" }.verdict,
        )
        assertTrue(graph.cycleMembers.isEmpty())
    }

    @Test
    fun nodeDetailsExposeParentsAndChildren(): Unit = runBlocking {
        val client = FakeObsClient()
        client.setReady()
        val viewModel = GraphViewModel(RegistryRepository(FakeRegistryDao()), client)
        withTimeout(5_000) { viewModel.uiState.first { it.graph != null } }

        assertEquals(listOf("LowerThird", "Scratch"), viewModel.childrenOf("Show"))
        assertEquals(listOf("Show"), viewModel.parentsOf("LowerThird"))
        assertEquals(emptyList<String>(), viewModel.parentsOf("Show"))
    }

    private class FakeRegistryDao : SceneRegistryDao {
        private val entities = MutableStateFlow<Map<String, SceneRegistryEntity>>(emptyMap())

        override fun observeAll(): Flow<List<SceneRegistryEntity>> =
            entities.map { map -> map.values.sortedWith(compareBy({ it.sortOrder }, { it.sceneName })) }

        override suspend fun byName(sceneName: String): SceneRegistryEntity? = entities.value[sceneName]

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

    @Suppress("TooManyFunctions")
    private class FakeObsClient : ObsClient {
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val events: SharedFlow<ObsEvent> = MutableSharedFlow(extraBufferCapacity = 4)
        override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

        fun setReady() {
            _connectionState.value = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "test"))
        }

        override suspend fun connect(host: String, port: Int, password: String?) = Unit
        override suspend fun disconnect() = Unit

        override suspend fun getSceneList(): SceneListSnapshot = SceneListSnapshot(
            currentProgramScene = "Show",
            scenes = listOf(
                SceneSummary("Show", 0),
                SceneSummary("Cam 1", 1),
                SceneSummary("LowerThird", 2),
                SceneSummary("Scratch", 3),
            ),
        )

        override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> = when (sceneName) {
            "Show" -> listOf(
                SceneItemInfo(1, 0, "LowerThird", enabled = true, isGroup = false, inputKind = null),
                SceneItemInfo(2, 1, "Scratch", enabled = true, isGroup = false, inputKind = null),
            )

            "Cam 1" -> listOf(
                SceneItemInfo(1, 0, "Tone", enabled = true, isGroup = false, inputKind = "ffmpeg_source"),
            )

            else -> emptyList()
        }

    
    override suspend fun getStudioModeEnabled(): Boolean = false
    override suspend fun setStudioModeEnabled(enabled: Boolean) = Unit
    override suspend fun getCurrentPreviewScene(): String = unused()
    override suspend fun setCurrentPreviewScene(sceneName: String) = unused()
    override suspend fun triggerStudioModeTransition() = unused()
    override suspend fun getSceneTransitionList() = unused()
    override suspend fun getCurrentSceneTransition() = unused()
    override suspend fun setCurrentSceneTransition(transitionName: String) = unused()
    override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = unused()
    override suspend fun getSourceScreenshot(
        sourceName: String,
        format: String,
        compressionQuality: Int,
        width: Int?,
        height: Int?,
    ): ByteArray = unused()

    private fun unused(): Nothing = throw NotImplementedError("not needed by these tests")

        override suspend fun getVersion() = unused()
        override suspend fun getStats() = unused()
        override suspend fun getCurrentProgramScene(): String = unused()
        override suspend fun setCurrentProgramScene(sceneName: String) = unused()
        override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = unused()
        override suspend fun getSpecialInputs() = unused()
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
