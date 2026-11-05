package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkCaptureTest {
    private val token = ObsSessionToken(1, 2)
    private val telemetry =
        Telemetry(
            stream = StreamStatus(true, false, "unused", 90_005, 0, 0.0, 0, 0),
            record = RecordStatus(true, true, "unused", 12_345, 0),
            session = token,
            receivedAt = TelemetryReadTimes(stream = 9_000, record = 9_500),
        )

    @Test
    fun pausedRecordingAndStreamUseSeparateReportedCountersWithoutExtrapolation() {
        val times = bookmarkTimes(telemetry, token, 10_000)
        assertEquals(BookmarkOutputTime(BookmarkOutputState.ACTIVE, 90_005, 1_000), times.stream)
        assertEquals(BookmarkOutputTime(BookmarkOutputState.PAUSED, 12_345, 500), times.recording)
        assertEquals(12_345L, bookmarkTimes(telemetry, token, 11_000).recording.durationMs)
    }

    @Test
    fun samplesExpireIndependentlyAndFutureTimesAreUnknown() {
        val times = bookmarkTimes(telemetry, token, 12_001)
        assertEquals(BookmarkOutputState.UNKNOWN, times.stream.state)
        assertEquals(BookmarkOutputState.PAUSED, times.recording.state)
        assertEquals(BookmarkTimes(), bookmarkTimes(telemetry, token, 8_000))
    }

    @Test
    fun wrongSessionFailedReadsAndNegativeCountersAreUnknown() {
        assertEquals(BookmarkTimes(), bookmarkTimes(telemetry, ObsSessionToken(2, 2), 10_000))
        val invalid =
            telemetry.copy(
                stream = telemetry.stream!!.copy(durationMs = -1),
                receivedAt = telemetry.receivedAt.copy(record = null),
            )
        assertEquals(BookmarkTimes(), bookmarkTimes(invalid, token, 10_000))
    }

    @Test
    fun reconnectingStreamIsExplicitWithoutLosingItsReportedCounter() {
        val sample = telemetry.copy(stream = telemetry.stream!!.copy(reconnecting = true))
        val stamp = bookmarkTimes(sample, token, 10_000).stream
        assertEquals(BookmarkOutputState.RECONNECTING, stamp.state)
        assertEquals(90_005L, stamp.durationMs)
    }

    @Test
    fun inactiveOutputsDoNotInventZeroTimestamps() {
        val inactive =
            telemetry.copy(
                stream = telemetry.stream!!.copy(active = false),
                record = telemetry.record!!.copy(active = false),
            )
        val times = bookmarkTimes(inactive, token, 10_000)
        assertEquals(
            BookmarkOutputTime(BookmarkOutputState.INACTIVE, sampleAgeMs = 1_000),
            times.stream,
        )
        assertEquals(
            BookmarkOutputTime(BookmarkOutputState.INACTIVE, sampleAgeMs = 500),
            times.recording,
        )
    }
}
