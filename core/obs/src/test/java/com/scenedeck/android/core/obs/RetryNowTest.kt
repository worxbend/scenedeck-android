package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A backoff long enough that only [ObsClient.retryNow] can start the next attempt in time. */
private const val LONG_BACKOFF_MS = 120_000L

internal class RetryNowTest : ObsClientTestBase() {

    private suspend fun awaitState(client: ObsClient, predicate: (ConnectionState) -> Boolean) {
        withTimeout(10_000) { while (!predicate(client.connectionState.value)) delay(20) }
    }

    /** Connects, then drops the socket so the client waits in its first backoff. */
    private suspend fun lostSessionClient(server: FakeObsServer): ObsClient {
        server.enqueueSession()
        val client = newClient(backoffMillis = { LONG_BACKOFF_MS })
        client.connect("127.0.0.1", server.port)
        server.closeActiveSockets(code = 1001, reason = "going away")
        awaitState(client) { it == ConnectionState.Reconnecting(1) }
        return client
    }

    @Test
    fun retryNow_cutsBackoffShortAndReconnects(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = lostSessionClient(server)
            server.enqueueSession()

            delay(200)
            assertEquals("backoff must hold the next attempt", 1, server.connectionCount.get())

            assertTrue(client.retryNow())
            awaitState(client) { it is ConnectionState.Ready }
            assertEquals(2, server.connectionCount.get())
            // Requests bind to the new session.
            client.getVersion()

            client.disconnect()
        }
    }

    @Test
    fun retryNow_isNoOpOutsideBackoff(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = newClient(backoffMillis = { LONG_BACKOFF_MS })
            assertFalse("no session yet", client.retryNow())

            server.enqueueSession()
            client.connect("127.0.0.1", server.port)
            assertFalse("a Ready session is left untouched", client.retryNow())
            delay(200)
            assertEquals(1, server.connectionCount.get())
            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)

            client.disconnect()
            assertFalse("disconnect stops the loop", client.retryNow())
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        }
    }

    @Test
    fun failedRetry_resumesBackoffWithNextAttempt(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = lostSessionClient(server)
            server.enqueueRejectedSession()

            assertTrue(client.retryNow())
            awaitState(client) { it == ConnectionState.Reconnecting(2) }
            assertEquals(1, server.connectionCount.get())

            // A fresh backoff wait: the next retry is honoured too.
            server.enqueueSession()
            assertTrue(client.retryNow())
            awaitState(client) { it is ConnectionState.Ready }
            assertEquals(2, server.connectionCount.get())

            client.disconnect()
        }
    }

    @Test
    fun disconnectDuringBackoff_stopsLoop(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = lostSessionClient(server)

            client.disconnect()
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
            assertFalse(client.retryNow())
            delay(200)
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        }
    }
}
