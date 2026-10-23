package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.internal.KtobsObsClient
import com.scenedeck.android.core.obs.internal.ObsSessionRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class RequestLifecycleTest : ObsClientTestBase() {

    @Test
    fun missingResponsesReleaseEveryPermitAndPendingRequest(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 300)
            client.connect("127.0.0.1", server.port)
            server.shouldRespond = { it != "GetStats" }
            repeat(3) { round ->
                val pending = (1..8).map { scope.async { runCatching { client.getStats() } } }
                awaitRequests(server, "GetStats", (round + 1) * 8)
                withTimeout(5_000) {
                    pending.awaitAll().forEach {
                        assertIsInstance<ObsRequestTimeoutException>(it.exceptionOrNull())
                    }
                }
                assertEquals(0, activeRequestCount(client))
            }
            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)
            server.shouldRespond = { true }
            assertTrue(client.getSceneList().scenes.isNotEmpty())
            client.disconnect()
        }
    }

    @Test
    fun requestDeadlineIncludesPermitWait(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 100)
            client.connect("127.0.0.1", server.port)
            val gate = client.requestSemaphore
            repeat(8) { gate.acquire() }

            val failure =
                withTimeout(5_000) { runCatching { client.startStream() }.exceptionOrNull() }
            assertIsInstance<ObsRequestTimeoutException>(failure)
            assertEquals(0, server.receivedCount("StartStream"))
            repeat(8) { gate.release() }
            client.disconnect()
        }
    }

    @Test
    fun lateResponseDoesNotBreakSubsequentRequests(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 100)
            client.connect("127.0.0.1", server.port)
            server.responseDelayMs = { if (it == "GetStats") 250 else 0 }

            assertIsInstance<ObsRequestTimeoutException>(
                runCatching { client.getStats() }.exceptionOrNull()
            )
            assertEquals(0, activeRequestCount(client))
            delay(300)
            server.responseDelayMs = { 0 }
            client.getStats()
            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)
            client.disconnect()
        }
    }

    @Test
    fun callerCancellationRemainsCancellationAndRemovesPendingRequest(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 10_000)
            client.connect("127.0.0.1", server.port)
            server.shouldRespond = { it != "GetStats" }
            val pending = scope.async { client.getStats() }
            awaitRequests(server, "GetStats", 1)
            pending.cancel()

            val failure = runCatching { pending.await() }.exceptionOrNull()
            assertIsInstance<CancellationException>(failure)
            withTimeout(5_000) { pending.join() }
            assertEquals(0, activeRequestCount(client))
            val outerTimeout = runCatching {
                withTimeout(20) { client.getStats() }
            }
                .exceptionOrNull()
            assertIsInstance<TimeoutCancellationException>(outerTimeout)
            assertEquals(0, activeRequestCount(client))
            client.disconnect()
        }
    }

    @Test
    fun concurrentCallerCancellationRemovesAllPendingRequests(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 10_000)
            client.connect("127.0.0.1", server.port)
            server.shouldRespond = { it != "GetStats" }
            repeat(3) { round ->
                val pending = (1..8).map { scope.async { client.getStats() } }
                awaitRequests(server, "GetStats", (round + 1) * 8)
                pending.forEach { it.cancel() }
                pending.forEach {
                    assertIsInstance<CancellationException>(
                        runCatching { it.await() }.exceptionOrNull()
                    )
                }
                withTimeout(5_000) { pending.joinAll() }
                assertEquals(0, activeRequestCount(client))
            }
            server.shouldRespond = { true }
            client.getStats()
            client.disconnect()
        }
    }

    @Test
    fun queuedCommandCannotUseReplacementSession(): Unit = runBlocking {
        FakeObsServer().use { original ->
            FakeObsServer().use { replacement ->
                original.start()
                replacement.start()
                original.enqueueSession()
                replacement.enqueueSession()
                val client = client(timeoutMs = 10_000)
                client.connect("127.0.0.1", original.port)
                val gate = client.requestSemaphore
                repeat(8) { gate.acquire() }
                val paused = StandardTestDispatcher()
                val queued =
                    scope.async(paused, start = CoroutineStart.UNDISPATCHED) {
                        runCatching { client.startStream() }
                    }

                val reconnect = scope.async { client.connect("127.0.0.1", replacement.port) }
                withTimeout(5_000) {
                    while (replacement.connectionCount.get() == 0) delay(10)
                }
                repeat(8) { gate.release() }
                withTimeout(5_000) { reconnect.await() }
                paused.scheduler.runCurrent()

                assertIsInstance<ObsNotConnectedException>(queued.await().exceptionOrNull())
                assertEquals(0, replacement.receivedCount("StartStream"))
                assertIsInstance<ConnectionState.Ready>(client.connectionState.value)
                client.disconnect()
            }
        }
    }

    @Test
    fun completedOldResponseCannotReturnAfterReplacement(): Unit = runBlocking {
        FakeObsServer().use { original ->
            FakeObsServer().use { replacement ->
                original.start()
                replacement.start()
                original.enqueueSession()
                replacement.enqueueSession()
                val client = client(timeoutMs = 10_000)
                client.connect("127.0.0.1", original.port)
                val replyAllowed = CountDownLatch(1)
                original.responseFrame = { type, frame ->
                    if (type == "GetStats") check(replyAllowed.await(5, TimeUnit.SECONDS))
                    frame
                }
                val paused = StandardTestDispatcher()
                val oldRead =
                    scope.async(paused, start = CoroutineStart.UNDISPATCHED) {
                        runCatching { client.getStats() }
                    }
                awaitRequests(original, "GetStats", 1)
                val response = activeRequests(client).values.single() as Deferred<*>
                replyAllowed.countDown()
                // The response completes on the protocol lane while its caller stays paused.
                withTimeout(5_000) { response.await() }
                client.connect("127.0.0.1", replacement.port)
                paused.scheduler.runCurrent()

                assertIsInstance<ObsNotConnectedException>(oldRead.await().exceptionOrNull())
                client.disconnect()
            }
        }
    }

    @Test
    fun repeatedTimeoutsDoNotRetainPendingUuids(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 30)
            client.connect("127.0.0.1", server.port)
            server.shouldRespond = { it != "GetStats" }

            repeat(12) {
                assertIsInstance<ObsRequestTimeoutException>(
                    runCatching { client.getStats() }.exceptionOrNull()
                )
                assertEquals(0, activeRequestCount(client))
            }
            client.disconnect()
        }
    }

    @Test
    fun requestErrorDropsPrivateCommentAndCause() {
        val privateMarker = "private-OBS-diagnostic-marker"
        val error =
            ObsRequestFailedException(
                "StartStream",
                "ResourceNotFound",
                privateMarker,
                IllegalArgumentException(privateMarker),
            )
        assertEquals("StartStream", error.requestType)
        assertEquals("ResourceNotFound", error.statusCode)
        assertTrue(!error.message.orEmpty().contains(privateMarker))
        assertTrue(!error.toString().contains(privateMarker))
        assertNull(error.cause)
    }

    @Test
    fun serverRejectedCommandKeepsStatusWithoutPrivateComment(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 10_000)
            client.connect("127.0.0.1", server.port)
            val marker = "private-server-response-comment"
            server.responseFrame = { type, frame ->
                if (type == "StartStream")
                    frame.replace(
                        "\"result\":true,\"code\":100",
                        "\"result\":false,\"code\":600,\"comment\":\"$marker\"",
                    )
                else frame
            }

            val failure = runCatching { client.startStream() }.exceptionOrNull()
            val error = assertIsInstance<ObsRequestFailedException>(failure)
            assertEquals("StartStream", error.requestType)
            assertEquals("ResourceNotFound", error.statusCode)
            assertTrue(!error.message.orEmpty().contains(marker))
            assertNull(error.cause)
            client.disconnect()
        }
    }

    @Test
    fun audioRequestOnVideoOnlyInputIsRecognisedAsWithoutAudio(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 10_000)
            client.connect("127.0.0.1", server.port)
            server.responseFrame = { type, frame ->
                if (type == "GetInputVolume")
                    frame.replace(
                        "\"result\":true,\"code\":100",
                        "\"result\":false,\"code\":604," +
                            "\"comment\":\"The specified input does not support audio.\"",
                    )
                else frame
            }

            val failure = runCatching { client.getInputVolume("Color") }.exceptionOrNull()
            val error = assertIsInstance<ObsRequestFailedException>(failure)
            assertTrue(error.isInputWithoutAudio)
            assertTrue(
                !ObsRequestFailedException("GetInputVolume", "ResourceNotFound", null)
                    .isInputWithoutAudio
            )
            client.disconnect()
        }
    }

    @Test
    fun malformedRequestResponseReturnsSafeProtocolFailureAndReleasesRequest(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = client(timeoutMs = 10_000)
            client.connect("127.0.0.1", server.port)
            val marker = "private-malformed-stats-field"
            server.responseData = { type, fixture ->
                if (type == "GetStats")
                    fixture?.replace("\"cpuUsage\":1.234567", "\"cpuUsage\":\"$marker\"")
                else fixture
            }

            val failure = runCatching { client.getStats() }.exceptionOrNull()
            val error = assertIsInstance<ObsRequestProtocolException>(failure)
            assertEquals("Unable to decode OBS protocol response", error.message)
            assertTrue(!error.toString().contains(marker))
            // Coroutine stack-trace recovery may copy safe exceptions into their cause chain.
            assertTrue(
                generateSequence<Throwable>(error) { it.cause }
                    .all { !it.toString().contains(marker) }
            )
            assertEquals(0, activeRequestCount(client))
            assertIsInstance<ConnectionState.Ready>(client.connectionState.value)
            server.responseData = { _, fixture -> fixture }
            client.getStats()
            client.disconnect()
        }
    }

    private fun client(timeoutMs: Long) = KtobsObsClient(scope, requestTimeoutMs = timeoutMs)

    private suspend fun awaitRequests(server: FakeObsServer, type: String, count: Int) {
        withTimeout(5_000) {
            while (server.receivedCount(type) < count) delay(10)
        }
    }

    /** Inspect the pinned library's actual waiter store, rather than infer cleanup from permits. */
    private fun activeRequestCount(client: KtobsObsClient): Int = activeRequests(client).size

    private fun activeRequests(client: KtobsObsClient): Map<*, *> {
        val runner =
            KtobsObsClient::class
                .java
                .getDeclaredField("sessionRunner")
                .apply {
                    isAccessible = true
                }
                .get(client) as ObsSessionRunner
        val session = requireNotNull(runner.session)
        return session.javaClass
            .getDeclaredField("activeUuids")
            .apply {
                isAccessible = true
            }
            .get(session) as Map<*, *>
    }
}
