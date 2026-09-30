package com.scenedeck.android.core.obs

import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Salt/challenge pair from the obs-websocket v5 protocol documentation example. */
internal const val TEST_SALT = "lM1GncleQOaCu9lT1yeUZhFYnqhsLLP1G5lAGo3ixaI="
internal const val TEST_CHALLENGE = "ztTBnnuqrqaKDzRM3xcVdbYm38ZXeEygNTDKqGS6cSA="
internal const val TEST_PASSWORD = "supersecretpassword"

internal const val CLOSE_AUTH_FAILED = 4009

/** `All` (bits 0-10) plus the `InputVolumeMeters` bit (1 shl 16). */
internal const val EXPECTED_EVENT_SUBS = 2047 + 65536

/** Reference implementation of the v5 auth digest (independent of ktobs's). */
internal fun expectedAuthResponse(password: String, salt: String, challenge: String): String {
    fun sha256(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    val secret = b64(sha256(password + salt))
    return b64(sha256(secret + challenge))
}

internal fun loadObsFixture(name: String): String =
    requireNotNull(FakeObsServer::class.java.classLoader?.getResource("obsws/$name")) {
            "missing fixture obsws/$name"
        }
        .readText()

/**
 * Scripted fake obs-websocket v5 server (MockWebServer WebSocket upgrade) speaking from JSON
 * fixtures recorded to match real OBS 31 / obs-websocket 5.6 shapes.
 *
 * Usage: `start()`, [enqueueSession] once per expected connection, then drive requests/events;
 * [sendEvent]/[closeActiveSockets] push server-side frames.
 */
internal class FakeObsServer(
    private val password: String? = null,
    private val salt: String = TEST_SALT,
    private val challenge: String = TEST_CHALLENGE,
) : AutoCloseable {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val responseExecutor = Executors.newSingleThreadScheduledExecutor()

    val connectionCount = AtomicInteger(0)
    val receivedEventSubs = AtomicInteger(-1)
    val receivedRequests = CopyOnWriteArrayList<String>()

    /** Extra delay before answering a given requestType (responses stay async). */
    var responseDelayMs: (requestType: String) -> Long = { 0L }

    private val activeSockets = CopyOnWriteArrayList<WebSocket>()

    val port: Int
        get() = server.port

    fun start() = server.start()

    /** Enqueue one WebSocket connection speaking the scripted protocol flow. */
    fun enqueueSession() {
        server.enqueue(
            MockResponse()
                .setHeader("Sec-WebSocket-Protocol", "obswebsocket.json")
                .withWebSocketUpgrade(SessionListener())
        )
    }

    fun sendEvent(eventType: String, eventDataJson: String, intent: Int = 1) {
        val frame =
            template("event.json")
                .replace("%TYPE%", eventType)
                .replace("%INTENT%", intent.toString())
                .replace("%DATA%", eventDataJson)
        activeSockets.forEach { it.send(frame) }
    }

    fun closeActiveSockets(code: Int = 1001, reason: String = "server shutdown") {
        activeSockets.forEach { it.close(code, reason) }
    }

    fun receivedCount(requestType: String): Int = receivedRequests.count { it == requestType }

    override fun close() {
        // Initiate server-side closes first so MockWebServer.shutdown() doesn't
        // hang waiting for open WebSocket connections.
        closeActiveSockets(code = 1000, reason = "shutdown")
        responseExecutor.shutdownNow()
        server.shutdown()
    }

    private fun fixture(name: String): String = loadObsFixture(name)

    private fun template(name: String): String = fixture(name)

    private inner class SessionListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connectionCount.incrementAndGet()
            activeSockets += webSocket
            val hello =
                if (password != null) {
                    template("hello_auth.json")
                        .replace("%CHALLENGE%", challenge)
                        .replace("%SALT%", salt)
                } else {
                    template("hello.json")
                }
            webSocket.send(hello)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = json.parseToJsonElement(text).jsonObject
            val data = message["d"]?.jsonObject ?: return
            when (message["op"]?.jsonPrimitive?.int) {
                OP_IDENTIFY -> handleIdentify(webSocket, data)
                OP_REQUEST -> handleRequest(webSocket, data)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            activeSockets -= webSocket
        }

        private fun handleIdentify(
            webSocket: WebSocket,
            data: kotlinx.serialization.json.JsonObject,
        ) {
            receivedEventSubs.set(data["eventSubscriptions"]?.jsonPrimitive?.int ?: -1)
            if (password != null) {
                val auth = data["authentication"]?.jsonPrimitive?.content
                if (auth != expectedAuthResponse(password, salt, challenge)) {
                    webSocket.close(CLOSE_AUTH_FAILED, "Authentication failed.")
                    return
                }
            }
            webSocket.send(template("identified.json"))
        }

        private fun handleRequest(
            webSocket: WebSocket,
            data: kotlinx.serialization.json.JsonObject,
        ) {
            val type = data["requestType"]!!.jsonPrimitive.content
            val id = data["requestId"]!!.jsonPrimitive.content
            receivedRequests += type

            val responseData = RESPONSE_FIXTURES[type]?.let { fixture("responses/$it") }
            val dataPart = if (responseData != null) ",\"responseData\":$responseData" else ""
            val frame =
                template("response.json")
                    .replace("%TYPE%", type)
                    .replace("%ID%", id)
                    .replace("%DATA%", dataPart)

            val delay = responseDelayMs(type)
            if (delay <= 0) {
                webSocket.send(frame)
            } else {
                responseExecutor.schedule({ webSocket.send(frame) }, delay, TimeUnit.MILLISECONDS)
            }
        }
    }

    companion object {
        private const val OP_IDENTIFY = 1
        private const val OP_REQUEST = 6

        private val RESPONSE_FIXTURES =
            mapOf(
                "GetVersion" to "get_version.json",
                "GetSceneList" to "get_scene_list.json",
                "GetCurrentProgramScene" to "get_current_program_scene.json",
                "GetSceneItemList" to "get_scene_item_list.json",
                "GetSceneItemEnabled" to "get_scene_item_enabled.json",
                "GetSpecialInputs" to "get_special_inputs.json",
                "GetInputMute" to "get_input_mute.json",
                "GetInputVolume" to "get_input_volume.json",
                "GetStreamStatus" to "get_stream_status.json",
                "GetRecordStatus" to "get_record_status.json",
                "StopRecord" to "stop_record.json",
                "GetProfileList" to "get_profile_list.json",
                "GetSceneCollectionList" to "get_scene_collection_list.json",
                "GetStats" to "get_stats.json",
                "GetStudioModeEnabled" to "get_studio_mode_enabled.json",
                "GetCurrentPreviewScene" to "get_current_preview_scene.json",
                "GetSceneTransitionList" to "get_scene_transition_list.json",
                "GetCurrentSceneTransition" to "get_current_scene_transition.json",
                "GetSourceScreenshot" to "get_source_screenshot.json",
                "GetVirtualCamStatus" to "get_virtualcam_status.json",
                "ToggleVirtualCam" to "toggle_virtualcam.json",
                "GetReplayBufferStatus" to "get_replay_buffer_status.json",
                "ToggleReplayBuffer" to "toggle_replay_buffer.json",
                "GetLastReplayBufferReplay" to "get_last_replay_buffer_replay.json",
                "GetMediaInputStatus" to "get_media_input_status.json",
                "GetInputAudioBalance" to "get_input_audio_balance.json",
                "GetInputAudioSyncOffset" to "get_input_audio_sync_offset.json",
                "GetInputAudioMonitorType" to "get_input_audio_monitor_type.json",
            )
    }
}
