package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.internal.KtobsObsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ConnectionErrorSanitizationTest : ObsClientTestBase() {

    @Test
    fun publicConnectionDiagnosticStringsRedactCallerSuppliedDetails() {
        val marker = "unique-private-connection-marker"
        val errors =
            listOf(
                ConnectionError.Auth(marker),
                ConnectionError.Unreachable(IllegalArgumentException(marker)),
                ConnectionError.Protocol(marker),
                ConnectionError.Closed(4009, marker),
            )
        errors.forEach { error ->
            assertTrue(!error.toString().contains(marker))
            assertTrue(!ConnectionState.Failed(error).toString().contains(marker))
        }
        assertTrue(errors.last().toString().contains("4009"))
    }

    @Test
    fun malformedVersionReturnsSafeProtocolMessage(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val marker = "unique-private-response-marker"
            server.responseData = { type, fixture ->
                if (type == "GetVersion") {
                    """
                    {
                      "obsVersion": {"private": "$marker"},
                      "obsWebSocketVersion": "5.6.1",
                      "rpcVersion": 1,
                      "availableRequests": [],
                      "supportedImageFormats": [],
                      "platform": "test",
                      "platformDescription": "test"
                    }
                    """
                        .trimIndent()
                } else fixture
            }
            val client = KtobsObsClient(scope)
            client.connect("127.0.0.1", server.port)

            val state = assertIsInstance<ConnectionState.Failed>(client.connectionState.value)
            val error = assertIsInstance<ConnectionError.Protocol>(state.error)
            assertEquals("Unable to decode OBS protocol response", error.message)
            assertTrue(!state.toString().contains(marker))
        }
    }

    @Test
    fun authenticationFailureDoesNotExposeServerReason(): Unit = runBlocking {
        FakeObsServer(password = TEST_PASSWORD).use { server ->
            server.start()
            server.enqueueSession()
            val marker = "unique-private-auth-marker"
            server.authenticationFailureReason = marker
            val client = KtobsObsClient(scope)
            client.connect("127.0.0.1", server.port, "wrong-password")

            val state = assertIsInstance<ConnectionState.Failed>(client.connectionState.value)
            val error = assertIsInstance<ConnectionError.Auth>(state.error)
            assertTrue(!error.message.orEmpty().contains(marker))
            assertTrue(!state.toString().contains(marker))
        }
    }

    @Test
    fun unreachableFailureDoesNotRetainRawTransportCause(): Unit = runBlocking {
        val server = FakeObsServer()
        server.start()
        val closedPort = server.port
        server.close()
        val client = KtobsObsClient(scope)
        client.connect("127.0.0.1", closedPort)

        val state = assertIsInstance<ConnectionState.Failed>(client.connectionState.value)
        val error = assertIsInstance<ConnectionError.Unreachable>(state.error)
        assertNull(error.cause)
    }
}
