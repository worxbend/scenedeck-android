package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the existing shared meter stream, filtering stamped origin before inspecting values. */
internal suspend fun observePreflightSignal(
    client: ObsClient,
    origin: WorkspaceSession,
    name: String?,
): PreflightFinding {
    if (name == null)
        return PreflightFinding(
            "signal",
            "Microphone signal",
            PreflightStatus.UNKNOWN,
            "Choose a microphone in Setup.",
        )
    var sampled = false
    val observed = requestResult {
        withTimeoutOrNull(4_000) {
            client.metersWithContext.first { batch ->
                if (batch.token != origin.token) return@first false
                val peaks =
                    batch.readings
                        .filter { it.inputName == name }
                        .flatMap { it.channels }
                        .map { it.peakMul }
                        .filter { it.isFinite() && it >= 0f }
                if (peaks.isNotEmpty()) sampled = true
                peaks.any { it > SIGNAL_THRESHOLD_MUL }
            }
        }
    }
    return signalFinding(name, observed.getOrNull() != null, sampled && observed.isSuccess)
}

private fun signalFinding(name: String, detected: Boolean, sampled: Boolean) =
    PreflightFinding(
        "signal",
        "Microphone signal",
        when {
            detected -> PreflightStatus.PASS
            sampled -> PreflightStatus.FAIL
            else -> PreflightStatus.UNKNOWN
        },
        when {
            detected -> "Signal detected from $name during this check."
            sampled ->
                "No signal above -50 dB from $name in four seconds. Speak and re-run if needed."
            else -> "No valid meter samples from $name. Signal is unknown."
        },
    )

private const val SIGNAL_THRESHOLD_MUL = 0.003162278f
