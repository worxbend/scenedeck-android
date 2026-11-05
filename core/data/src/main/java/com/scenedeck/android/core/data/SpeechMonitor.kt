package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionMeters

/** Receives every shared sample but emits only on the alert repository's low-frequency ticks. */
internal class SpeechMonitor(val target: SpeechTarget) {
    private var lastAt: Long? = null
    private var quietSince: Long? = null
    private var peak: Float? = null
    private var heardSignal = false

    fun receive(batch: ObsSessionMeters, now: Long) {
        if (batch.token != target.origin.token) return
        val current = peakFrom(batch)
        if (!lastAt.isFresh(now, 2_000) || current == null) heardSignal = false
        if (current != null && current > SIGNAL_THRESHOLD) heardSignal = true
        if (!lastAt.isFresh(now, 2_000) || current == null || current > SIGNAL_THRESHOLD)
            quietSince = null
        if (current != null && current <= SIGNAL_THRESHOLD && quietSince == null) quietSince = now
        peak = current
        lastAt = if (current == null) null else now
    }

    private fun peakFrom(batch: ObsSessionMeters): Float? {
        val channels = batch.readings.firstOrNull { it.inputName == target.inputName }?.channels
        val valid = channels?.takeIf {
            it.isNotEmpty() &&
                it.all { channel -> channel.peakMul.isFinite() && channel.peakMul >= 0 }
        }
        return valid?.maxOf { it.peakMul }
    }

    fun reading(now: Long): AlertReading {
        val kind = LiveAlertKind.MICROPHONE
        if (!lastAt.isFresh(now, 2_000) || peak == null) return unknown(kind)
        val quietFor = quietSince?.let { lastAt!! - it } ?: 0
        val condition =
            when {
                heardSignal || peak!! > SIGNAL_THRESHOLD -> AlertCondition.CLEAR
                quietFor >= 10_000 -> AlertCondition.BAD
                else -> AlertCondition.HOLD
            }
        heardSignal = false
        return AlertReading(kind, condition, lastAt, quietFor / 1_000.0, 10.0)
    }

    private companion object {
        const val SIGNAL_THRESHOLD = 0.003162278f // -50 dB, post-fader.
    }
}
