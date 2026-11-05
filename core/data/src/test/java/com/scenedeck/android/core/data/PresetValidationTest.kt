package com.scenedeck.android.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetValidationTest : PresetTestFixture() {
    @Test
    fun invalidDraftsCannotChangeTheSavedDefinition(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val original = saved.draft()
        val invalid =
            listOf(
                original.copy(name = " "),
                original.copy(name = "n".repeat(MAX_PRESET_NAME_LENGTH + 1)),
                original.copy(steps = emptyList()),
                original.copy(steps = List(MAX_PRESET_STEPS + 1) { steps.first() }),
                original.copy(steps = listOf(PresetStep.Mute(" ", true))),
                original.copy(
                    steps = listOf(PresetStep.Media("n".repeat(1_025), PresetMediaState.PAUSED))
                ),
            ) +
                listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY).map { value ->
                    original.copy(steps = listOf(PresetStep.Volume("Music", value)))
                }
        invalid.forEach { assertTrue(runCatching { repository.save(origin, saved, it) }.isFailure) }
        assertEquals(listOf(saved), repository.snapshot(origin))
    }

    @Test
    fun unsupportedVersionsUnknownActionsAndCorruptDataAreFailedNotEmpty(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val row = saved.encodePreset()
        val corrupt =
            listOf(
                row.copy(payload = row.payload.replace("\"version\":1", "\"version\":2")),
                row.copy(
                    payload =
                        "{\"version\":1,\"steps\":[{\"type\":\"script\",\"source\":\"private\"}]}"
                ),
                row.copy(payload = "x".repeat(32_769)),
                row.copy(payload = row.payload.dropLast(1) + ",\"private\":\"unknown\"}"),
                row.copy(revision = -1),
                row.copy(nameKey = "not-the-name"),
            )
        corrupt.forEach {
            database.actionPresetDao().save(it)
            assertEquals(
                PresetState.Failed(origin),
                repository.state.first { state -> state is PresetState.Failed },
            )
            assertTrue(runCatching { repository.snapshot(origin) }.isFailure)
        }
        database.actionPresetDao().save(row)
        repository.retry()
        val ready = repository.state.first { it is PresetState.Ready } as PresetState.Ready
        assertEquals(listOf(saved), ready.presets)
    }

    @Test
    fun unavailableWorkspaceDoesNotExposeAnotherScopesDefinitions(): Unit = runBlocking {
        repository.save(origin, null, draft())
        context.unavailable()
        assertTrue(repository.state.first() is PresetState.Unavailable)
        assertTrue(
            runCatching { repository.save(origin, null, draft("Other")) }.exceptionOrNull()
                is WorkspaceChangedException
        )
    }

    @Test
    fun failedUpdateRollsBackAndDoesNotCorruptTheOriginal(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_preset BEFORE UPDATE ON action_presets " +
                "BEGIN SELECT RAISE(ABORT, 'private diagnostic'); END"
        )
        assertTrue(
            runCatching { repository.save(origin, saved, saved.draft().copy(name = "New")) }
                .isFailure
        )
        assertEquals(listOf(saved), repository.snapshot(origin))
    }

    @Test
    fun excessRowsNeverBecomeValidPartialLists(): Unit = runBlocking {
        repeat(MAX_ACTION_PRESETS + 1) {
            database
                .actionPresetDao()
                .save(ActionPreset(origin.key, "preset:$it", "Preset $it", 0, steps).encodePreset())
        }
        assertEquals(
            PresetState.Failed(origin),
            repository.state.first { it is PresetState.Failed },
        )
        assertTrue(runCatching { repository.snapshot(origin) }.isFailure)
    }
}
