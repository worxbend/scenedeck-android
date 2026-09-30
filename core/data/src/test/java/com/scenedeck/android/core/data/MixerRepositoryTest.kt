package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.SpecialInputs
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MixerRepositoryTest {

    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
    }

    @Test
    fun mediaPollingRunsOnlyWhileSubscribed() = runTest {
        val client = MediaClient()
        val repository = MixerRepository(client, settings, backgroundScope)
        client.setReady()
        repository.mixerState.first { state -> state.inputs.any { it.name == "Music" } }
        runCurrent()

        // Connected but no collector: no polling at all.
        assertTrue(client.mediaStatusCalls.isEmpty())

        val collector = backgroundScope.launch { repository.mediaStatus.collect {} }
        runCurrent()
        assertEquals(listOf("Music"), client.mediaStatusCalls)

        // Subscribed: the ~2 s cadence keeps refreshing.
        advanceTimeBy(2_001) // MEDIA_POLL_MS + 1
        runCurrent()
        assertTrue(client.mediaStatusCalls.size >= 2)

        collector.cancel()
        runCurrent()
        client.mediaStatusCalls.clear()
        advanceTimeBy(8_000) // 4 poll cadences
        runCurrent()
        assertTrue(client.mediaStatusCalls.isEmpty())
        assertTrue(repository.mediaStatus.value.isEmpty())
    }

    @Test
    fun mediaStatusPrunesInputsRemovedMidSession() = runTest {
        val client = MediaClient()
        val repository = MixerRepository(client, settings, backgroundScope)
        client.setReady()
        repository.mixerState.first { state -> state.inputs.any { it.name == "Music" } }
        val collector = backgroundScope.launch { repository.mediaStatus.collect {} }
        runCurrent()
        assertEquals(setOf("Music"), repository.mediaStatus.value.keys)

        // OBS removes the input; discovery re-runs and the next poll tick prunes it.
        client.sceneItems = emptyList()
        client.emit(ObsEvent.InputRemoved("Music"))
        // Await the re-discovery itself (its settings read runs on a real IO dispatcher,
        // so runCurrent alone can't guarantee it has finished).
        repository.mixerState.first { s -> s.inputs.none { it.name == "Music" } }
        advanceTimeBy(2_001) // MEDIA_POLL_MS + 1
        runCurrent()
        assertTrue(repository.mediaStatus.value.isEmpty())

        collector.cancel()
    }

    @Test
    fun failedVolumeWriteRollsBackOptimisticPatch() = runTest {
        val client =
            object : MediaClient() {
                override suspend fun setInputVolume(inputName: String, volumeMul: Double) =
                    throw IOException("socket closed")
            }
        val repository = MixerRepository(client, settings, backgroundScope)
        client.setReady()
        val state = repository.mixerState.first { s -> s.inputs.any { it.name == "Music" } }
        assertEquals(1.0, state.inputs.single { it.name == "Music" }.volumeMul, 0.0)

        try {
            repository.setInputVolume("Music", 0.25)
            error("Expected the OBS failure to propagate")
        } catch (expected: IOException) {
            // expected: caller sees the failure
        }

        val reverted =
            repository.mixerState.first { s ->
                s.inputs.single { it.name == "Music" }.volumeMul == 1.0
            }
        assertEquals(1.0, reverted.inputs.single { it.name == "Music" }.volumeMul, 0.0)
    }

    @Test
    fun delayedFailedVolumeWriteCannotUndoNewerSuccessfulWrite() = runTest {
        val failureGate = CompletableDeferred<Unit>()
        val client =
            object : MediaClient() {
                override suspend fun setInputVolume(inputName: String, volumeMul: Double) {
                    if (volumeMul == 0.25) {
                        failureGate.await()
                        throw IOException("old write failed")
                    }
                }
            }
        val repository = MixerRepository(client, settings, backgroundScope)
        client.setReady()
        repository.mixerState.first { it.inputs.any { input -> input.name == "Music" } }
        val older = async { runCatching { repository.setInputVolume("Music", 0.25) } }
        runCurrent()
        repository.setInputVolume("Music", 0.75)
        failureGate.complete(Unit)
        assertTrue(older.await().isFailure)
        runCurrent()
        assertEquals(
            0.75,
            repository.mixerState.value.inputs.single { it.name == "Music" }.volumeMul,
            0.0,
        )
    }

    @Test
    fun failedMuteWriteRollsBackOptimisticPatch() = runTest {
        val client =
            object : MediaClient() {
                override suspend fun setInputMute(inputName: String, muted: Boolean) =
                    throw IOException("socket closed")
            }
        val repository = MixerRepository(client, settings, backgroundScope)
        client.setReady()
        repository.mixerState.first { s -> s.inputs.any { it.name == "Music" } }

        try {
            repository.setInputMute("Music", true)
            error("Expected the OBS failure to propagate")
        } catch (expected: IOException) {
            // expected: caller sees the failure
        }

        val reverted =
            repository.mixerState.first { s -> !s.inputs.single { it.name == "Music" }.muted }
        assertTrue(!reverted.inputs.single { it.name == "Music" }.muted)
    }

    /** One media-kind input ("Music") in the single program scene. */
    private open class MediaClient :
        FakeObsClient(
            sceneListSnapshot =
                SceneListSnapshot(
                    currentProgramScene = "Main",
                    scenes = listOf(SceneSummary("Main", 0)),
                )
        ) {
        var sceneItems: List<SceneItemInfo> =
            listOf(SceneItemInfo(1, 0, "Music", true, false, "ffmpeg_source"))

        val mediaStatusCalls = mutableListOf<String>()

        override suspend fun getSpecialInputs() = SpecialInputs()

        override suspend fun getSceneItemList(sceneName: String) = sceneItems

        override suspend fun getInputVolume(inputName: String) = 1.0

        override suspend fun getInputMute(inputName: String) = false

        override suspend fun getMediaInputStatus(inputName: String): MediaStatus {
            mediaStatusCalls += inputName
            return MediaStatus(MediaStateKind.PLAYING, durationMs = 60_000, cursorMs = 1_000)
        }
    }
}
