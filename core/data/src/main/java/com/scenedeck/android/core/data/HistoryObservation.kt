package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent

/** Reduced synchronously before enqueueing, so rapid A → B → A events cannot conflate. */
internal class HistoryObservation(
    val origin: WorkspaceSession,
    val seed: HistorySeed,
    val startedMonoMs: Long,
) {
    var persisted = false
    var ordinal = 0L
    var dropped = 0L
    var needsGap = false
    var heartbeatAt = startedMonoMs
    private val outputs = HistoryOutput.entries.map(::HistoryOutputCapture)
    private var program: String? = null
    private var programPublished = false
    private var programKnown = false
    private var programEventSeen = false
    private var warnings = emptyList<LiveAlert>()

    fun capture(
        inputs: HistoryInputs,
        received: ObsSessionEvent?,
        now: Long,
    ): List<HistoryPayload> = buildList {
        if (received?.token == origin.token) addAll(event(received.event, now))
        addAll(snapshot(inputs, now))
    }

    fun snapshot(inputs: HistoryInputs, now: Long): List<HistoryPayload> = buildList {
        val telemetry = inputs.telemetry.takeIf { it.session == origin.token } ?: Telemetry()
        outputs.mapNotNullTo(this) { it.snapshot(telemetry, now) }
        val scene = inputs.program?.takeIf { it.origin == origin }?.name
        val needsProgramSeed = !programEventSeen && !programKnown
        if (needsProgramSeed && (scene != null || !programPublished)) {
            program = scene
            programKnown = scene != null
            programPublished = true
            add(HistoryPayload.Program(scene, true))
        }
        addAll(warningChanges(inputs.alerts))
    }

    fun event(event: ObsEvent, now: Long): List<HistoryPayload> = buildList {
        outputs.mapNotNullTo(this) { it.event(event, now) }
        val scene =
            when (event) {
                is ObsEvent.CurrentProgramSceneChanged -> event.sceneName
                is ObsEvent.SceneNameChanged ->
                    event.sceneName.takeIf { event.oldSceneName == program }
                else -> null
            }
        if (scene != null) {
            programEventSeen = true
            if (scene != program || !programPublished) add(HistoryPayload.Program(scene, false))
            program = scene
            programKnown = true
            programPublished = true
        }
    }

    fun position(wall: Long, mono: Long) =
        HistoryPosition(seed.id, seed.ownerId, wall, (mono - startedMonoMs).coerceAtLeast(0))

    private fun warningChanges(state: LiveAlertsState): List<HistoryPayload.Warning> {
        if (state.origin?.matches(origin) != true) return emptyList()
        val previous = warnings.associateBy { it.kind }
        val next = state.alerts.associateBy { it.kind }
        warnings = state.alerts
        return buildList {
            previous.values
                .filter { it.kind !in next }
                .forEach {
                    add(it.toHistory(HistoryWarningPhase.CLEARED_OR_DISMISSED))
                }
            next.values
                .filter { it != previous[it.kind] }
                .forEach {
                    add(
                        it.toHistory(
                            if (it.uncertain) HistoryWarningPhase.UNCERTAIN
                            else HistoryWarningPhase.SHOWN
                        )
                    )
                }
        }
    }
}

private fun LiveAlert.toHistory(phase: HistoryWarningPhase) =
    HistoryPayload.Warning(HistoryWarning.valueOf(kind.name), phase, occurrence)
