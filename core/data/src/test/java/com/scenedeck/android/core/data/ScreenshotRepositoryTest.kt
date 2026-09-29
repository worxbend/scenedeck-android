package com.scenedeck.android.core.data

import android.graphics.Bitmap
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScreenshotRepositoryTest {

    private lateinit var client: FakeObsClient
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        client = FakeObsClient(
            sceneListSnapshot = SceneListSnapshot(
                currentProgramScene = "Hot",
                scenes = listOf(
                    SceneSummary("Hot", 0),
                    SceneSummary("Cold A", 1),
                    SceneSummary("Cold B", 2),
                ),
            ),
        )
        client.screenshotBytes = makeJpeg()
        settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
    }

    @Test
    fun hotScenesRefreshFastColdScenesSlow() = runTest {
        val obsState = ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)
        var virtualNow = 0L
        repository.nowMs = { virtualNow }

        // Screen visible = an active collector on thumbnails.
        val collectJob = backgroundScope.launch { repository.thumbnails.collect { } }
        client.setReady()
        runCurrent()

        // First tick: all three scenes captured immediately.
        assertEquals(
            setOf("Hot", "Cold A", "Cold B"),
            client.screenshotCalls.toSet(),
        )

        // +3 s (past hot interval, below cold): only Hot refetches.
        client.screenshotCalls.clear()
        virtualNow += 3_000
        advanceTimeBy(3_000)
        assertEquals(listOf("Hot"), client.screenshotCalls.distinct())

        // +10 s total since start: cold scenes refetch too.
        client.screenshotCalls.clear()
        virtualNow += 7_000
        advanceTimeBy(7_000)
        assertTrue(client.screenshotCalls.toSet().containsAll(setOf("Hot", "Cold A", "Cold B")))

        collectJob.cancel()
    }

    @Test
    fun disabledSettingStopsPolling() = runTest {
        settings.setScenePreviewsEnabled(false)
        val obsState = ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)

        backgroundScope.launch { repository.thumbnails.collect { } }
        client.setReady()
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(client.screenshotCalls.isEmpty())
        assertTrue(repository.thumbnails.value.isEmpty())
    }

    private fun makeJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
        return stream.toByteArray()
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
}
