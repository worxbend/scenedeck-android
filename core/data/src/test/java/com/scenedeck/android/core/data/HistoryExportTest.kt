package com.scenedeck.android.core.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExportTest : HistoryTestFixture() {
    @Test
    fun exportEscapesNamesAndKeepsUnknownStateWithoutInternalMetadata(): Unit = runBlocking {
        val name = "Studio \"A\"\n\\Camera"
        repository.start(SEED.copy(workspace = WorkspaceKey(7, name)))
        repository.append(write(1, payload = HistoryPayload.Program(name, true)))
        repository.append(
            write(
                2,
                payload =
                    HistoryPayload.Output(HistoryOutput.STREAM, HistoryOutputState.UNKNOWN, true),
            )
        )
        repository.append(
            write(4, payload = HistoryPayload.Gap(HistoryGapReason.OBSERVATION_INTERRUPTED))
        )
        repository.recover("new-process")
        val exported = repository.exportSnapshot().exportJson(20_000)
        val document = Json.parseToJsonElement(exported).jsonObject
        assertEquals("scenedeck-history", document.getValue("format").jsonPrimitive.content)
        assertEquals("1", document.getValue("version").jsonPrimitive.content)
        val observation = document.getValue("observations").jsonArray.single().jsonObject
        assertEquals(name, observation.getValue("collectionName").jsonPrimitive.content)
        assertEquals("null", observation.getValue("endedAtMs").toString())
        assertEquals("1", observation.getValue("missingEvents").jsonPrimitive.content)
        val events = observation.getValue("events").jsonArray
        val scene = events[0].jsonObject.getValue("payload").jsonObject
        val output = events[1].jsonObject.getValue("payload").jsonObject
        assertEquals(name, scene.getValue("sceneName").jsonPrimitive.content)
        assertEquals("UNKNOWN", output.getValue("state").jsonPrimitive.content)
        assertFalse(exported.contains(SEED.ownerId))
        val keys = observation.keys
        assertEquals(
            setOf(
                "id",
                "profileId",
                "collectionName",
                "startedAtMs",
                "lastObservedAtMs",
                "elapsedMs",
                "endedAtMs",
                "endReason",
                "lastOrdinal",
                "omittedEvents",
                "missingEvents",
                "events",
            ),
            keys,
        )
    }

    @Test
    fun selectedSnapshotRemainsConsistentAfterLaterWritesOrDeletion(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        repository.start(SEED.copy(id = "other", workspace = WorkspaceKey(8, "Other")))
        val snapshot = repository.exportSnapshot(SEED.id)
        repository.append(write(2))
        repository.delete(SEED.id)
        assertEquals(listOf(SEED.id), snapshot.observations.map { it.session.id })
        assertEquals(1, snapshot.observations.single().events.size)
        assertEquals(
            "1",
            Json.parseToJsonElement(snapshot.exportJson(20_000))
                .jsonObject
                .getValue("observations")
                .jsonArray
                .single()
                .jsonObject
                .getValue("lastOrdinal")
                .jsonPrimitive
                .content,
        )
        assertTrue(runCatching { repository.exportSnapshot(SEED.id) }.isFailure)
        assertEquals("other", repository.exportSnapshot().observations.single().session.id)
    }

    @Test
    fun payloadValidationRejectsUnboundedNamesAndImpossibleStates(): Unit = runBlocking {
        repository.start(SEED)
        val invalid =
            listOf(
                HistoryPayload.Program("x".repeat(257), false),
                HistoryPayload.Program("", false),
                HistoryPayload.Output(HistoryOutput.STREAM, HistoryOutputState.PAUSED, false),
                HistoryPayload.Output(
                    HistoryOutput.RECORDING,
                    HistoryOutputState.RECONNECTING,
                    false,
                ),
                HistoryPayload.Output(
                    HistoryOutput.VIRTUAL_CAMERA,
                    HistoryOutputState.ACTIVE,
                    false,
                    1,
                ),
                HistoryPayload.Output(HistoryOutput.STREAM, HistoryOutputState.UNKNOWN, false, 1),
                HistoryPayload.Output(
                    HistoryOutput.RECORDING,
                    HistoryOutputState.ACTIVE,
                    false,
                    -1,
                ),
                HistoryPayload.Warning(HistoryWarning.DISK_SPACE, HistoryWarningPhase.SHOWN, 0),
            )
        invalid.forEach {
            assertTrue(runCatching { repository.append(write(1, payload = it)) }.isFailure)
        }
        assertTrue(repository.exportSnapshot().observations.single().events.isEmpty())
    }

    @Test
    fun unsupportedPayloadVersionAndOversizeStoredPayloadFailExplicitly(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        val sql = "UPDATE history_events SET payload = ?"
        database.openHelper.writableDatabase.execSQL(
            sql,
            arrayOf(
                "{\"version\":2,\"event\":{\"type\":\"gap\",\"reason\":\"OBSERVATION_INTERRUPTED\"}}"
            ),
        )
        assertTrue(runCatching { repository.exportSnapshot() }.isFailure)
        database.openHelper.writableDatabase.execSQL(sql, arrayOf(" ".repeat(4_097)))
        assertTrue(runCatching { repository.exportSnapshot() }.isFailure)
    }
}
