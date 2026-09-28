package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class RequestsTest : ObsClientTestBase() {

    @Test
    fun sceneRequests_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val list = client.getSceneList()
            assertEquals("Scene A", list.currentProgramScene)
            assertEquals(
                listOf(SceneSummary("Scene A", 0), SceneSummary("Scene B", 1)),
                list.scenes,
            )
            assertEquals("Scene A", client.getCurrentProgramScene())

            client.setCurrentProgramScene("Scene B")
            assertEquals(1, server.receivedCount("SetCurrentProgramScene"))

            val items = client.getSceneItemList("Scene A")
            assertEquals(2, items.size)
            assertEquals(
                SceneItemInfo(
                    id = 3,
                    index = 0,
                    sourceName = "Mic/Aux",
                    enabled = true,
                    isGroup = false,
                    inputKind = "pulse_input_capture",
                ),
                items[0],
            )
            assertFalse(items[1].enabled)
            assertTrue(client.getSceneItemEnabled("Scene A", 3))

            client.disconnect()
        }
    }

    @Test
    fun audioRequests_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val special = client.getSpecialInputs()
            assertEquals(listOf("Desktop Audio", "Mic/Aux", "Mic/Aux 2"), special.names)

            assertTrue(client.getInputMute("Mic/Aux"))
            client.setInputMute("Mic/Aux", false)
            assertEquals(1, server.receivedCount("SetInputMute"))

            assertEquals(0.75, client.getInputVolume("Mic/Aux"), 1e-9)
            client.setInputVolume("Mic/Aux", 0.5)
            assertEquals(1, server.receivedCount("SetInputVolume"))

            client.disconnect()
        }
    }

    @Test
    fun outputRequests_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val stream = client.getStreamStatus()
            assertTrue(stream.active)
            assertEquals(0.12, stream.congestion, 1e-9)
            assertEquals(12345678L, stream.bytes)

            client.startStream()
            client.stopStream()
            assertEquals(1, server.receivedCount("StartStream"))
            assertEquals(1, server.receivedCount("StopStream"))

            val record = client.getRecordStatus()
            assertFalse(record.active)
            client.startRecord()
            client.stopRecord()
            assertEquals(1, server.receivedCount("StartRecord"))
            assertEquals(1, server.receivedCount("StopRecord"))

            client.disconnect()
        }
    }

    @Test
    fun configRequests_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val profiles = client.getProfileList()
            assertEquals("Main", profiles.currentProfile)
            assertEquals(listOf("Main", "Studio"), profiles.profiles)
            client.setCurrentProfile("Studio")
            assertEquals(1, server.receivedCount("SetCurrentProfile"))

            val collections = client.getSceneCollectionList()
            assertEquals("Default", collections.currentCollection)
            assertEquals(listOf("Default", "Test Rig"), collections.collections)
            client.setCurrentSceneCollection("Test Rig")
            assertEquals(1, server.receivedCount("SetCurrentSceneCollection"))

            client.disconnect()
        }
    }

    @Test
    fun statsRequest_mapsFields(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val stats = client.getStats()
            assertEquals(59.94, stats.activeFps, 1e-9)
            assertEquals(2, stats.renderSkippedFrames)
            assertEquals(1, stats.outputSkippedFrames)
            assertEquals(1.234, stats.averageFrameRenderTimeMs, 1e-9)

            client.disconnect()
        }
    }
}
