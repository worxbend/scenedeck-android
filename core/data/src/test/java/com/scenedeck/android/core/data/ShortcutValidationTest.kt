package com.scenedeck.android.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutValidationTest : ShortcutTestFixture() {
    @Test
    fun invalidIncompleteOrReservedDraftsCannotReplaceSavedBinding(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val original = saved.draft()
        val invalid =
            listOf(
                original.copy(chord = null),
                original.copy(action = null),
                original.copy(action = ShortcutAction.Scene(" ")),
                original.copy(action = ShortcutAction.Mute("x".repeat(1_025), false)),
                original.copy(id = ""),
                original.copy(id = "x".repeat(129)),
            ) +
                listOf(
                        "Enter",
                        "SPACE",
                        "TAB",
                        "ESCAPE",
                        "DPAD_UP",
                        "MEDIA_PLAY",
                        "a",
                        "F0",
                        "F13",
                        "NUM10",
                    )
                    .map {
                        original.copy(chord = ShortcutChord(it))
                    } +
                listOf(-1, 16).map { original.copy(chord = ShortcutChord("F8", it)) }
        invalid.forEach { assertTrue(runCatching { repository.save(origin, saved, it) }.isFailure) }
        assertEquals(listOf(saved), repository.snapshot(origin))
    }

    @Test
    fun malformedStorageFailsClosedAndRecoveryDoesNotInstallDefaults(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val row = saved.encodeShortcut()
        val corrupt =
            listOf(
                row.copy(payload = row.payload.replace("\"version\":1", "\"version\":2")),
                row.copy(payload = row.payload.replace("\"version\":1,", "")),
                row.copy(payload = "{\"version\":1,\"action\":{\"type\":\"script\"}}"),
                row.copy(payload = "x".repeat(8_193)),
                row.copy(payload = row.payload.dropLast(1) + ",\"private\":\"unknown\"}"),
                row.copy(revision = -1),
                row.copy(key = "ENTER"),
                row.copy(modifiers = 16),
            )
        corrupt.forEach {
            database.shortcutBindingDao().save(it)
            assertEquals(
                ShortcutState.Failed(origin),
                repository.state.first { state -> state is ShortcutState.Failed },
            )
            assertTrue(runCatching { repository.snapshot(origin) }.isFailure)
        }
        database.shortcutBindingDao().save(row)
        repository.retry()
        val ready = repository.state.first { it is ShortcutState.Ready } as ShortcutState.Ready
        assertEquals(listOf(saved), ready.bindings)
    }

    @Test
    fun unavailableWorkspaceNeverExposesOldBindings(): Unit = runBlocking {
        repository.save(origin, null, draft())
        context.unavailable()
        assertTrue(repository.state.first() is ShortcutState.Unavailable)
        assertTrue(
            runCatching { repository.save(origin, null, draft("F1")) }.exceptionOrNull()
                is WorkspaceChangedException
        )
    }

    @Test
    fun failedUpdatePreservesTheOriginalAndCanBeRetried(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val draft = saved.draft().copy(enabled = false)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_shortcut BEFORE UPDATE ON shortcut_bindings " +
                "BEGIN SELECT RAISE(ABORT, 'private diagnostic'); END"
        )
        assertTrue(runCatching { repository.save(origin, saved, draft) }.isFailure)
        assertEquals(listOf(saved), repository.snapshot(origin))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_shortcut")
        assertEquals(draft, repository.save(origin, saved, draft).draft())
    }

    @Test
    fun excessRowsAreNeverPublishedAsAValidPartialList(): Unit = runBlocking {
        repeat(MAX_SHORTCUT_BINDINGS + 1) { index ->
            database
                .shortcutBindingDao()
                .save(
                    ShortcutBinding(
                            origin.key,
                            "shortcut:$index",
                            0,
                            ShortcutChord("F${index % 12 + 1}", index / 12),
                            ShortcutAction.TakePreview,
                            true,
                        )
                        .encodeShortcut()
                )
        }
        assertEquals(
            ShortcutState.Failed(origin),
            repository.state.first { it is ShortcutState.Failed },
        )
        assertTrue(runCatching { repository.snapshot(origin) }.isFailure)
    }

    @Test
    fun escapedExactNamesAtTheLimitRoundTrip(): Unit = runBlocking {
        val name = "x" + "\u0001".repeat(1_023)
        val saved = repository.save(origin, null, draft().copy(action = ShortcutAction.Scene(name)))
        assertEquals(ShortcutAction.Scene(name), repository.snapshot(origin).single().action)
        assertEquals(saved, repository.snapshot(origin).single())
    }
}
