package com.scenedeck.android.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.data.AudioInputCatalog
import com.scenedeck.android.core.data.MeterRefresh
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.designsystem.theme.UiDensity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLayoutAudioTest {

    // viewModelScope needs a Main dispatcher on the plain JVM; without one the launched writes
    // depend on whatever an earlier test left installed.
    @Before
    fun setUpMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private val repository =
        SettingsRepository(
            SceneDeckSettingsStore.forTesting(
                PreferenceDataStoreFactory.create(
                    produceFile = { File.createTempFile("settings_layout", ".preferences_pb") }
                )
            )
        )

    private fun viewModel(ready: Boolean = true) =
        SettingsViewModel(repository, AudioInputCatalog(audioObsClient(ready)))

    @Test
    fun densityAndMeterRefreshPersist(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.setDensity(UiDensity.COMPACT)
        viewModel.setMeterRefresh(MeterRefresh.OFF)

        val saved =
            withTimeout(WRITE_TIMEOUT_MS) {
                repository.settings.first {
                    it.uiDensity == "COMPACT" && it.meterRefresh == MeterRefresh.OFF
                }
            }
        assertEquals("COMPACT", saved.uiDensity)
        val state =
            withTimeout(WRITE_TIMEOUT_MS) {
                viewModel.layoutAndAudio.first { it.density == UiDensity.COMPACT }
            }
        assertEquals(MeterRefresh.OFF, state.meterRefresh)
    }

    @Test
    fun allowListTogglesAreAtomicAndClearable(): Unit = runBlocking {
        val viewModel = viewModel()
        listOf("Mic", "Desktop Audio", "Music").forEach { viewModel.setAudioInputAllowed(it, true) }
        withTimeout(WRITE_TIMEOUT_MS) { repository.settings.first { it.audioAllowList.size == 3 } }

        viewModel.setAudioInputAllowed("Music", false)
        val afterRemove =
            withTimeout(WRITE_TIMEOUT_MS) {
                repository.settings.first { it.audioAllowList.size == 2 }
            }
        assertEquals(setOf("Mic", "Desktop Audio"), afterRemove.audioAllowList)

        viewModel.clearAudioAllowList()
        withTimeout(WRITE_TIMEOUT_MS) { repository.settings.first { it.audioAllowList.isEmpty() } }
    }

    @Test
    fun choicesMergeObsInputsWithSavedNames(): Unit = runBlocking {
        repository.setAudioAllowList(setOf("Old Mic"))
        val viewModel = viewModel()

        viewModel.loadAudioInputChoices()
        val choices =
            withTimeout(WRITE_TIMEOUT_MS) { viewModel.audioInputChoices.first { !it.loading } }

        assertEquals(listOf("Desktop Audio", "Mic", "Old Mic"), choices.names)
    }

    @Test
    fun disconnectedChoicesKeepSavedNamesOnly(): Unit = runBlocking {
        repository.setAudioAllowList(setOf("Old Mic"))
        val viewModel = viewModel(ready = false)

        viewModel.loadAudioInputChoices()
        val choices =
            withTimeout(WRITE_TIMEOUT_MS) { viewModel.audioInputChoices.first { !it.loading } }

        assertEquals(listOf("Old Mic"), choices.names)
    }

    private companion object {
        // Real DataStore file writes; generous so a CPU-starved parallel quality gate (lint and
        // many test JVMs at once) cannot time out a correct write.
        const val WRITE_TIMEOUT_MS = 20_000L
    }
}
