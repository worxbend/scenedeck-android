package com.scenedeck.android.feature.mixer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MixerViewModelTest {

    private lateinit var client: FakeObsClient
    private lateinit var settings: SettingsRepository
    private lateinit var mixer: MixerRepository
    private lateinit var viewModel: MixerViewModel
    private lateinit var holderScope: CoroutineScope

    @Before
    fun setUp() {
        client = FakeObsClient(
            sceneListSnapshot = SceneListSnapshot(
                currentProgramScene = "Cam 1",
                scenes = listOf(SceneSummary("Cam 1", 0), SceneSummary("Quiet A", 1)),
            ),
        )
        client.sceneItems = mapOf(
            "Cam 1" to listOf(
                SceneItemInfo(1, 0, "Test Tone 440", enabled = true, isGroup = false, inputKind = "ffmpeg_source"),
            ),
        )
        settings = SettingsRepository(
            SceneDeckSettingsStore.forTesting(
                PreferenceDataStoreFactory.create(
                    produceFile = { File.createTempFile("mixer_settings_test", ".preferences_pb") },
                ),
            ),
        )
        holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        mixer = MixerRepository(client, settings, holderScope)
        viewModel = MixerViewModel(mixer, settings, client)
    }

    @After
    fun tearDown() {
        holderScope.cancel()
    }

    @Test
    fun defaultsToActiveModeWithSceneInputs(): Unit = runBlocking {
        client.setReady()
        val state = awaitState { it.inputs.isNotEmpty() }
        assertEquals(MixerMode.ACTIVE, state.mode)
        assertEquals("Cam 1", state.displayedScene)
        assertFalse(state.notInProgram)
        assertEquals(
            listOf("Desktop Audio", "Mic/Aux", "Test Tone 440"),
            state.inputs.map { it.name },
        )
    }

    @Test
    fun selectedModeFreezesSceneAndFlagsNotInProgram(): Unit = runBlocking {
        client.setReady()
        awaitState { it.inputs.isNotEmpty() }

        viewModel.setMode(MixerMode.SELECTED)
        awaitState { it.displayedScene != null }
        viewModel.selectScene("Quiet A")

        // Discovery for "Quiet A" completes asynchronously: globals remain only.
        val state = awaitState {
            it.displayedScene == "Quiet A" &&
                it.inputs.map { input -> input.name } == listOf("Desktop Audio", "Mic/Aux")
        }
        assertEquals(MixerMode.SELECTED, state.mode)
        assertTrue(state.notInProgram) // program is still "Cam 1"
        assertEquals(listOf("Desktop Audio", "Mic/Aux"), state.inputs.map { it.name })
    }

    @Test
    fun searchFiltersByName(): Unit = runBlocking {
        client.setReady()
        awaitState { it.inputs.isNotEmpty() }

        viewModel.setSearch("desktop")

        val state = awaitState { it.search == "desktop" }
        assertEquals(listOf("Desktop Audio"), state.inputs.map { it.name })
    }

    @Test
    fun pinnedModeFreezesTheCurrentSet(): Unit = runBlocking {
        client.setReady()
        val before = awaitState { it.inputs.isNotEmpty() }.inputs

        viewModel.setMode(MixerMode.PINNED)

        val state = awaitState { it.mode == MixerMode.PINNED }
        assertEquals(before.map { it.name }, state.inputs.map { it.name })
    }

    private suspend fun awaitState(condition: (MixerUiState) -> Boolean): MixerUiState =
        withTimeout(5_000) { viewModel.uiState.first(condition) }
}
