package com.scenedeck.android.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.data.AudioInputCatalog
import com.scenedeck.android.core.data.MeterRefresh
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.designsystem.theme.UiDensity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLayoutAudioTest {

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
            withTimeout(5_000) {
                repository.settings.first {
                    it.uiDensity == "COMPACT" && it.meterRefresh == MeterRefresh.OFF
                }
            }
        assertEquals("COMPACT", saved.uiDensity)
        val state =
            withTimeout(5_000) {
                viewModel.layoutAndAudio.first { it.density == UiDensity.COMPACT }
            }
        assertEquals(MeterRefresh.OFF, state.meterRefresh)
    }

    @Test
    fun allowListTogglesAreAtomicAndClearable(): Unit = runBlocking {
        val viewModel = viewModel()
        listOf("Mic", "Desktop Audio", "Music").forEach { viewModel.setAudioInputAllowed(it, true) }
        withTimeout(5_000) { repository.settings.first { it.audioAllowList.size == 3 } }

        viewModel.setAudioInputAllowed("Music", false)
        val afterRemove =
            withTimeout(5_000) { repository.settings.first { it.audioAllowList.size == 2 } }
        assertEquals(setOf("Mic", "Desktop Audio"), afterRemove.audioAllowList)

        viewModel.clearAudioAllowList()
        withTimeout(5_000) { repository.settings.first { it.audioAllowList.isEmpty() } }
    }

    @Test
    fun choicesMergeObsInputsWithSavedNames(): Unit = runBlocking {
        repository.setAudioAllowList(setOf("Old Mic"))
        val viewModel = viewModel()

        viewModel.loadAudioInputChoices()
        val choices = withTimeout(5_000) { viewModel.audioInputChoices.first { !it.loading } }

        assertEquals(listOf("Desktop Audio", "Mic", "Old Mic"), choices.names)
    }

    @Test
    fun disconnectedChoicesKeepSavedNamesOnly(): Unit = runBlocking {
        repository.setAudioAllowList(setOf("Old Mic"))
        val viewModel = viewModel(ready = false)

        viewModel.loadAudioInputChoices()
        val choices = withTimeout(5_000) { viewModel.audioInputChoices.first { !it.loading } }

        assertEquals(listOf("Old Mic"), choices.names)
    }
}
