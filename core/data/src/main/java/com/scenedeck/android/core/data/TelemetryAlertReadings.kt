package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.StreamStatus

/** Session-owned counter baseline. Reset by its owner on every connection/collection change. */
internal class TelemetryAlertReadings {
    private var previousStream: StreamStatus? = null
    private var previousStreamAt: Long? = null
    private var dropped = unknown(LiveAlertKind.DROPPED_FRAMES)

    fun read(telemetry: Telemetry?, now: Long, diskMb: Long?): List<AlertReading> =
        listOf(drops(telemetry, now), render(telemetry, now), disk(telemetry, now, diskMb))

    private fun drops(telemetry: Telemetry?, now: Long): AlertReading {
        val stream = telemetry?.stream
        val stamp = telemetry?.receivedAt?.stream
        if (!stamp.isFresh(now) || stream == null || !stream.validCounters()) {
            previousStream = null
            previousStreamAt = null
            return unknown(LiveAlertKind.DROPPED_FRAMES)
        }
        if (stamp != previousStreamAt) {
            val previous = previousStream
            val previousAt = previousStreamAt
            previousStream = stream
            previousStreamAt = stamp
            dropped =
                when {
                    !stream.active -> inactive(LiveAlertKind.DROPPED_FRAMES)
                    previous == null || !previous.active || !previousAt.isFresh(stamp!!) ->
                        unknown(LiveAlertKind.DROPPED_FRAMES)
                    else -> dropDelta(previous, stream, stamp)
                }
        }
        return dropped
    }

    private fun dropDelta(before: StreamStatus, after: StreamStatus, at: Long): AlertReading {
        val total = after.totalFrames.toLong() - before.totalFrames
        val skipped = after.skippedFrames.toLong() - before.skippedFrames
        if (total <= 0 || skipped !in 0..total || after.durationMs < before.durationMs) {
            return unknown(LiveAlertKind.DROPPED_FRAMES)
        }
        return reading(LiveAlertKind.DROPPED_FRAMES, skipped * 100.0 / total, 1.0, 0.5, at)
    }

    private fun render(telemetry: Telemetry?, now: Long): AlertReading {
        val kind = LiveAlertKind.RENDER_LOAD
        val active = activeOutput(telemetry, now)
        val stats = telemetry?.stats
        val at = telemetry?.receivedAt?.stats
        val fps = telemetry?.targetFps?.takeIf { it.isFinite() && it > 0 } ?: 60.0
        return when {
            active == false -> inactive(kind)
            active == null || !at.isFresh(now) || stats == null -> unknown(kind)
            else -> reading(kind, stats.averageFrameRenderTimeMs, 900.0 / fps, 700.0 / fps, at!!)
        }
    }

    private fun disk(telemetry: Telemetry?, now: Long, minimumMb: Long?): AlertReading {
        val kind = LiveAlertKind.DISK_SPACE
        val record = telemetry?.record?.takeIf { telemetry.receivedAt.record.isFresh(now) }
        val available =
            telemetry?.stats?.availableDiskSpaceMb?.takeIf {
                telemetry.receivedAt.stats.isFresh(now) && it.isFinite() && it >= 0
            }
        return when {
            record == null -> unknown(kind)
            !record.active -> inactive(kind)
            minimumMb == null || available == null -> unknown(kind)
            else -> diskReading(available, minimumMb, telemetry.receivedAt.stats!!)
        }
    }

    private fun diskReading(available: Double, minimumMb: Long, at: Long): AlertReading {
        val condition =
            when {
                available < minimumMb -> AlertCondition.BAD
                available >= minimumMb + maxOf(256.0, minimumMb * 0.1) -> AlertCondition.CLEAR
                else -> AlertCondition.HOLD
            }
        return AlertReading(
            LiveAlertKind.DISK_SPACE,
            condition,
            at,
            available,
            minimumMb.toDouble(),
        )
    }

    private fun activeOutput(telemetry: Telemetry?, now: Long): Boolean? {
        val stream = telemetry?.stream?.takeIf { telemetry.receivedAt.stream.isFresh(now) }?.active
        val record = telemetry?.record?.takeIf { telemetry.receivedAt.record.isFresh(now) }?.active
        return when {
            stream == true || record == true -> true
            stream == false && record == false -> false
            else -> null
        }
    }
}

internal fun unknown(kind: LiveAlertKind) = AlertReading(kind, AlertCondition.UNKNOWN)

internal fun inactive(kind: LiveAlertKind) = AlertReading(kind, AlertCondition.INACTIVE)

private fun reading(
    kind: LiveAlertKind,
    value: Double,
    bad: Double,
    clear: Double,
    at: Long,
): AlertReading {
    if (!value.isFinite() || value < 0) return unknown(kind)
    val condition =
        when {
            value >= bad -> AlertCondition.BAD
            value < clear -> AlertCondition.CLEAR
            else -> AlertCondition.HOLD
        }
    return AlertReading(kind, condition, at, value, bad)
}

private fun StreamStatus.validCounters() =
    totalFrames >= 0 && skippedFrames in 0..totalFrames && durationMs >= 0
