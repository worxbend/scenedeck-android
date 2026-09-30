package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class HandshakeTest : ObsClientTestBase() {

    @Test
    fun authDigest_matchesReferenceVector() {
        // Salt/challenge from the obs-websocket v5 protocol docs; digest computed
        // independently (java.security here, Python at authoring time).
        assertEquals(
            "8eVfwPAKQTVbEdeFbiPgsxA+sOKotbI61+YjhvIo6AY=",
            expectedAuthResponse(TEST_PASSWORD, TEST_SALT, TEST_CHALLENGE),
        )
    }

    @Test
    fun connectWithoutAuth_reachesReady(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)

            val state = assertIsInstance<ConnectionState.Ready>(client.connectionState.value)
            assertEquals("31.0.1", state.sessionInfo.obsVersion)
            assertEquals("5.6.1", state.sessionInfo.obsWebSocketVersion)
            // Meters must be explicitly subscribed on top of All.
            assertEquals(EXPECTED_EVENT_SUBS, server.receivedEventSubs.get())

            client.disconnect()
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        }
    }

    @Test
    fun connectWithCorrectPassword_reachesReady(): Unit = runBlocking {
        FakeObsServer(password = TEST_PASSWORD).use { server ->
            server.start()
            val client = connectedClient(server, password = TEST_PASSWORD)

            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)
            assertEquals(1, server.connectionCount.get())
            client.disconnect()
        }
    }

    @Test
    fun connectWithWrongPassword_failsAuthWithoutRetry(): Unit = runBlocking {
        FakeObsServer(password = TEST_PASSWORD).use { server ->
            server.start()
            val client = connectedClient(server, password = "definitely-wrong")

            val state = assertIsInstance<ConnectionState.Failed>(client.connectionState.value)
            assertIsInstance<ConnectionError.Auth>(state.error)

            // Auth failures are terminal: no reconnect attempt may be made.
            delay(10 * TEST_BACKOFF_MS * 2)
            assertEquals(1, server.connectionCount.get())
            assertTrue(client.connectionState.value is ConnectionState.Failed)
        }
    }

    @Test
    fun connectWithoutPasswordWhenRequired_failsAuth(): Unit = runBlocking {
        FakeObsServer(password = TEST_PASSWORD).use { server ->
            server.start()
            val client = connectedClient(server, password = null)

            val state = assertIsInstance<ConnectionState.Failed>(client.connectionState.value)
            assertIsInstance<ConnectionError.Auth>(state.error)
            // The client must fail before sending Identify.
            assertEquals(-1, server.receivedEventSubs.get())
        }
    }

    @Test
    fun connectToUnreachableHost_failsUnreachable(): Unit = runBlocking {
        val server = FakeObsServer()
        server.start()
        val deadPort = server.port
        server.close()

        val client = newClient()
        client.connect("127.0.0.1", deadPort)

        val state = assertIsInstance<ConnectionState.Failed>(client.connectionState.value)
        assertIsInstance<ConnectionError.Unreachable>(state.error)
    }

    @Test
    fun requestWithoutConnection_throwsNotConnected() {
        val client = newClient()
        assertFails<ObsNotConnectedException> {
            runBlocking { client.getVersion() }
        }
    }

    @Test
    fun cancellingClientScopeFinishesPendingConnect(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            server.responseDelayMs = { 30_000 }
            val client = newClient()
            val pending = async { client.connect("127.0.0.1", server.port) }
            kotlinx.coroutines.withTimeout(5_000) {
                while (server.receivedCount("GetVersion") == 0) delay(10)
            }
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            kotlinx.coroutines.withTimeout(5_000) { pending.join() }
            assertTrue(pending.isCancelled)
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        }
    }

    @Test
    fun cancellingConnectClosesItsSession(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            server.responseDelayMs = { 30_000 }
            val client = newClient()
            val pending = async { client.connect("127.0.0.1", server.port) }
            kotlinx.coroutines.withTimeout(5_000) {
                while (server.receivedCount("GetVersion") == 0) delay(10)
            }
            pending.cancel()
            kotlinx.coroutines.withTimeout(5_000) { pending.join() }
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        }
    }
}
