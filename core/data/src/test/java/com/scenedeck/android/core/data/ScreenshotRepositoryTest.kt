package com.scenedeck.android.core.data

import android.graphics.Bitmap
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.obs.ScreenshotRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScreenshotRepositoryTest {

    private lateinit var client: FakeObsClient
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        client =
            FakeObsClient(
                sceneListSnapshot =
                    SceneListSnapshot(
                        currentProgramScene = "Hot",
                        scenes =
                            listOf(
                                SceneSummary("Hot", 0),
                                SceneSummary("Cold A", 1),
                                SceneSummary("Cold B", 2),
                            ),
                    )
            )
        client.screenshotBytes = makeJpeg()
        settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
    }

    @Test
    fun hotScenesRefreshFastColdScenesSlow() = runTest {
        settings.settings
            .first() // Poll cadence starts after asynchronous preferences are available.
        val obsState =
            ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)
        var virtualNow = 0L
        repository.nowMs = { virtualNow }

        client.setReady()
        obsState.deckState.first { it.allScenes.size == 3 }
        // Screen visible = an active collector on thumbnails.
        val collectJob = backgroundScope.launch { repository.thumbnails.collect {} }
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
        val obsState =
            ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)

        backgroundScope.launch { repository.thumbnails.collect {} }
        client.setReady()
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(client.screenshotCalls.isEmpty())
        assertTrue(repository.thumbnails.value.isEmpty())
    }

    @Test
    fun openingAndLeavingScenesStartsAndStopsPolling() = runTest {
        settings.settings.first() // Await asynchronous DataStore initialization.
        val obsState =
            ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)
        client.setReady()
        obsState.deckState.first { it.allScenes.size == 3 }
        runCurrent()
        assertTrue(client.screenshotCalls.isEmpty())

        val collector = backgroundScope.launch { repository.thumbnails.collect {} }
        runCurrent()
        assertEquals(setOf("Hot", "Cold A", "Cold B"), client.screenshotCalls.toSet())

        collector.cancel()
        runCurrent()
        client.screenshotCalls.clear()
        advanceTimeBy(12_000)
        runCurrent()
        assertTrue(client.screenshotCalls.isEmpty())
        assertTrue(repository.thumbnails.value.isEmpty())
    }

    @Test
    fun slowCapturesHaveNoWaitingCoroutineBacklogOrUnrelatedRestarts() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancellations = 0
        val slow =
            object :
                FakeObsClient(
                    sceneListSnapshot =
                        SceneListSnapshot(
                            "Scene0",
                            (0 until 100).map { SceneSummary("Scene$it", it) },
                        )
                ) {
                override suspend fun getSourceScreenshot(request: ScreenshotRequest): ByteArray {
                    screenshotCalls += request.sourceName
                    try {
                        gate.await()
                    } catch (cancelled: CancellationException) {
                        cancellations++
                        throw cancelled
                    }
                    return makeJpeg()
                }
            }
        settings.settings.first()
        val obsState =
            ObsStateRepository(slow, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val pollerJob = SupervisorJob(backgroundScope.coroutineContext[Job])
        val pollerScope = CoroutineScope(backgroundScope.coroutineContext + pollerJob)
        val repository = ScreenshotRepository(slow, settings, obsState, pollerScope)
        repository.nowMs = { testScheduler.currentTime }
        slow.setReady()
        obsState.deckState.first { it.allScenes.size == 100 }
        val collector = backgroundScope.launch { repository.thumbnails.collect {} }
        runCurrent()
        assertEquals(2, slow.screenshotCalls.size)
        val initialJobs = descendantCount(pollerJob)
        assertTrue("No per-scene waiting job fanout", initialJobs < 32)
        settings.setHaptics(false)
        settings.settings.first { !it.haptics }
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, slow.screenshotCalls.size)
        assertEquals(initialJobs, descendantCount(pollerJob))
        assertEquals(0, cancellations)
        collector.cancel()
        runCurrent()
        assertEquals(2, cancellations)
        assertTrue(repository.thumbnails.value.isEmpty())
    }

    @Test
    fun removingScenePrunesCachedFrameAndFutureCaptures() = runTest {
        settings.settings.first()
        val obsState =
            ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)
        repository.nowMs = { testScheduler.currentTime }
        client.setReady()
        obsState.deckState.first { it.allScenes.size == 3 }
        backgroundScope.launch { repository.thumbnails.collect {} }
        runCurrent()
        assertEquals(3, repository.thumbnails.value.size)
        client.emit(ObsEvent.SceneListChanged(listOf(SceneSummary("Hot", 0))))
        obsState.deckState.first { it.allScenes.size == 1 }
        runCurrent()
        assertEquals(setOf("Hot"), repository.thumbnails.value.keys)
        client.screenshotCalls.clear()
        advanceTimeBy(12_000)
        runCurrent()
        assertTrue(client.screenshotCalls.all { it == "Hot" })
    }

    @Test
    @Config(sdk = [34])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun rejectsInvalidOversizedBytesAndPixelDimensions() = runTest {
        val obsState =
            ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)
        assertNull(repository.decodeBitmap(byteArrayOf(1, 2, 3)))
        assertNull(repository.decodeBitmap(ByteArray(2 * 1024 * 1024 + 1)))
        val huge = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        huge.eraseColor(0xFF336699.toInt())
        val bytes =
            java.io
                .ByteArrayOutputStream()
                .apply { huge.compress(Bitmap.CompressFormat.PNG, 100, this) }
                .toByteArray()
        assertNull(repository.decodeBitmap(bytes))
        assertTrue(repository.decodeBitmap(makeJpeg()) != null)
    }

    @Test
    @Config(sdk = [34])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun rejectedCaptureRetainsLastAcceptedFrame() = runTest {
        settings.settings.first()
        val obsState =
            ObsStateRepository(client, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(client, settings, obsState, backgroundScope)
        repository.nowMs = { testScheduler.currentTime }
        client.setReady()
        obsState.deckState.first { it.allScenes.size == 3 }
        backgroundScope.launch { repository.thumbnails.collect {} }
        runCurrent()
        val accepted = repository.thumbnails.value
        assertEquals(3, accepted.size)
        client.screenshotBytes = byteArrayOf(1, 2, 3)
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(accepted, repository.thumbnails.value)
    }

    @Test
    fun slowProgramAndPreviewCapturesDoNotStarveColdScene() = runTest {
        val slow =
            object :
                    FakeObsClient(
                        sceneListSnapshot =
                            SceneListSnapshot(
                                "Program",
                                listOf(
                                    SceneSummary("Program", 0),
                                    SceneSummary("Preview", 1),
                                    SceneSummary("Cold", 2),
                                ),
                            )
                    ) {
                    override suspend fun getSourceScreenshot(
                        request: ScreenshotRequest
                    ): ByteArray {
                        screenshotCalls += request.sourceName
                        delay(3_000)
                        return makeJpeg()
                    }
                }
                .apply {
                    studioModeEnabled = true
                    previewScene = "Preview"
                }
        settings.settings.first()
        val obsState =
            ObsStateRepository(slow, RegistryRepository(FakeRegistryDao()), backgroundScope)
        val repository = ScreenshotRepository(slow, settings, obsState, backgroundScope)
        repository.nowMs = { testScheduler.currentTime }
        slow.setReady()
        obsState.deckState.first { it.previewScene == "Preview" && it.allScenes.size == 3 }
        backgroundScope.launch { repository.thumbnails.collect {} }
        advanceTimeBy(6_001)
        runCurrent()
        assertTrue(slow.screenshotCalls.toSet().containsAll(setOf("Program", "Preview", "Cold")))
    }

    private fun makeJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
        return stream.toByteArray()
    }

    private fun descendantCount(job: Job): Int = job.children.sumOf { 1 + descendantCount(it) }

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
}
