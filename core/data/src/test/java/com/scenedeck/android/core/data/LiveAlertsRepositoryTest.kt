package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.VolumeMeterReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveAlertsRepositoryTest {
    @Test
    fun sharesPollingClearsOnNewCommandsAndCancelsAllConsumers(): Unit = runTest {
        val meters = MutableSharedFlow<List<VolumeMeterReading>>()
        var reads = 0
        val client =
            object : FakeObsClient() {
                override val volumeMeters = meters

                override suspend fun getStats(): ObsStats {
                    reads++
                    return super.getStats().copy(averageFrameRenderTimeMs = 30.0)
                }
            }
        client.streamStatusResponse = StreamStatus(true, false, "", 1_000, 0, 0.0, 0, 100)
        client.setReady()
        val database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        val scope =
            CoroutineScope(
                backgroundScope.coroutineContext +
                    SupervisorJob(backgroundScope.coroutineContext[Job])
            )
        try {
            val workspaces = MutableWorkspaceContext().apply { unavailable() }
            val active = ActiveConnection().apply { confirm(begin(7)) }
            val clock = MonotonicClock { testScheduler.currentTime }
            val stats = StatsRepository(client, scope, clock)
            val alerts =
                LiveAlertsRepository(
                    client,
                    stats,
                    active,
                    PreflightRepository(database, workspaces),
                    clock,
                    scope,
                )
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(7, reads) // One shared 1 Hz poller, including its initial read.
            assertTrue(alerts.state.value.alerts.any { it.kind == LiveAlertKind.RENDER_LOAD })
            assertEquals(1, meters.subscriptionCount.value)
            assertFalse(alerts.armSpeech(SpeechTarget(TEST_WORKSPACE, "Mic")))
            active.begin(8) // Invalidates ownership before any network suspension/change.
            runCurrent()
            assertTrue(alerts.state.value.alerts.isEmpty())
            alerts.setHaptics(true)
            assertTrue(alerts.state.value.hapticsEnabled)
            scope.cancel()
            runCurrent()
            assertEquals(0, meters.subscriptionCount.value)
            val lastReads = reads
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(lastReads, reads)
        } finally {
            scope.cancel()
            database.close()
        }
    }
}
