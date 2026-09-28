package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ReconnectTest : ObsClientTestBase() {

    @Test
    fun unexpectedSocketLoss_reconnectsWithBackoff(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            server.enqueueSession() // the reconnect lands on the second script

            val client = newClient(backoffMillis = { 200 })
            val states = CopyOnWriteArrayList<ConnectionState>()
            val collectJob = scope.launch { client.connectionState.collect { states += it } }

            client.connect("127.0.0.1", server.port)
            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)

            // Simulate Wi-Fi drop: server closes the socket out from under the client.
            server.closeActiveSockets(code = 1001, reason = "going away")

            withTimeout(15_000) {
                while (server.connectionCount.get() < 2) delay(20)
            }
            withTimeout(15_000) {
                while (client.connectionState.value !is ConnectionState.Ready) delay(20)
            }

            assertEquals(2, server.connectionCount.get())
            assertTrue(
                "expected a Reconnecting state during backoff, saw $states",
                states.any { it is ConnectionState.Reconnecting },
            )
            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)

            collectJob.cancel()
            client.disconnect()
        }
    }

    @Test
    fun concurrentRequests_boundedBySemaphore(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            // Hold permits: responses go out 300 ms late, well after the snapshot.
            server.responseDelayMs = { 300 }
            val results = (1..20).map { async { client.getStats() } }

            delay(150)
            assertEquals(
                "only Semaphore(8) requests may be in flight",
                8,
                server.receivedCount("GetStats"),
            )

            results.awaitAll()
            withTimeout(10_000) {
                while (server.receivedCount("GetStats") < 20) delay(20)
            }
            assertEquals(20, server.receivedCount("GetStats"))

            client.disconnect()
        }
    }
}
