package com.scenedeck.android.core.obs

import app.cash.turbine.test
import com.scenedeck.android.core.model.ChannelLevels
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.VolumeMeterReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val METER_INTENT = 1 shl 16

internal class EventsTest : ObsClientTestBase() {

    @Test
    fun programSceneChanged_dispatches(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = newClient()
            client.events.test {
                client.connect("127.0.0.1", server.port, password = null)

                server.sendEvent(
                    "CurrentProgramSceneChanged",
                    loadObsFixture("events/current_program_scene_changed.json"),
                )
                assertEquals(ObsEvent.CurrentProgramSceneChanged("Scene B"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            client.disconnect()
        }
    }

    @Test
    fun outputAndMuteEvents_dispatch(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = newClient()
            client.events.test {
                client.connect("127.0.0.1", server.port, password = null)

                server.sendEvent(
                    "StreamStateChanged",
                    loadObsFixture("events/stream_state_changed.json"),
                )
                assertEquals(
                    ObsEvent.StreamStateChanged(active = true, state = "OBS_WEBSOCKET_OUTPUT_STARTED"),
                    awaitItem(),
                )

                server.sendEvent(
                    "InputMuteStateChanged",
                    loadObsFixture("events/input_mute_state_changed.json"),
                )
                assertEquals(ObsEvent.InputMuteStateChanged("Mic/Aux", muted = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            client.disconnect()
        }
    }

    @Test
    fun volumeMeters_parsesChannels(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = newClient()
            client.volumeMeters.test {
                client.connect("127.0.0.1", server.port, password = null)

                server.sendEvent(
                    "InputVolumeMeters",
                    loadObsFixture("events/input_volume_meters.json"),
                    intent = METER_INTENT,
                )
                val batch = awaitItem()
                assertEquals(
                    listOf(
                        VolumeMeterReading(
                            inputName = "Mic/Aux",
                            channels = listOf(
                                ChannelLevels(0.5f, 0.6f, 0.55f),
                                ChannelLevels(0.4f, 0.45f, 0.42f),
                            ),
                        ),
                    ),
                    batch,
                )
                cancelAndIgnoreRemainingEvents()
            }
            client.disconnect()
        }
    }

    @Test
    fun meterFlood_dropsOldestNeverBlocks(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = newClient()
            client.connect("127.0.0.1", server.port, password = null)

            // Slow collector — Turbine would drain eagerly and hide the drop policy.
            val receivedList = java.util.concurrent.CopyOnWriteArrayList<List<VolumeMeterReading>>()
            val collectJob = scope.launch {
                client.volumeMeters.collect {
                    receivedList += it
                    delay(5)
                }
            }

            val total = 1000
            val lastName = "Meter${total - 1}"
            repeat(total) { i ->
                server.sendEvent(
                    "InputVolumeMeters",
                    """{"inputs":[{"inputName":"Meter$i","inputLevelsMul":[[0.1,0.2,0.15]]}]}""",
                    intent = METER_INTENT,
                )
            }

            // The newest batch is never dropped: it must arrive even after the flood.
            withTimeout(15_000) {
                while (receivedList.lastOrNull()?.firstOrNull()?.inputName != lastName) delay(20)
            }
            collectJob.cancel()

            assertTrue(
                "expected oldest meter batches to be dropped under flood, got ${receivedList.size}/$total",
                receivedList.size < total,
            )
            client.disconnect()
        }
    }
}
