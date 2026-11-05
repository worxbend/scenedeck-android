package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.RecordingPhase

/** Local observation evidence; never extrapolates an OBS counter or trusts a pre-event read. */
internal class HistoryOutputCapture(private val output: HistoryOutput) {
    private var state: HistoryOutputState? = null
    private var evidenceAt: Long? = null
    private var eventAt: Long? = null
    private var seenKnown = false

    fun event(event: ObsEvent, now: Long): HistoryPayload.Output? {
        val next =
            when {
                output == HistoryOutput.STREAM && event is ObsEvent.StreamStateChanged ->
                    if (event.reconnecting) HistoryOutputState.RECONNECTING
                    else activeState(event.active)
                output == HistoryOutput.RECORDING && event is ObsEvent.RecordStateChanged ->
                    if (event.phase == RecordingPhase.PAUSED) HistoryOutputState.PAUSED
                    else activeState(event.active)
                output == HistoryOutput.VIRTUAL_CAMERA &&
                    event is ObsEvent.VirtualcamStateChanged -> activeState(event.active)
                else -> return null
            }
        eventAt = now
        evidenceAt = now
        seenKnown = true
        return transition(next, initial = false)
    }

    fun snapshot(telemetry: Telemetry, now: Long): HistoryPayload.Output? {
        val reading = telemetry.reading(output)
        val afterEvent =
            eventAt?.let { reading.startedAt?.let { start -> start > it } == true } ?: true
        if (reading.receivedAt.isFresh(now) && afterEvent) {
            evidenceAt = reading.receivedAt
            val initial = !seenKnown
            seenKnown = true
            return transition(reading.state, initial, reading.durationMs)
        }
        return if (!evidenceAt.isFresh(now)) transition(HistoryOutputState.UNKNOWN, !seenKnown)
        else null
    }

    private fun transition(
        next: HistoryOutputState,
        initial: Boolean,
        durationMs: Long? = null,
    ): HistoryPayload.Output? {
        if (state == next) return null
        state = next
        return HistoryPayload.Output(output, next, initial, durationMs?.coerceAtLeast(0))
    }
}

private data class HistoryOutputReading(
    val state: HistoryOutputState,
    val receivedAt: Long?,
    val startedAt: Long?,
    val durationMs: Long? = null,
)

private fun activeState(active: Boolean) =
    if (active) HistoryOutputState.ACTIVE else HistoryOutputState.INACTIVE

private fun Telemetry.reading(output: HistoryOutput): HistoryOutputReading =
    when (output) {
        HistoryOutput.STREAM ->
            HistoryOutputReading(
                when {
                    stream == null -> HistoryOutputState.UNKNOWN
                    stream.reconnecting -> HistoryOutputState.RECONNECTING
                    else -> activeState(stream.active)
                },
                receivedAt.stream,
                streamReadStartedAtMs,
                stream?.durationMs?.takeIf { stream.active || stream.reconnecting },
            )
        HistoryOutput.RECORDING ->
            HistoryOutputReading(
                when {
                    record == null -> HistoryOutputState.UNKNOWN
                    record.paused -> HistoryOutputState.PAUSED
                    else -> activeState(record.active)
                },
                receivedAt.record,
                recordReadStartedAtMs,
                record?.durationMs?.takeIf { record.active },
            )
        HistoryOutput.VIRTUAL_CAMERA ->
            HistoryOutputReading(
                activeState(virtualCamActive),
                receivedAt.virtualCam,
                virtualCamReadStartedAtMs,
            )
    }
