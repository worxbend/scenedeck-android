package com.scenedeck.android.background

import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionServiceLogicTest {

    private val ready =
        ConnectionState.Ready(
            ObsVersionInfo(obsVersion = "31.0.0", obsWebSocketVersion = "5.5.2", rpcVersion = 1)
        )

    @Test
    fun `start action goes foreground`() {
        val logic = SessionServiceLogic()
        assertEquals(
            SessionServiceLogic.Effect.GO_FOREGROUND,
            logic.onStartAction(ObsSessionService.ACTION_START),
        )
    }

    @Test
    fun `null action (sticky restart) goes foreground`() {
        val logic = SessionServiceLogic()
        assertEquals(SessionServiceLogic.Effect.GO_FOREGROUND, logic.onStartAction(null))
    }

    @Test
    fun `disconnect action shuts down`() {
        val logic = SessionServiceLogic()
        assertEquals(
            SessionServiceLogic.Effect.SHUTDOWN,
            logic.onStartAction(ObsSessionService.ACTION_DISCONNECT),
        )
    }

    @Test
    fun `disconnected before first ready does not kill the service`() {
        val logic = SessionServiceLogic()
        assertEquals(
            SessionServiceLogic.Effect.NONE,
            logic.onConnectionState(ConnectionState.Disconnected),
        )
        assertFalse(logic.wasReady)
    }

    @Test
    fun `disconnected after ready shuts down (explicit disconnect elsewhere)`() {
        val logic = SessionServiceLogic()
        logic.onConnectionState(ConnectionState.Connecting)
        logic.onConnectionState(ready)
        assertTrue(logic.wasReady)
        assertEquals(
            SessionServiceLogic.Effect.SHUTDOWN,
            logic.onConnectionState(ConnectionState.Disconnected),
        )
    }

    @Test
    fun `reconnecting and failed keep the service alive`() {
        val logic = SessionServiceLogic()
        logic.onConnectionState(ready)
        assertEquals(
            SessionServiceLogic.Effect.NONE,
            logic.onConnectionState(ConnectionState.Reconnecting(attempt = 2)),
        )
        assertEquals(
            SessionServiceLogic.Effect.NONE,
            logic.onConnectionState(ConnectionState.Failed(ConnectionError.Unreachable())),
        )
    }

    @Test
    fun `notification text mapping`() {
        assertEquals(
            "Connected · Cam 1",
            SessionNotification.textFor(ready, "Cam 1"),
        )
        assertEquals("Connected to OBS", SessionNotification.textFor(ready, null))
        assertEquals(
            "Connecting to OBS…",
            SessionNotification.textFor(ConnectionState.Connecting, null),
        )
        assertEquals(
            "Reconnecting (attempt 3)…",
            SessionNotification.textFor(ConnectionState.Reconnecting(3), null),
        )
        assertEquals(
            "Connection failed — tap to open SceneDeck",
            SessionNotification.textFor(ConnectionState.Failed(ConnectionError.Auth()), null),
        )
        assertEquals(
            "Disconnected",
            SessionNotification.textFor(ConnectionState.Disconnected, null),
        )
    }
}
