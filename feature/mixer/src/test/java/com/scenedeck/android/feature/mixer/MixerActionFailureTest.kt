package com.scenedeck.android.feature.mixer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MixerActionFailureTest {
    private lateinit var client: FakeObsClient
    private lateinit var viewModel: MixerViewModel
    private lateinit var repositoryScope: CoroutineScope

    @Before
    fun setUp() {
        client = FakeObsClient()
        val settings =
            SettingsRepository(
                SceneDeckSettingsStore.forTesting(
                    PreferenceDataStoreFactory.create(
                        produceFile = {
                            File.createTempFile("mixer_failure", ".preferences_pb")
                        }
                    )
                )
            )
        repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        viewModel =
            MixerViewModel(MixerRepository(client, settings, repositoryScope), settings, client)
    }

    @After
    fun tearDown() {
        repositoryScope.cancel()
    }

    @Test
    fun failedMuteShowsSanitizedError(): Unit = runBlocking {
        client.actionFailure = IllegalStateException("password=secret")
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
        viewModel.toggleMute("Desktop Audio", true)
        val message = withTimeout(5_000) { error.await() }
        assertTrue(message.contains("Check the OBS connection"))
        assertFalse(message.contains("secret"))
    }

    @Test
    fun failedFaderCommitShowsError(): Unit = runBlocking {
        client.actionFailure = IllegalStateException("connection lost")
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
        viewModel.onVolumeCommit("Desktop Audio", 0.5)
        assertTrue(withTimeout(5_000) { error.await() }.contains("Check the OBS connection"))
    }

    @Test
    fun failedMonitorChangeShowsError(): Unit = runBlocking {
        client.actionFailure = IllegalStateException("request denied")
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
        viewModel.setAudioMonitorType(
            "Desktop Audio",
            com.scenedeck.android.core.model.MonitorTypeKind.NONE,
        )
        assertTrue(withTimeout(5_000) { error.await() }.contains("Check the OBS connection"))
    }

    @Test
    fun failedMediaActionShowsError(): Unit = runBlocking {
        client.actionFailure = IllegalStateException("request denied")
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
        viewModel.mediaRestart("Test Tone 440")
        assertTrue(withTimeout(5_000) { error.await() }.contains("Check the OBS connection"))
    }
}
