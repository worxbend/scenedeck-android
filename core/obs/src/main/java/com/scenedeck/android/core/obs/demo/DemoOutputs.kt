package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.RecordingPhase
import com.scenedeck.android.core.model.StreamStatus

internal class DemoOutputs(clock: () -> Long, private val emit: (ObsEvent) -> Unit) {
    private val stream = DemoElapsed(clock)
    private val record = DemoElapsed(clock)
    var camera = false
        private set

    fun streamStatus(): StreamStatus {
        val elapsed = stream.duration()
        return StreamStatus(
            stream.active,
            false,
            demoTimecode(elapsed),
            elapsed,
            elapsed * 750,
            0.0,
            0,
            (elapsed * 60 / 1_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }

    fun recordStatus(): RecordStatus {
        val elapsed = record.duration()
        return RecordStatus(
            record.active,
            record.paused,
            demoTimecode(elapsed),
            elapsed,
            elapsed * 1_500,
        )
    }

    fun stream(active: Boolean) {
        if (stream.active == active) return
        if (active) stream.start() else stream.stop()
        emit(
            ObsEvent.StreamStateChanged(
                active,
                if (active) "OBS_WEBSOCKET_OUTPUT_STARTED" else "OBS_WEBSOCKET_OUTPUT_STOPPED",
            )
        )
    }

    fun record(active: Boolean) {
        if (record.active == active) return
        if (active) record.start() else record.stop()
        recordingEvent(if (active) RecordingPhase.STARTED else RecordingPhase.STOPPED)
    }

    fun paused(value: Boolean) {
        demoRequire(record.active)
        if (record.paused == value) return
        record.pause(value)
        recordingEvent(if (value) RecordingPhase.PAUSED else RecordingPhase.RESUMED)
    }

    fun advancedRecording() = demoRequire(record.active && !record.paused)

    fun toggleCamera(): Boolean {
        camera = !camera
        emit(
            ObsEvent.VirtualcamStateChanged(
                camera,
                if (camera) "OBS_WEBSOCKET_OUTPUT_STARTED" else "OBS_WEBSOCKET_OUTPUT_STOPPED",
            )
        )
        return camera
    }

    private fun recordingEvent(phase: RecordingPhase) {
        emit(
            ObsEvent.RecordStateChanged(
                record.active,
                "OBS_WEBSOCKET_OUTPUT_${phase.name}",
                null,
                phase,
            )
        )
    }
}
