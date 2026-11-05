package com.scenedeck.android.core.data

import kotlinx.coroutines.channels.Channel

internal sealed interface HistoryCommand {
    val owner: String

    data class Initialize(override val owner: String, val reason: HistoryEndReason) : HistoryCommand

    data class Start(val seed: HistorySeed) : HistoryCommand {
        override val owner
            get() = seed.ownerId
    }

    data class Append(val write: HistoryAppend) : HistoryCommand {
        override val owner
            get() = write.ownerId
    }

    data class Touch(val position: HistoryPosition) : HistoryCommand {
        override val owner
            get() = position.ownerId
    }

    data class Finish(val position: HistoryPosition, val reason: HistoryEndReason) :
        HistoryCommand {
        override val owner
            get() = position.ownerId
    }
}

/** Only the capture engine submits synchronously; the writer suspends independently. */
internal class HistoryCaptureQueue(capacity: Int = 128) {
    val commands = Channel<HistoryCommand>(capacity)

    fun start(observation: HistoryObservation): Boolean =
        commands.trySend(HistoryCommand.Start(observation.seed)).isSuccess

    fun emit(
        observation: HistoryObservation,
        payloads: List<HistoryPayload>,
        wall: Long,
        mono: Long,
    ) {
        if (observation.needsGap) gap(observation, wall, mono)
        payloads.forEach { append(observation, it, wall, mono) }
    }

    fun tick(observation: HistoryObservation, wall: Long, mono: Long) {
        if (observation.needsGap) gap(observation, wall, mono)
        if (
            mono - observation.heartbeatAt >= 10_000 &&
                commands.trySend(HistoryCommand.Touch(observation.position(wall, mono))).isSuccess
        ) {
            observation.heartbeatAt = mono
        }
    }

    fun finish(
        observation: HistoryObservation,
        reason: HistoryEndReason,
        wall: Long,
        mono: Long,
    ): Boolean {
        if (observation.needsGap && !gap(observation, wall, mono)) return false
        return commands
            .trySend(HistoryCommand.Finish(observation.position(wall, mono), reason))
            .isSuccess
    }

    private fun gap(observation: HistoryObservation, wall: Long, mono: Long): Boolean {
        val saved =
            append(
                observation,
                HistoryPayload.Gap(HistoryGapReason.OBSERVATION_INTERRUPTED),
                wall,
                mono,
            )
        if (saved) observation.needsGap = false
        return saved
    }

    private fun append(
        observation: HistoryObservation,
        payload: HistoryPayload,
        wall: Long,
        mono: Long,
    ): Boolean {
        observation.ordinal++
        val write =
            HistoryAppend(
                observation.seed.id,
                observation.seed.ownerId,
                observation.ordinal,
                wall,
                (mono - observation.startedMonoMs).coerceAtLeast(0),
                payload,
            )
        val queued = commands.trySend(HistoryCommand.Append(write)).isSuccess
        if (!queued) {
            observation.dropped++
            observation.needsGap = true
        }
        return queued
    }
}
