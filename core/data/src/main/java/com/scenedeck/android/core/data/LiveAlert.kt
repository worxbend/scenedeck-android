package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionToken

/** Current evidence, distinct from the sustained warning shown to the operator. */
enum class AlertCondition {
    INACTIVE,
    UNKNOWN,
    CLEAR,
    HOLD,
    BAD,
}

enum class LiveAlertKind {
    CONNECTION,
    DROPPED_FRAMES,
    RENDER_LOAD,
    DISK_SPACE,
    MICROPHONE,
}

data class AlertReading(
    val kind: LiveAlertKind,
    val condition: AlertCondition,
    val sampleAtMs: Long? = null,
    val value: Double? = null,
    val threshold: Double? = null,
)

data class LiveAlert(
    val kind: LiveAlertKind,
    val occurrence: Long,
    /** Evidence has become unavailable since this warning; this is not a recovery. */
    val uncertain: Boolean = false,
)

data class SpeechTarget(val origin: WorkspaceSession, val inputName: String)

data class LiveAlertOrigin(
    val profileId: Long,
    val targetGeneration: Long,
    val token: ObsSessionToken,
) {
    fun matches(workspace: WorkspaceSession): Boolean =
        profileId == workspace.key.profileId &&
            targetGeneration == workspace.targetGeneration &&
            token == workspace.token
}

data class LiveAlertsState(
    val readings: List<AlertReading> = emptyList(),
    val alerts: List<LiveAlert> = emptyList(),
    val speechTarget: SpeechTarget? = null,
    val armedSpeech: SpeechTarget? = null,
    val hapticsEnabled: Boolean = false,
    val diskThresholdMb: Long? = null,
    val connected: Boolean = false,
    val origin: LiveAlertOrigin? = null,
)

internal const val ALERT_MAX_SAMPLE_AGE_MS = 3_000L
internal const val ALERT_COOLDOWN_MS = 60_000L

internal fun Long?.isFresh(now: Long, maxAge: Long = ALERT_MAX_SAMPLE_AGE_MS) =
    this != null && now - this in 0..maxAge

/** One warning's hysteresis. Clock ticks cannot accumulate duplicated sample evidence. */
internal class SustainedAlert(
    private val kind: LiveAlertKind,
    private val onsetMs: Long = 5_000,
    private val recoveryMs: Long = 5_000,
    private val nextOccurrence: () -> Long,
) {
    var alert: LiveAlert? = null
        private set

    private var lastSample: Long? = null
    private var badSince: Long? = null
    private var clearSince: Long? = null
    private var dismissedUntil = Long.MIN_VALUE

    fun observe(reading: AlertReading, now: Long) {
        when (reading.condition) {
            AlertCondition.INACTIVE -> reset()
            AlertCondition.UNKNOWN -> {
                resetEvidence()
                alert = alert?.copy(uncertain = true)
            }
            else -> observeKnown(reading, now)
        }
    }

    private fun observeKnown(reading: AlertReading, now: Long) {
        val stamp = reading.sampleAtMs ?: return
        if (stamp == lastSample) return
        if (lastSample?.let { stamp - it !in 0..ALERT_MAX_SAMPLE_AGE_MS } == true) resetEvidence()
        lastSample = stamp
        alert = alert?.copy(uncertain = false)
        when (reading.condition) {
            AlertCondition.BAD -> observeBad(stamp, now)
            AlertCondition.CLEAR -> observeClear(stamp)
            else -> {
                badSince = null
                clearSince = null
            }
        }
    }

    private fun observeBad(stamp: Long, now: Long) {
        clearSince = null
        val start = badSince ?: stamp.also { badSince = it }
        if (alert == null && stamp - start >= onsetMs && now >= dismissedUntil) {
            alert = LiveAlert(kind, nextOccurrence())
        }
    }

    private fun observeClear(stamp: Long) {
        badSince = null
        val start = clearSince ?: stamp.also { clearSince = it }
        if (stamp - start >= recoveryMs) alert = null
    }

    fun invalidateEvidence() {
        resetEvidence()
        alert = alert?.copy(uncertain = true)
    }

    fun dismiss(now: Long) {
        alert = null
        dismissedUntil = now + ALERT_COOLDOWN_MS
        resetEvidence()
    }

    fun reset() {
        alert = null
        dismissedUntil = Long.MIN_VALUE
        resetEvidence()
    }

    private fun resetEvidence() {
        lastSample = null
        badSince = null
        clearSince = null
    }
}
