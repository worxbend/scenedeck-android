package com.scenedeck.android.core.obs

import app.cash.turbine.test
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.RecordingPhase
import com.scenedeck.android.core.obs.internal.KtobsObsClient
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class RecordingToolsProtocolTest : ObsClientTestBase() {
    @Test
    fun advertisedRequestsAreMappedWithoutVersionGuessing(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            val ready = client.connectionState.value as ConnectionState.Ready
            assertEquals(ObsCapabilities(), ready.sessionInfo.capabilities)
            server.responseData = { type, fixture ->
                if (type == "GetVersion")
                    fixture!!.replace(
                        "[\"GetVersion\",\"GetSceneList\"]",
                        "[\"PauseRecord\",\"ResumeRecord\",\"SplitRecordFile\"," +
                            "\"CreateRecordChapter\",\"SetMediaInputCursor\",\"FutureRequest\"]",
                    )
                else fixture
            }
            assertEquals(
                ObsCapabilities(true, true, true, true, true),
                client.getVersion().capabilities,
            )
            client.disconnect()
        }
    }

    @Test
    fun targetValueRequestsAndChapterNameUseExpectedWireShape(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            client.pauseRecord()
            client.resumeRecord()
            client.splitRecordFile()
            client.createRecordChapter("Guest \"hello\" ✓")
            listOf("PauseRecord", "ResumeRecord", "SplitRecordFile", "CreateRecordChapter")
                .forEach {
                    assertEquals(1, server.receivedCount(it))
                }
            assertEquals(0, server.receivedCount("ToggleRecordPause"))
            val frame = Json.parseToJsonElement(server.receivedRequestFrames.last()).jsonObject
            assertEquals(
                "Guest \"hello\" ✓",
                frame["requestData"]!!.jsonObject["chapterName"]!!.jsonPrimitive.content,
            )
            client.disconnect()
        }
    }

    @Test
    fun missingWriteReplyTimesOutOnceAndFollowingReadsWork(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            server.enqueueSession()
            val client = KtobsObsClient(scope, requestTimeoutMs = 100)
            client.connect("127.0.0.1", server.port)
            server.shouldRespond = { it != "SplitRecordFile" }
            assertIsInstance<ObsRequestTimeoutException>(
                runCatching { client.splitRecordFile() }.exceptionOrNull()
            )
            assertFalse(client.getRecordStatus().active)
            assertEquals(1, server.receivedCount("SplitRecordFile"))
            client.disconnect()
        }
    }

    @Test
    fun cancelledChapterIsNeverRetried(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            server.shouldRespond = { it != "CreateRecordChapter" }
            val operation = async { client.createRecordChapter(null) }
            withTimeout(5_000) {
                while (server.receivedCount("CreateRecordChapter") == 0) delay(10)
            }
            operation.cancel()
            operation.join()
            assertTrue(operation.isCancelled)
            assertFalse(client.getRecordStatus().active)
            assertEquals(1, server.receivedCount("CreateRecordChapter"))
            client.disconnect()
        }
    }

    @Test
    fun recordingLifecycleIsTypedAndUnknownFutureStatesStayUnknown(): Unit = runBlocking {
        FakeObsServer().use { server ->
            server.start()
            val client = connectedClient(server)
            client.events.test {
                server.sendEvent(
                    "RecordStateChanged",
                    """{"outputActive":true,"outputState":"OBS_WEBSOCKET_OUTPUT_STARTED", "outputPath":null}""",
                )
                assertEquals(
                    RecordingPhase.STARTED,
                    (awaitItem() as ObsEvent.RecordStateChanged).phase,
                )
                server.sendEvent(
                    "RecordStateChanged",
                    """{"outputActive":true,"outputState":"FUTURE_STATE"}""",
                )
                assertEquals(
                    RecordingPhase.UNKNOWN,
                    (awaitItem() as ObsEvent.RecordStateChanged).phase,
                )
                cancelAndIgnoreRemainingEvents()
            }
            client.disconnect()
        }
    }
}
