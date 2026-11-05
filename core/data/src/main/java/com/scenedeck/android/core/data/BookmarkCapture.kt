package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionToken

internal fun bookmarkTimes(
    telemetry: Telemetry,
    token: ObsSessionToken,
    nowMs: Long,
): BookmarkTimes {
    if (telemetry.session != token) return BookmarkTimes()
    val stream = telemetry.stream
    val record = telemetry.record
    return BookmarkTimes(
        stream =
            outputTime(
                    stream?.active,
                    false,
                    stream?.durationMs,
                    telemetry.receivedAt.stream,
                    nowMs,
                )
                .let {
                    if (stream?.reconnecting == true && it.state == BookmarkOutputState.ACTIVE)
                        it.copy(state = BookmarkOutputState.RECONNECTING)
                    else it
                },
        recording =
            outputTime(
                record?.active,
                record?.paused == true,
                record?.durationMs,
                telemetry.receivedAt.record,
                nowMs,
            ),
    )
}

private fun outputTime(
    active: Boolean?,
    paused: Boolean,
    duration: Long?,
    received: Long?,
    now: Long,
): BookmarkOutputTime {
    val age = received?.let { now - it }
    return when {
        active == null || age == null || age !in 0..3_000 -> BookmarkOutputTime()
        !active -> BookmarkOutputTime(BookmarkOutputState.INACTIVE, sampleAgeMs = age)
        duration == null || duration < 0 -> BookmarkOutputTime()
        else ->
            BookmarkOutputTime(
                if (paused) BookmarkOutputState.PAUSED else BookmarkOutputState.ACTIVE,
                duration,
                age,
            )
    }
}
