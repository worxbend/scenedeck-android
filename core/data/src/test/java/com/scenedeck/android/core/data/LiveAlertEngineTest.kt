package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ChannelLevels
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.VolumeMeterReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAlertEngineTest {
    @Test
    fun dropsUseRecentDeltasAndRequireSustainedSamplesWithRecoveryHysteresis() {
        val fixture = Fixture()
        // Large historical losses do not warn when recent frames are healthy.
        repeat(7) { second ->
            fixture.sample(second * 1_000L, Sample(skipped = 5_000, total = 10_000 + second * 100))
        }
        assertNull(fixture.alert(LiveAlertKind.DROPPED_FRAMES))
        repeat(6) { second ->
            fixture.sample(
                (second + 7) * 1_000L,
                Sample(skipped = 5_002 + second * 2, total = 10_700 + second * 100),
            )
        }
        val warning = fixture.alert(LiveAlertKind.DROPPED_FRAMES)
        assertNotNull(warning)
        repeat(5) { second ->
            fixture.sample(
                (second + 13) * 1_000L,
                Sample(skipped = 5_012, total = 11_300 + second * 100),
            )
        }
        assertEquals(warning, fixture.alert(LiveAlertKind.DROPPED_FRAMES))
        fixture.sample(18_000, Sample(skipped = 5_012, total = 11_800))
        assertNull(fixture.alert(LiveAlertKind.DROPPED_FRAMES))
    }

    @Test
    fun counterResetInvalidValuesAndStaleRepeatedSamplesCannotAccumulateWarnings() {
        val fixture = Fixture()
        fixture.sample(0, Sample(skipped = 20, total = 100))
        fixture.sample(1_000, Sample(skipped = 22, total = 200))
        fixture.sample(2_000, Sample(skipped = 0, total = 0))
        fixture.sample(3_000, Sample(skipped = -1, total = 100))
        fixture.sample(4_000, Sample(skipped = 500, total = 100))
        fixture.sample(5_000, Sample(skipped = 1, total = 100))
        fixture.sample(6_000, Sample(skipped = 3, total = 200))
        repeat(8) { second -> fixture.update((second + 7) * 1_000L) }
        assertNull(fixture.alert(LiveAlertKind.DROPPED_FRAMES))
        assertEquals(
            AlertCondition.UNKNOWN,
            fixture.reading(LiveAlertKind.DROPPED_FRAMES).condition,
        )
    }

    @Test
    fun recordingOnlyUsesCanvasRenderBudgetAndDiskThresholdIncludingPausedRecording() {
        val fixture = Fixture()
        repeat(6) { second ->
            fixture.sample(
                second * 1_000L,
                Sample(stream = false, record = true, renderMs = 20.0, fps = 30.0, disk = 1_000.0),
            )
        }
        assertNull(fixture.alert(LiveAlertKind.RENDER_LOAD)) // 20 ms fits the 30 fps budget.
        assertNotNull(fixture.alert(LiveAlertKind.DISK_SPACE))
        assertEquals(
            AlertCondition.INACTIVE,
            fixture.reading(LiveAlertKind.DROPPED_FRAMES).condition,
        )
        repeat(6) { second ->
            fixture.sample(
                (second + 6) * 1_000L,
                Sample(stream = false, record = true, renderMs = 20.0, fps = 60.0, disk = 1_000.0),
            )
        }
        assertNotNull(fixture.alert(LiveAlertKind.RENDER_LOAD))
        fixture.sample(12_000, Sample(stream = false, record = false))
        assertTrue(fixture.engine.state.alerts.isEmpty())
    }

    @Test
    fun missingReadsAreUnknownAndNeverClearAnExistingWarning() {
        val fixture = Fixture()
        repeat(6) { second -> fixture.sample(second * 1_000L, Sample(renderMs = 30.0)) }
        val warning = fixture.alert(LiveAlertKind.RENDER_LOAD)!!
        fixture.update(9_000) // No new samples; earlier evidence is stale.
        assertEquals(warning.occurrence, fixture.alert(LiveAlertKind.RENDER_LOAD)?.occurrence)
        assertTrue(fixture.alert(LiveAlertKind.RENDER_LOAD)!!.uncertain)
        fixture.sample(10_000, Sample(renderMs = Double.NaN))
        assertTrue(fixture.alert(LiveAlertKind.RENDER_LOAD)!!.uncertain)
        repeat(5) { second -> fixture.sample((second + 11) * 1_000L) }
        assertNotNull(fixture.alert(LiveAlertKind.RENDER_LOAD))
        fixture.sample(16_000)
        assertNull(fixture.alert(LiveAlertKind.RENDER_LOAD))
    }

    @Test
    fun dismissalCoolsDownAndOldDismissalsCannotHideANewOccurrence() {
        val fixture = Fixture()
        repeat(6) { second -> fixture.sample(second * 1_000L, Sample(renderMs = 30.0)) }
        val first = fixture.alert(LiveAlertKind.RENDER_LOAD)!!
        fixture.engine.dismiss(first.kind, first.occurrence, 5_000)
        repeat(59) { second -> fixture.sample((second + 6) * 1_000L, Sample(renderMs = 30.0)) }
        assertNull(fixture.alert(first.kind))
        fixture.sample(65_000, Sample(renderMs = 30.0))
        val next = fixture.alert(first.kind)!!
        assertTrue(next.occurrence > first.occurrence)
        fixture.engine.dismiss(first.kind, first.occurrence, 65_000)
        assertEquals(next, fixture.alert(first.kind))
    }

    @Test
    fun unexpectedLossWarnsButDeliberateReplacementAndCollectionChangesClearContext() {
        val fixture = Fixture()
        fixture.sample(0)
        fixture.connection = ConnectionState.Reconnecting(1)
        fixture.update(1_000)
        fixture.update(3_000)
        assertNotNull(fixture.alert(LiveAlertKind.CONNECTION))
        fixture.target = fixture.target.copy(confirmed = false, generation = 2)
        fixture.update(4_000)
        assertTrue(fixture.engine.state.alerts.isEmpty())
        fixture.target = fixture.target.copy(confirmed = true)
        fixture.connection = READY.copy(sessionId = 2)
        fixture.update(5_000) // Previous telemetry is from session 0.
        assertEquals(AlertCondition.UNKNOWN, fixture.reading(LiveAlertKind.RENDER_LOAD).condition)
        fixture.connection = READY.copy(collectionChanging = true)
        fixture.update(6_000)
        assertFalse(fixture.engine.state.connected)
        assertTrue(fixture.engine.state.readings.isEmpty())
    }

    @Test
    fun diskConfigurationFailureIsUnknownAndChangedThresholdNeedsFreshRecovery() {
        val fixture = Fixture()
        repeat(6) { second ->
            fixture.sample(second * 1_000L, Sample(record = true, disk = 1_000.0))
        }
        assertNotNull(fixture.alert(LiveAlertKind.DISK_SPACE))
        fixture.setup = PreflightSetupState.Failed(TEST_WORKSPACE)
        fixture.sample(6_000, Sample(record = true, disk = 1_000.0))
        assertEquals(AlertCondition.UNKNOWN, fixture.reading(LiveAlertKind.DISK_SPACE).condition)
        assertTrue(fixture.alert(LiveAlertKind.DISK_SPACE)!!.uncertain)
        fixture.setup =
            PreflightSetupState.Ready(
                TEST_WORKSPACE,
                PreflightConfig("Studio", minimumDiskMb = 500),
            )
        repeat(5) { second ->
            fixture.sample((second + 7) * 1_000L, Sample(record = true, disk = 1_000.0))
        }
        assertNotNull(fixture.alert(LiveAlertKind.DISK_SPACE))
        fixture.sample(12_000, Sample(record = true, disk = 1_000.0))
        assertNull(fixture.alert(LiveAlertKind.DISK_SPACE))
    }

    @Test
    fun speechRequiresConsentValidContinuousSamplesAndRearmingAfterReplacement() {
        val fixture = Fixture()
        fixture.sample(0)
        fixture.meter(0, 0f)
        fixture.update(20_000)
        assertNull(fixture.engine.state.armedSpeech)
        val target = fixture.engine.state.speechTarget!!
        assertTrue(fixture.engine.armSpeech(target))
        repeat(11) { second ->
            fixture.meter((second + 21) * 1_000L, 0f)
            fixture.sample((second + 21) * 1_000L)
        }
        assertNotNull(fixture.alert(LiveAlertKind.MICROPHONE))
        // A brief signal between UI ticks must reset quiet time and recover immediately.
        fixture.meter(31_100, 0.1f)
        fixture.meter(31_200, 0f)
        fixture.sample(32_000)
        assertNull(fixture.alert(LiveAlertKind.MICROPHONE))
        fixture.update(35_000)
        assertEquals(AlertCondition.UNKNOWN, fixture.reading(LiveAlertKind.MICROPHONE).condition)
        fixture.connection = ConnectionState.Reconnecting(1)
        fixture.update(36_000)
        assertNull(fixture.engine.state.armedSpeech)
        assertFalse(fixture.engine.armSpeech(target))
    }

    @Test
    fun missingInvalidOrWrongSessionMetersAreUnknownAndClockTicksDoNotProveSilence() {
        val fixture = Fixture()
        fixture.sample(0)
        assertTrue(fixture.engine.armSpeech(fixture.engine.state.speechTarget!!))
        fixture.meter(0, 0f)
        fixture.meter(9_000, 0f) // Gap resets continuity.
        fixture.update(10_000)
        assertNull(fixture.alert(LiveAlertKind.MICROPHONE))
        fixture.meter(11_000, Float.NaN)
        fixture.update(11_000)
        assertEquals(AlertCondition.UNKNOWN, fixture.reading(LiveAlertKind.MICROPHONE).condition)
        fixture.engine.receiveMeters(
            ObsSessionMeters(ObsSessionToken(99), listOf(meterReading(0f))),
            12_000,
        )
        fixture.update(12_000)
        assertEquals(AlertCondition.UNKNOWN, fixture.reading(LiveAlertKind.MICROPHONE).condition)
        assertNull(fixture.alert(LiveAlertKind.MICROPHONE))
    }

    @Test
    fun unknownCanvasUsesSixtyAndNeutralRenderLoadDoesNotClaimRecovery() {
        val fixture = Fixture()
        repeat(6) { second -> fixture.sample(second * 1_000L, Sample(fps = null, renderMs = 16.0)) }
        val warning = fixture.alert(LiveAlertKind.RENDER_LOAD)
        assertNotNull(warning)
        repeat(10) { second -> fixture.sample((second + 6) * 1_000L, Sample(renderMs = 13.0)) }
        assertEquals(warning, fixture.alert(LiveAlertKind.RENDER_LOAD))
        repeat(6) { second ->
            fixture.sample((second + 16) * 1_000L, Sample(fps = 30.0, renderMs = 16.0))
        }
        assertNull(fixture.alert(LiveAlertKind.RENDER_LOAD))
    }

    private data class Sample(
        val skipped: Int = 0,
        val total: Int? = null,
        val stream: Boolean = true,
        val record: Boolean = false,
        val renderMs: Double = 1.0,
        val fps: Double? = 60.0,
        val disk: Double = 5_000.0,
    )

    private class Fixture {
        val engine = LiveAlertEngine { it == TEST_WORKSPACE }
        var connection: ConnectionState = READY
        var target =
            ConnectionTarget(TEST_WORKSPACE.key.profileId, TEST_WORKSPACE.targetGeneration, true)
        var setup: PreflightSetupState =
            PreflightSetupState.Ready(TEST_WORKSPACE, PreflightConfig("Studio", microphone = "Mic"))
        private var telemetry = Telemetry()

        fun sample(now: Long, options: Sample = Sample()) =
            with(options) {
                val total = this.total ?: (now / 10).toInt() + 100
                telemetry =
                    Telemetry(
                        connection = connection,
                        session = (connection as? ConnectionState.Ready)?.token,
                        stats = ObsStats(1.0, 1.0, disk, 60.0, renderMs, 0, total, 0, total),
                        stream = StreamStatus(stream, false, "", now, 0, 0.0, skipped, total),
                        record = RecordStatus(record, true, "", now, 0),
                        targetFps = fps,
                        receivedAt = TelemetryReadTimes(now, now, now),
                    )
                update(now)
            }

        fun update(now: Long) = engine.update(connection, target, telemetry, setup, now)

        fun alert(kind: LiveAlertKind) = engine.state.alerts.firstOrNull { it.kind == kind }

        fun reading(kind: LiveAlertKind) = engine.state.readings.first { it.kind == kind }

        fun meter(now: Long, peak: Float) =
            engine.receiveMeters(
                ObsSessionMeters(TEST_WORKSPACE.token, listOf(meterReading(peak))),
                now,
            )
    }

    private companion object {
        val READY = ConnectionState.Ready(ObsVersionInfo("31", "5", 1, "test"))

        fun meterReading(peak: Float) =
            VolumeMeterReading("Mic", listOf(ChannelLevels(peak, peak, peak)))
    }
}
