package com.scenedeck.android.feature.doctor

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
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.ObsRequestTimeoutException
import com.scenedeck.android.core.obs.ScreenshotRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorViewModelTest {

    @Before
    fun setUpMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun stalePrimaryEntryIsReportedInsteadOfAbortingTheRun(): Unit = runBlocking {
        val dao = FakeRegistryDao()
        dao.upsert(SceneRegistryEntity("Camera", SceneRole.PRIMARY.name))
        dao.upsert(SceneRegistryEntity("Ghost", SceneRole.PRIMARY.name))
        val client = FakeObsClient().apply { setReady() }

        val viewModel = DoctorViewModel(RegistryRepository(dao), client)
        val state = withTimeout(5_000) { viewModel.uiState.first { it.ranOnce } }

        assertNull(state.error)
        assertFalse(client.itemListRequests.contains("Ghost"))
        val checks = state.issues.map { it.checkId to it.sceneName }
        assertTrue(checks.contains("stale-entry" to "Ghost"))
        // The video-only color source is not an audio source, so nothing is broken.
        assertTrue(state.issues.none { it.checkId == "broken-audio" })
        // Camera → Lower Third: Lower Third is only implicitly Primary, so no inversion.
        assertTrue(state.issues.none { it.checkId == "hierarchy-inversion" })
    }

    @Test
    fun failedRunShowsSanitizedErrorAndDropsPreviousResults(): Unit = runBlocking {
        val dao = FakeRegistryDao()
        dao.upsert(SceneRegistryEntity("Ghost", SceneRole.PRIMARY.name))
        val client = FakeObsClient().apply { setReady() }
        val viewModel = DoctorViewModel(RegistryRepository(dao), client)
        val first = withTimeout(5_000) { viewModel.uiState.first { it.ranOnce } }
        assertTrue(first.issues.isNotEmpty())

        client.sceneListFailure = IOException("socket 10.0.0.2:4455 reset by peer")
        viewModel.refresh()
        val failed = withTimeout(5_000) { viewModel.uiState.first { it.error != null } }

        assertEquals(DOCTOR_FAILED_MESSAGE, failed.error)
        assertFalse(failed.error.orEmpty().contains("10.0.0.2"))
        assertTrue(failed.issues.isEmpty())
        assertFalse(failed.ranOnce)
        assertFalse(failed.running)

        client.sceneListFailure = null
        viewModel.refresh()
        val recovered =
            withTimeout(5_000) { viewModel.uiState.first { it.ranOnce && it.error == null } }
        assertTrue(recovered.issues.isNotEmpty())
    }

    @Test
    fun failureMessagesNeverCarryExceptionDetails() {
        assertEquals(DOCTOR_TIMEOUT_MESSAGE, doctorFailureMessage(ObsRequestTimeoutException()))
        assertEquals(
            DOCTOR_FAILED_MESSAGE,
            doctorFailureMessage(
                ObsRequestFailedException("GetSceneList", "RequestProcessingFailed", "private")
            ),
        )
    }

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

    /** OBS fixture: Camera nests Lower Third and a video-only color source plus a mic. */
    @Test
    fun graphWalkTimeoutFailsTheRunInsteadOfReportingAllClear(): Unit = runBlocking {
        val client = FakeObsClient().apply { setReady() }
        client.sceneItemFailure = ObsRequestTimeoutException()
        val viewModel = DoctorViewModel(RegistryRepository(FakeRegistryDao()), client)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.error != null } }

        assertEquals(DOCTOR_TIMEOUT_MESSAGE, state.error)
        assertFalse(state.ranOnce)
        assertTrue(state.issues.isEmpty())
    }

    @Suppress("TooManyFunctions")
    private class FakeObsClient : ObsClient {
        @Volatile var sceneListFailure: Exception? = null
        @Volatile var sceneItemFailure: Exception? = null
        val itemListRequests: MutableList<String> =
            java.util.Collections.synchronizedList(mutableListOf())
        private val _connectionState =
            MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val events = MutableSharedFlow<ObsEvent>(extraBufferCapacity = 4)
        override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

        fun setReady() {
            _connectionState.value =
                ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "test"))
        }

        override suspend fun connect(host: String, port: Int, password: String?) = Unit

        override suspend fun disconnect() = Unit

        override suspend fun getSceneList(): SceneListSnapshot {
            sceneListFailure?.let { throw it }
            return SceneListSnapshot(
                currentProgramScene = "Camera",
                scenes = listOf(SceneSummary("Camera", 0), SceneSummary("Lower Third", 1)),
            )
        }

        override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> {
            itemListRequests += sceneName
            sceneItemFailure?.let { throw it }
            return when (sceneName) {
                "Camera" ->
                    listOf(
                        SceneItemInfo(1, 0, "Lower Third", true, false, null),
                        SceneItemInfo(2, 1, "Camera BG", true, false, "color_source_v3"),
                        SceneItemInfo(3, 2, "Mic", true, false, "pulse_input_capture"),
                    )
                "Lower Third" -> emptyList()
                else ->
                    throw ObsRequestFailedException("GetSceneItemList", "ResourceNotFound", null)
            }
        }

        override suspend fun getInputVolume(inputName: String): Double =
            if (inputName == "Camera BG") {
                throw ObsRequestFailedException("GetInputVolume", "InvalidResourceState", null)
            } else {
                1.0
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

        override suspend fun getSourceScreenshot(request: ScreenshotRequest): ByteArray = unused()

        private fun unused(): Nothing = throw NotImplementedError("not needed by these tests")

        override suspend fun getVersion() = unused()

        override suspend fun getStats() = unused()

        override suspend fun getCurrentProgramScene(): String = unused()

        override suspend fun setCurrentProgramScene(sceneName: String) = unused()

        override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = unused()

        override suspend fun getSpecialInputs() = unused()

        override suspend fun getInputMute(inputName: String) = unused()

        override suspend fun setInputMute(inputName: String, muted: Boolean) = unused()

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
