package com.scenedeck.android.feature.live

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.DeckImportPreview
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.NamedDeck
import com.scenedeck.android.core.data.NamedDeckCodec
import com.scenedeck.android.core.data.NamedDeckRepository
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.database.SceneDeckDatabase
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NamedDeckFilesTest {
    @Test
    fun pickerKeepsItsOriginAndRejectsReplacementBeforeReading(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(app, SceneDeckDatabase::class.java).build()
        try {
            val workspaces = MutableWorkspaceContext()
            val repository = NamedDeckRepository(database, workspaces)
            val failed = CompletableDeferred<String>()
            val files =
                NamedDeckFiles(
                    repository,
                    this,
                    { error("Stale import reached preview") },
                    { failed.complete(it) },
                )
            assertTrue(files.prepare(TEST_WORKSPACE))
            assertFalse(files.prepare(TEST_WORKSPACE))
            val replacement = TEST_WORKSPACE.copy(targetGeneration = 2)
            workspaces.select(replacement)
            files.importFrom(Uri.parse("content://decks/backup"), app.contentResolver)
            withTimeout(5_000) { failed.await() }
            assertTrue(repository.snapshot(replacement).decks.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun api28DocumentReadBuildsPreviewBeforeAnyMutation(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(app, SceneDeckDatabase::class.java).build()
        try {
            val repository = NamedDeckRepository(database, MutableWorkspaceContext())
            val preview = CompletableDeferred<DeckImportPreview>()
            val files =
                NamedDeckFiles(
                    repository,
                    this,
                    { preview.complete(it) },
                    { preview.completeExceptionally(AssertionError(it)) },
                )
            val uri = Uri.parse("content://decks/backup")
            val payload =
                NamedDeckCodec.encode(
                    NamedDeckSnapshot(listOf(NamedDeck("x", "Travel", listOf("Missing", "Camera"))))
                )
            shadowOf(app.contentResolver)
                .registerInputStream(uri, ByteArrayInputStream(payload.toByteArray()))
            assertTrue(files.prepare(TEST_WORKSPACE))
            files.importFrom(uri, app.contentResolver)
            val result = withTimeout(5_000) { preview.await() }
            assertEquals(TEST_WORKSPACE, result.origin)
            assertEquals(listOf("Missing", "Camera"), result.entries.single().scenes)
            assertTrue(repository.snapshot(TEST_WORKSPACE).decks.isEmpty())
        } finally {
            database.close()
        }
    }
}
