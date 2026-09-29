package com.scenedeck.android.core.obs

import app.cash.turbine.test
import com.scenedeck.android.core.model.ObsEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StudioModeTest : ObsClientTestBase() {

    @Test
    fun studioModeRequests_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            assertTrue(client.getStudioModeEnabled())
            client.setStudioModeEnabled(true)
            assertEquals(1, server.receivedCount("SetStudioModeEnabled"))

            assertEquals("Screen", client.getCurrentPreviewScene())
            client.setCurrentPreviewScene("Screen")
            assertEquals(1, server.receivedCount("SetCurrentPreviewScene"))

            client.triggerStudioModeTransition()
            assertEquals(1, server.receivedCount("TriggerStudioModeTransition"))

            client.disconnect()
        }
    }

    @Test
    fun transitionRequests_roundTrip(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val list = client.getSceneTransitionList()
            assertEquals("Fade", list.currentName)
            assertEquals(3, list.transitions.size)
            assertEquals("Cut", list.transitions[0].name)
            assertTrue(list.transitions[0].fixed)
            assertEquals(300, list.transitions[1].durationMs)

            val current = client.getCurrentSceneTransition()
            assertEquals("Fade", current.name)
            assertEquals("fade_transition", current.kind)
            assertEquals(300, current.durationMs)
            assertTrue(current.configurable)

            client.setCurrentSceneTransition("Swipe")
            assertEquals(1, server.receivedCount("SetCurrentSceneTransition"))
            client.setCurrentSceneTransitionDuration(600)
            assertEquals(1, server.receivedCount("SetCurrentSceneTransitionDuration"))

            client.disconnect()
        }
    }

    @Test
    fun sourceScreenshot_decodesDataUri(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val bytes = client.getSourceScreenshot("Cam 1")
            assertEquals(1, server.receivedCount("GetSourceScreenshot"))
            // base64 "/9j/4AAQSkZJRgABAQAAAQABAAD/2w==" → JPEG magic + tail.
            assertArrayEquals(
                byteArrayOf(
                    0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xFF.toByte(), 0xDB.toByte(),
                ),
                bytes,
            )

            client.disconnect()
        }
    }

    @Test
    fun studioEvents_dispatch(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = newClient()
            client.events.test {
                client.connect("127.0.0.1", server.port, password = null)

                server.sendEvent(
                    "StudioModeStateChanged",
                    loadObsFixture("events/studio_mode_state_changed.json"),
                )
                assertEquals(ObsEvent.StudioModeStateChanged(enabled = true), awaitItem())

                server.sendEvent(
                    "CurrentPreviewSceneChanged",
                    loadObsFixture("events/current_preview_scene_changed.json"),
                )
                assertEquals(ObsEvent.CurrentPreviewSceneChanged("Screen"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            client.disconnect()
        }
    }
}
