package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.HistoryEventEntity
import com.scenedeck.android.core.database.HistoryPreferencesEntity
import com.scenedeck.android.core.database.HistorySessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_HISTORY_PAYLOAD_LENGTH = 4_096
private val historyJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class StoredHistoryPayload(val version: Int = 1, val event: HistoryPayload)

internal fun checkHistory(valid: Boolean) {
    if (!valid) throw HistoryStorageException()
}

internal fun String.checkedHistoryId(): String = also { checkHistory(length in 1..128) }

internal fun WorkspaceKey.checkedHistoryWorkspace(): WorkspaceKey = also {
    checkHistory(profileId > 0 && collectionName.length in 1..256)
}

internal fun HistoryPreferencesEntity.decodeHistory(): HistoryPreferences {
    checkHistory(id == 0 && sessionLimit in setOf(10, 30, 100) && generation >= 0)
    return HistoryPreferences(enabled, sessionLimit, generation)
}

internal fun HistorySeed.encodeHistory(): HistorySessionEntity {
    workspace.checkedHistoryWorkspace()
    checkHistory(generation >= 0)
    return HistorySessionEntity(
        sessionId = id.checkedHistoryId(),
        ownerId = ownerId.checkedHistoryId(),
        generation = generation,
        connectionProfileId = workspace.profileId,
        sceneCollectionName = workspace.collectionName,
        startedAtMs = startedAtMs,
        lastObservedAtMs = startedAtMs,
    )
}

internal fun HistorySessionEntity.decodeHistory(): HistorySession {
    ownerId.checkedHistoryId()
    checkHistory(generation >= 0)
    checkHistory(elapsedMs >= 0 && lastOrdinal in 0 until Long.MAX_VALUE)
    checkHistory(omittedEvents in 0..lastOrdinal && missingEvents in 0..lastOrdinal)
    checkHistory(omittedEvents <= lastOrdinal - missingEvents)
    checkHistory(endedAtMs == null || endReason != null)
    val reason = endReason?.let { HistoryEndReason.valueOf(it) }
    return HistorySession(
        sessionId.checkedHistoryId(),
        WorkspaceKey(connectionProfileId, sceneCollectionName).checkedHistoryWorkspace(),
        startedAtMs,
        lastObservedAtMs,
        elapsedMs,
        endedAtMs,
        reason,
        lastOrdinal,
        omittedEvents,
        missingEvents,
    )
}

internal fun HistoryPayload.encodeHistory(): String {
    checkedHistoryPayload()
    return historyJson.encodeToString(StoredHistoryPayload(event = this)).also {
        checkHistory(it.length <= MAX_HISTORY_PAYLOAD_LENGTH)
    }
}

internal fun HistoryEventEntity.decodeHistory(): HistoryEvent {
    checkHistory(
        ordinal in 1 until Long.MAX_VALUE && elapsedMs >= 0 && missingBefore in 0 until ordinal
    )
    checkHistory(payload.length <= MAX_HISTORY_PAYLOAD_LENGTH)
    val stored = historyJson.decodeFromString<StoredHistoryPayload>(payload)
    checkHistory(stored.version == 1)
    stored.event.checkedHistoryPayload()
    return HistoryEvent(ordinal, observedAtMs, elapsedMs, missingBefore, stored.event)
}

internal fun HistoryPayload.checkedHistoryPayload() {
    when (this) {
        is HistoryPayload.Output -> checkOutput()
        is HistoryPayload.Program -> checkHistory(sceneName == null || sceneName.length in 1..256)
        is HistoryPayload.Warning -> checkHistory(occurrence > 0)
        is HistoryPayload.Gap -> Unit
    }
}

private fun HistoryPayload.Output.checkOutput() {
    checkHistory(durationMs == null || durationMs >= 0)
    checkHistory(state != HistoryOutputState.PAUSED || output == HistoryOutput.RECORDING)
    checkHistory(state != HistoryOutputState.RECONNECTING || output == HistoryOutput.STREAM)
    val withoutDuration =
        output == HistoryOutput.VIRTUAL_CAMERA ||
            state == HistoryOutputState.UNKNOWN ||
            state == HistoryOutputState.INACTIVE
    checkHistory(!withoutDuration || durationMs == null)
}
