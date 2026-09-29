package com.scenedeck.android.feature.stats

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
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

class StatsViewModelTest {

    private lateinit var client: FakeObsClient
    private lateinit var repository: StatsRepository
    private lateinit var viewModel: StatsViewModel
    private lateinit var pollScope: CoroutineScope

    @Before
    fun setUp() {
        client = FakeObsClient(
            statsResponse = ObsStats(
                cpuUsage = 12.5,
                memoryUsageMb = 512.0,
                availableDiskSpaceMb = 1_000.0,
                activeFps = 59.94,
                averageFrameRenderTimeMs = 1.2,
                renderSkippedFrames = 2,
                renderTotalFrames = 10_000,
                outputSkippedFrames = 7,
                outputTotalFrames = 9_000,
            ),
            streamStatusResponse = StreamStatus(
                active = true,
                reconnecting = false,
                timecode = "00:00:10.000",
                durationMs = 10_000,
                bytes = 750_000,
                congestion = 0.45,
                skippedFrames = 10,
                totalFrames = 500,
            ),
            recordStatusResponse = RecordStatus(
                active = true,
                paused = true,
                timecode = "00:00:05.000",
                durationMs = 5_000,
                bytes = 1_234,
            ),
        )
        val settings = SettingsRepository(
            SceneDeckSettingsStore.forTesting(
                PreferenceDataStoreFactory.create(
                    produceFile = { File.createTempFile("stats_settings_test", ".preferences_pb") },
                ),
            ),
        )
        pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repository = StatsRepository(client, pollScope)
        viewModel = StatsViewModel(repository, settings)
    }

    @After
    fun tearDown() {
        pollScope.cancel()
    }

    @Test
    fun mapsTelemetryIntoUiState(): Unit = runBlocking {
        client.setReady()
        val state = awaitState { it.sampleCount > 0 }

        assertTrue(state.connection is ConnectionState.Ready)
        assertEquals(59.94f, state.fps, 1e-3f)
        assertEquals(1.2f, state.renderTimeMs, 1e-3f)
        assertEquals(2f, state.droppedPct, 1e-3f) // 10 skipped / 500 total
        assertEquals(45f, state.congestionPct, 1e-3f)
        assertEquals(12.5, state.cpuUsagePct, 1e-6)
        assertEquals(512.0, state.memoryUsageMb, 1e-6)
        assertEquals(10_000, state.renderTotalFrames)
        assertEquals(2, state.renderSkippedFrames)
        assertEquals(9_000, state.outputTotalFrames)
        assertEquals(7, state.outputSkippedFrames)
        assertEquals(750_000L, state.streamBytes)
        assertTrue(state.streamActive)
        assertTrue(state.recordActive)
        assertTrue(state.recordPaused)
    }

    @Test
    fun feedsDrawPhaseHolders(): Unit = runBlocking {
        client.setReady()
        awaitState { it.sampleCount > 0 }

        assertEquals(listOf(59.94f), viewModel.fpsTrend.samples.value)
        assertEquals(listOf(1.2f), viewModel.renderTrend.samples.value)
        assertEquals(listOf(0f), viewModel.frameDrops.renderSkipped.value)
        assertEquals(listOf(0f), viewModel.frameDrops.outputSkipped.value)
    }

    @Test
    fun disconnectResetsStateAndHolders(): Unit = runBlocking {
        client.setReady()
        awaitState { it.sampleCount > 0 }

        client.setDisconnected()
        val state = awaitState {
            it.connection is ConnectionState.Disconnected
        }

        assertEquals(0, state.sampleCount)
        assertFalse(state.streamActive)
        assertTrue(viewModel.fpsTrend.samples.value.isEmpty())
        assertTrue(viewModel.frameDrops.renderSkipped.value.isEmpty())
    }

    @Test
    fun rollingBitrateFlowsFromRepository(): Unit = runBlocking {
        client.setReady()
        val first = awaitState { it.sampleCount > 0 }
        assertEquals(0, first.bitrateKbps) // baseline sample: no delta yet

        // 750_000 bytes/s ≈ 6 000 kbit/s once the next poll sees the delta.
        client.streamStatusResponse = client.streamStatusResponse.copy(bytes = 1_500_000)
        val state = awaitState { it.bitrateKbps > 0 }
        assertTrue(state.bitrateKbps in 5_000..7_000) // wall-clock 1 Hz poll, loose bounds
    }

    private suspend fun awaitState(condition: (StatsUiState) -> Boolean): StatsUiState =
        withTimeout(5_000) { viewModel.uiState.first(condition) }
}
