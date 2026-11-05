package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.ObsSessionToken

/** Serialized by LiveAlertsRepository. Pure clock-driven state machine, with no OBS writes. */
internal class LiveAlertEngine(private val isCurrent: (WorkspaceSession) -> Boolean) {
    private data class Origin(val target: ConnectionTarget, val token: ObsSessionToken)

    private var origin: Origin? = null
    private var nextId = 0L
    private var counterReadings = TelemetryAlertReadings()
    private var speech: SpeechMonitor? = null
    private val conditions =
        LiveAlertKind.entries.associateWith { kind ->
            SustainedAlert(
                kind,
                onsetMs =
                    when (kind) {
                        LiveAlertKind.CONNECTION -> 2_000
                        LiveAlertKind.MICROPHONE -> 0
                        else -> 5_000
                    },
                recoveryMs = if (kind == LiveAlertKind.MICROPHONE) 0 else 5_000,
            ) {
                ++nextId
            }
        }
    var state = LiveAlertsState()
        private set

    fun update(
        connection: ConnectionState,
        target: ConnectionTarget,
        telemetry: Telemetry,
        setup: PreflightSetupState?,
        now: Long,
    ) {
        val ready = connection as? ConnectionState.Ready
        if (!target.confirmed || target.profileId == null || ready?.collectionChanging == true) {
            clearContext()
            return
        }
        if (ready != null) {
            val next = Origin(target, ready.token)
            if (origin != next) {
                clearContext()
                origin = next
            }
        } else if (origin?.target != target) {
            clearContext()
            return
        }
        val configuration = (setup as? PreflightSetupState.Ready)?.takeIf { validSetup(it, ready) }
        val microphone =
            configuration?.config?.microphone?.let { SpeechTarget(configuration.origin, it) }
        if (speech?.target != microphone) stopSpeech()
        val threshold = configuration?.config?.minimumDiskMb
        if (ready != null && state.diskThresholdMb != threshold)
            conditions.getValue(LiveAlertKind.DISK_SPACE).invalidateEvidence()
        val currentTelemetry = telemetry.takeIf { ready != null && it.session == ready.token }
        val readings =
            counterReadings.read(currentTelemetry, now, threshold) +
                connectionReading(ready, now) +
                (speech?.reading(now) ?: inactive(LiveAlertKind.MICROPHONE))
        readings.forEach { conditions.getValue(it.kind).observe(it, now) }
        state =
            state.copy(
                readings = readings,
                alerts = conditions.values.mapNotNull { it.alert },
                speechTarget = microphone,
                armedSpeech = speech?.target,
                diskThresholdMb = threshold,
                connected = ready != null,
            )
    }

    private fun validSetup(
        setup: PreflightSetupState.Ready,
        ready: ConnectionState.Ready?,
    ): Boolean {
        val target = origin?.target ?: return false
        return ready != null &&
            setup.origin.token == ready.token &&
            setup.origin.targetGeneration == target.generation &&
            setup.origin.key.profileId == target.profileId &&
            isCurrent(setup.origin)
    }

    private fun connectionReading(ready: ConnectionState.Ready?, now: Long) =
        if (ready == null) AlertReading(LiveAlertKind.CONNECTION, AlertCondition.BAD, now)
        else inactive(LiveAlertKind.CONNECTION)

    private fun clearContext() {
        origin = null
        counterReadings = TelemetryAlertReadings()
        conditions.values.forEach { it.reset() }
        speech = null
        state = LiveAlertsState(hapticsEnabled = state.hapticsEnabled)
    }

    fun armSpeech(target: SpeechTarget): Boolean {
        if (speech != null || state.speechTarget != target || !isCurrent(target.origin))
            return false
        speech = SpeechMonitor(target)
        conditions.getValue(LiveAlertKind.MICROPHONE).reset()
        state =
            state.copy(
                armedSpeech = target,
                readings =
                    state.readings.map {
                        if (it.kind == LiveAlertKind.MICROPHONE) unknown(it.kind) else it
                    },
                alerts = state.alerts.filterNot { it.kind == LiveAlertKind.MICROPHONE },
            )
        return true
    }

    fun stopSpeech() {
        speech = null
        conditions.getValue(LiveAlertKind.MICROPHONE).reset()
        state =
            state.copy(
                armedSpeech = null,
                readings =
                    state.readings.map {
                        if (it.kind == LiveAlertKind.MICROPHONE) inactive(it.kind) else it
                    },
                alerts = state.alerts.filterNot { it.kind == LiveAlertKind.MICROPHONE },
            )
    }

    fun receiveMeters(batch: ObsSessionMeters, now: Long) {
        val monitor = speech ?: return
        if (isCurrent(monitor.target.origin)) monitor.receive(batch, now)
    }

    fun dismiss(kind: LiveAlertKind, occurrence: Long, now: Long) {
        val condition = conditions.getValue(kind)
        if (condition.alert?.occurrence == occurrence) condition.dismiss(now)
        state = state.copy(alerts = conditions.values.mapNotNull { it.alert })
    }

    fun setHaptics(enabled: Boolean) {
        state = state.copy(hapticsEnabled = enabled)
    }
}
