package com.scenedeck.android.core.obs

import app.cash.turbine.test
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PowerFeaturesTest : ObsClientTestBase() {

    @Test
    fun virtualCam_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            assertTrue(client.getVirtualCamStatus())
            assertTrue(client.toggleVirtualCam())
            assertEquals(1, server.receivedCount("ToggleVirtualCam"))

            client.disconnect()
        }
    }

    @Test
    fun mediaControls_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val status = client.getMediaInputStatus("Test Tone 440")
            assertEquals(MediaStateKind.PLAYING, status.state)
            assertEquals(60_000L, status.durationMs)
            assertEquals(31_061L, status.cursorMs)

            client.triggerMediaInputAction("Test Tone 440", MediaActionKind.PAUSE)
            assertEquals(1, server.receivedCount("TriggerMediaInputAction"))

            client.setMediaInputCursor("Test Tone 440", 45_000)
            assertEquals(1, server.receivedCount("SetMediaInputCursor"))

            client.disconnect()
        }
    }

    @Test
    fun audioExtras_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            assertEquals(-0.5, client.getInputAudioBalance("Test Tone 440"), 1e-9)
            client.setInputAudioBalance("Test Tone 440", -0.5)
            assertEquals(1, server.receivedCount("SetInputAudioBalance"))

            assertEquals(120, client.getInputAudioSyncOffset("Test Tone 440"))
            client.setInputAudioSyncOffset("Test Tone 440", 120)
            assertEquals(1, server.receivedCount("SetInputAudioSyncOffset"))

            assertEquals(
                MonitorTypeKind.MONITOR_ONLY,
                client.getInputAudioMonitorType("Test Tone 440"),
            )
            client.setInputAudioMonitorType("Test Tone 440", MonitorTypeKind.MONITOR_ONLY)
            assertEquals(1, server.receivedCount("SetInputAudioMonitorType"))

            client.disconnect()
        }
    }

    @Test
    fun sceneItemEnabled_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            client.setSceneItemEnabled("Starting Soon", 2, false)
            assertEquals(1, server.receivedCount("SetSceneItemEnabled"))

            client.disconnect()
        }
    }

    @Test
    @Suppress("LongMethod") // walks each power event through the dispatch table in one session
    fun powerEvents_dispatch(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = newClient()
            client.events.test {
                client.connect("127.0.0.1", server.port, password = null)

                server.sendEvent(
                    "VirtualcamStateChanged",
                    loadObsFixture("events/virtualcam_state_changed.json"),
                )
                assertEquals(
                    ObsEvent.VirtualcamStateChanged(
                        active = true,
                        state = "OBS_WEBSOCKET_OUTPUT_STARTED",
                    ),
                    awaitItem(),
                )

                server.sendEvent(
                    "MediaInputPlaybackEnded",
                    loadObsFixture("events/media_input_playback_ended.json"),
                )
                assertEquals(ObsEvent.MediaInputPlaybackEnded("Test Tone 440"), awaitItem())

                server.sendEvent(
                    "InputAudioBalanceChanged",
                    loadObsFixture("events/input_audio_balance_changed.json"),
                )
                assertEquals(ObsEvent.InputAudioBalanceChanged("Test Tone 440", -0.5), awaitItem())

                server.sendEvent(
                    "InputAudioSyncOffsetChanged",
                    loadObsFixture("events/input_audio_sync_offset_changed.json"),
                )
                assertEquals(
                    ObsEvent.InputAudioSyncOffsetChanged("Test Tone 440", 120),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            client.disconnect()
        }
    }
}
