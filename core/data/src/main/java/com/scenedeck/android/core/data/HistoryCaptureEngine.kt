package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionEvent
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No suspensions or DB work under this monitor. Captured events retain their original identity. */
internal class HistoryCaptureEngine(
    private val source: HistorySource,
    private val clock: MonotonicClock,
    private val wall: WallClock,
    private val queue: HistoryCaptureQueue,
) {
    @Volatile
    var owner: String = UUID.randomUUID().toString()
        private set

    private var initialized = false
    private var waitingForStorage = false
    private var settings: HistoryPreferences? = null
    private var observation: HistoryObservation? = null
    private var pendingGap = false
    private val current = MutableStateFlow(HistoryCaptureState())
    val state = current.asStateFlow()

    @Synchronized
    fun accepts(epoch: String): Boolean =
        owner == epoch && current.value.phase != HistoryCapturePhase.FAILED

    @Synchronized
    fun activate(epoch: String) {
        if (!accepts(epoch)) return
        initialized = true
        refresh()
    }

    @Synchronized
    fun storage(value: HistoryState) {
        if (value == HistoryState.Failed) {
            fail(owner, HistoryCaptureFailure.STORAGE)
            return
        }
        if (value !is HistoryState.Ready) return
        val previous = settings
        settings = value.preferences
        waitingForStorage = false
        if (
            !value.preferences.enabled ||
                (previous?.generation != value.preferences.generation &&
                    value.sessions.none { it.id == observation?.seed?.id })
        )
            observation = null
        refresh()
    }

    @Synchronized
    fun refresh(event: ObsSessionEvent? = null, epoch: String = owner) {
        val preferences =
            settings?.takeIf { accepts(epoch) && initialized && !waitingForStorage } ?: return
        if (!preferences.enabled) {
            current.value = HistoryCaptureState(HistoryCapturePhase.DISABLED)
        } else {
            val inputs = source.current()
            val now = clock.nowMs()
            val calendar = wall.nowMs()
            enter(inputs, preferences, now, calendar)?.let { active ->
                val payloads = active.capture(inputs, event, now)
                if (source.isCurrent(active.origin)) {
                    queue.emit(active, payloads, calendar, now)
                    queue.tick(active, calendar, now)
                    publish(active)
                }
            }
        }
    }

    private fun enter(
        inputs: HistoryInputs,
        preferences: HistoryPreferences,
        now: Long,
        calendar: Long,
    ): HistoryObservation? {
        if (observation?.origin != inputs.workspace) {
            val reason =
                if (inputs.connected) HistoryEndReason.WORKSPACE_CHANGED
                else HistoryEndReason.DISCONNECTED
            val closed = observation?.let { queue.finish(it, reason, calendar, now) } ?: true
            observation = null
            if (!closed) {
                fail(owner, HistoryCaptureFailure.OVERFLOW)
                return null
            }
        }
        val origin = inputs.workspace?.takeIf(source::isCurrent)
        return if (origin == null) {
            current.value = HistoryCaptureState(HistoryCapturePhase.WAITING)
            null
        } else
            observation
                ?: HistoryObservation(
                        origin,
                        HistorySeed(
                            UUID.randomUUID().toString(),
                            owner,
                            preferences.generation,
                            origin.key,
                            calendar,
                        ),
                        now,
                    )
                    .takeIf(queue::start)
                    ?.also {
                        it.needsGap = pendingGap
                        pendingGap = false
                        observation = it
                    }
                ?: run {
                    fail(owner, HistoryCaptureFailure.OVERFLOW)
                    null
                }
    }

    @Synchronized
    fun completed(command: HistoryCommand, available: Boolean) {
        if (!accepts(command.owner)) return
        val id =
            when (command) {
                is HistoryCommand.Start -> command.seed.id
                is HistoryCommand.Append -> command.write.sessionId
                is HistoryCommand.Touch -> command.position.sessionId
                else -> null
            }
        val active = observation?.takeIf { it.seed.id == id } ?: return
        if (available) {
            if (command is HistoryCommand.Start) active.persisted = true
            if (source.isCurrent(active.origin)) publish(active) else refresh()
        } else {
            observation = null
            waitingForStorage = true
            current.value = HistoryCaptureState(HistoryCapturePhase.WAITING)
        }
    }

    @Synchronized
    fun fail(epoch: String, failure: HistoryCaptureFailure) {
        if (owner != epoch) return
        observation = null
        current.value = HistoryCaptureState(HistoryCapturePhase.FAILED, failure = failure)
    }

    @Synchronized
    fun restart(): HistoryCommand.Initialize? {
        if (current.value.phase != HistoryCapturePhase.FAILED) return null
        val reason =
            when (current.value.failure) {
                HistoryCaptureFailure.OVERFLOW -> HistoryEndReason.CAPTURE_OVERFLOW
                HistoryCaptureFailure.SOURCE_ENDED -> HistoryEndReason.CAPTURE_INTERRUPTED
                else -> HistoryEndReason.STORAGE_FAILURE
            }
        owner = UUID.randomUUID().toString()
        initialized = false
        waitingForStorage = false
        observation = null
        pendingGap = true
        current.value = HistoryCaptureState()
        return HistoryCommand.Initialize(owner, reason)
    }

    @Synchronized
    fun link(origin: WorkspaceSession): HistoryBookmarkLink? {
        val active =
            observation?.takeIf { it.origin == origin && it.persisted && source.isCurrent(origin) }
                ?: return null
        return HistoryBookmarkLink(
            active.seed.id,
            (clock.nowMs() - active.startedMonoMs).coerceAtLeast(0),
        )
    }

    private fun publish(active: HistoryObservation) {
        current.value =
            HistoryCaptureState(
                if (active.persisted) HistoryCapturePhase.CAPTURING
                else HistoryCapturePhase.STARTING,
                active.seed.id,
                active.origin.key,
                active.dropped,
            )
    }
}
