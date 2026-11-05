package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionMeters
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WorkspaceProtocolTest : ObsClientTestBase() {

    @Test
    fun callerOriginSurvivesSuspensionBeforeItsFirstRequest(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            val original = (client.connectionState.value as ConnectionState.Ready).token
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val action =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching {
                        client.withSession(original) {
                            entered.complete(Unit)
                            release.await()
                            setCurrentProgramScene("Camera")
                        }
                    }
                }
            entered.await()
            server.sendEvent("CurrentSceneCollectionChanged", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first { it is ConnectionState.Ready && it.token != original }
            }
            release.complete(Unit)
            assertTrue(action.await().exceptionOrNull() is ObsWorkspaceChangedException)
            assertEquals(0, server.receivedCount("SetCurrentProgramScene"))
            client.disconnect()
        }
    }

    @Test
    fun metersRetainOriginAndPauseDuringCollectionChange(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            val original = (client.connectionState.value as ConnectionState.Ready).token
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val received = CopyOnWriteArrayList<ObsSessionMeters>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.metersWithContext.collect {
                        entered.complete(Unit)
                        release.await()
                        received += it
                    }
                }
            val meters = loadObsFixture("events/input_volume_meters.json")
            server.sendEvent("InputVolumeMeters", meters)
            withTimeout(5_000) { entered.await() }
            server.sendEvent("CurrentSceneCollectionChanging", COLLECTION_DATA)
            server.sendEvent("InputVolumeMeters", meters)
            server.sendEvent("CurrentSceneCollectionChanged", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first {
                    it is ConnectionState.Ready && it.collectionRevision == 2L
                }
            }
            val replacement = (client.connectionState.value as ConnectionState.Ready).token
            server.sendEvent("InputVolumeMeters", meters)
            release.complete(Unit)
            withTimeout(5_000) { while (received.size < 2) delay(10) }
            assertEquals(listOf(original, replacement), received.take(2).map { it.token })
            collector.cancelAndJoin()
            client.disconnect()
        }
    }

    @Test
    fun collectionChangeDuringInitialVersionReadStillBlocksPolling(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.responseFrame = { request, frame ->
                if (request == "GetVersion") {
                    server.sendEvent("CurrentSceneCollectionChanging", COLLECTION_DATA)
                }
                frame
            }
            val client = connectedClient(server)
            val ready = client.connectionState.value as ConnectionState.Ready
            assertTrue(ready.collectionChanging)
            assertEquals(1L, ready.collectionRevision)
            assertTrue(
                runCatching { client.getSceneList() }.exceptionOrNull()
                    is ObsWorkspaceChangedException
            )
            assertEquals(0, server.receivedCount("GetSceneList"))
            server.sendEvent("CurrentSceneCollectionChanged", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first {
                    it is ConnectionState.Ready && !it.collectionChanging
                }
            }
            assertTrue(client.getSceneList().scenes.isNotEmpty())
            client.disconnect()
        }
    }

    @Test
    fun bufferedEventsRetainOriginAcrossReconnect(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            val original = (client.connectionState.value as ConnectionState.Ready).token
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val received = CopyOnWriteArrayList<ObsSessionEvent>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.eventsWithContext.collect {
                        entered.complete(Unit)
                        release.await()
                        received += it
                    }
                }
            val sceneEvent = loadObsFixture("events/current_program_scene_changed.json")
            server.sendEvent("CurrentProgramSceneChanged", sceneEvent)
            withTimeout(5_000) { entered.await() }
            server.sendEvent("CurrentProgramSceneChanged", sceneEvent)
            // This following event proves the preceding scene event has reached the receiver.
            server.sendEvent("CurrentSceneCollectionChanging", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first {
                    it is ConnectionState.Ready && it.collectionChanging
                }
            }
            client.disconnect()
            server.enqueueSession()
            client.connect("127.0.0.1", server.port, null)
            val replacement = (client.connectionState.value as ConnectionState.Ready).token
            assertNotEquals(original.sessionId, replacement.sessionId)
            release.complete(Unit)
            withTimeout(5_000) { while (received.size < 2) delay(10) }
            assertEquals(listOf(original, original), received.take(2).map { it.token })
            collector.cancelAndJoin()
            client.disconnect()
        }
    }

    @Test
    fun collectionChangeRejectsPollingAndLateResponses(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            server.responseDelayMs = { if (it == "GetSceneList") 400 else 0 }
            val pending = async { runCatching { client.getSceneList() } }
            withTimeout(5_000) { while (server.receivedCount("GetSceneList") != 1) delay(10) }
            server.sendEvent("CurrentSceneCollectionChanging", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first {
                    it is ConnectionState.Ready && it.collectionChanging
                }
            }
            val duringChange = runCatching { client.getSceneList() }
            assertTrue(duringChange.exceptionOrNull() is ObsWorkspaceChangedException)
            assertEquals(1, server.receivedCount("GetSceneList"))

            server.sendEvent("CurrentSceneCollectionChanged", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first {
                    it is ConnectionState.Ready &&
                        !it.collectionChanging &&
                        it.collectionRevision == 2L
                }
            }
            assertTrue(pending.await().exceptionOrNull() is ObsWorkspaceChangedException)
            server.responseDelayMs = { 0 }
            assertTrue(client.getSceneList().scenes.isNotEmpty())
            assertEquals(2, server.receivedCount("GetSceneList"))
            client.disconnect()
        }
    }

    @Test
    fun queuedWritesCannotCrossCollectionBoundary(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            repeat(8) { client.requestSemaphore.acquire() }
            val write =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { client.setCurrentProgramScene("Camera") }
                }
            val collectionWrite =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { client.setCurrentSceneCollection("Third") }
                }
            server.sendEvent("CurrentSceneCollectionChanging", COLLECTION_DATA)
            server.sendEvent("CurrentSceneCollectionChanged", COLLECTION_DATA)
            withTimeout(5_000) {
                client.connectionState.first {
                    it is ConnectionState.Ready && it.collectionRevision == 2L
                }
            }
            repeat(8) { client.requestSemaphore.release() }
            assertTrue(write.await().exceptionOrNull() is ObsWorkspaceChangedException)
            assertTrue(collectionWrite.await().exceptionOrNull() is ObsWorkspaceChangedException)
            assertEquals(0, server.receivedCount("SetCurrentProgramScene"))
            assertEquals(0, server.receivedCount("SetCurrentSceneCollection"))
            client.disconnect()
        }
    }

    @Test
    fun intentionalCollectionSwitchCanCompleteAcrossItsOwnEvents(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            server.responseFrame = { request, frame ->
                if (request == "SetCurrentSceneCollection") {
                    server.sendEvent("CurrentSceneCollectionChanging", COLLECTION_DATA)
                    server.sendEvent("CurrentSceneCollectionChanged", COLLECTION_DATA)
                }
                frame
            }
            client.setCurrentSceneCollection("Other")
            val ready = client.connectionState.value as ConnectionState.Ready
            assertEquals(2L, ready.collectionRevision)
            assertEquals(1, server.receivedCount("SetCurrentSceneCollection"))
            client.disconnect()
        }
    }

    private companion object {
        const val COLLECTION_DATA = "{\"sceneCollectionName\":\"Other\"}"
    }
}
