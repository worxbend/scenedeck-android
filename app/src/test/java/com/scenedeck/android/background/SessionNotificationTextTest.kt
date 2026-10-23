package com.scenedeck.android.background

import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Keep-alive notification output state: LIVE/REC with elapsed time, never a guessed idle. */
class SessionNotificationTextTest {

    private val ready =
        ConnectionState.Ready(
            ObsVersionInfo(obsVersion = "32.0.0", obsWebSocketVersion = "5.6.0", rpcVersion = 1)
        )

    private fun stream(
        active: Boolean,
        timecode: String = "00:12:03.250",
        reconnecting: Boolean = false,
    ) =
        StreamStatus(
            active = active,
            reconnecting = reconnecting,
            timecode = timecode,
            durationMs = 0,
            bytes = 0,
            congestion = 0.0,
            skippedFrames = 0,
            totalFrames = 0,
        )

    private fun record(
        active: Boolean,
        timecode: String = "00:05:00.000",
        paused: Boolean = false,
    ) =
        RecordStatus(
            active = active,
            paused = paused,
            timecode = timecode,
            durationMs = 0,
            bytes = 0,
        )

    @Test
    fun `recording shows REC with record time and the program scene`() {
        assertEquals(
            "REC 00:05:00 · BRB",
            SessionNotification.textFor(ready, "BRB", stream(false), record(true)),
        )
    }

    @Test
    fun `streaming shows LIVE with stream time`() {
        assertEquals(
            "LIVE 00:12:03 · Cam 1",
            SessionNotification.textFor(ready, "Cam 1", stream(true), record(false)),
        )
    }

    @Test
    fun `simultaneous outputs show both`() {
        assertEquals(
            "LIVE 00:12:03 · REC 00:05:00 · Cam 1",
            SessionNotification.textFor(ready, "Cam 1", stream(true), record(true)),
        )
    }

    @Test
    fun `paused recording and reconnecting stream are labelled`() {
        assertEquals(
            "LIVE (reconnecting) 00:12:03 · REC (paused) 00:05:00",
            SessionNotification.textFor(
                ready,
                null,
                stream(true, reconnecting = true),
                record(true, paused = true),
            ),
        )
    }

    @Test
    fun `both outputs known idle reads OFF AIR`() {
        assertEquals(
            "Connected · OFF AIR · BRB",
            SessionNotification.textFor(ready, "BRB", stream(false), record(false)),
        )
    }

    @Test
    fun `unknown output state never claims idle`() {
        assertEquals("Connected · BRB", SessionNotification.textFor(ready, "BRB", null, null))
        assertEquals(
            "Connected · BRB",
            SessionNotification.textFor(ready, "BRB", stream(false), null),
        )
    }

    @Test
    fun `stale telemetry from a non-ready snapshot is ignored`() {
        val stale =
            Telemetry(
                connection = ConnectionState.Reconnecting(1),
                stream = stream(true),
                record = record(true),
            )
        assertEquals("Connected · BRB", ObsSessionService.notificationText(ready, "BRB", stale))
    }

    @Test
    fun `ready telemetry feeds the notification`() {
        val live = Telemetry(connection = ready, stream = stream(true), record = record(false))
        assertEquals(
            "LIVE 00:12:03 · BRB",
            ObsSessionService.notificationText(ready, "BRB", live),
        )
    }

    @Test
    fun `not ready connection ignores outputs`() {
        val live = Telemetry(connection = ready, stream = stream(true), record = record(true))
        assertEquals(
            "Reconnecting (attempt 2)…",
            ObsSessionService.notificationText(ConnectionState.Reconnecting(2), "BRB", live),
        )
    }
}
