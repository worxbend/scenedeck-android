package com.scenedeck.android.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.ConnectionProfileEntity
import com.scenedeck.android.core.database.NamedDeckEntity
import com.scenedeck.android.core.database.NamedDeckMemberEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.database.WorkspaceDeckSelectionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NamedDeckRepositoryTest {
    private lateinit var database: SceneDeckDatabase
    private lateinit var workspaces: MutableWorkspaceContext
    private lateinit var repository: NamedDeckRepository
    private val origin = TEST_WORKSPACE

    @Before
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        workspaces = MutableWorkspaceContext()
        repository = NamedDeckRepository(database, workspaces)
    }

    @After fun close() = database.close()

    @Test
    fun emptyWorkspaceAndBuiltinSelectionSurviveRepositoryRecreation(): Unit = runBlocking {
        assertEquals(NamedDeckSnapshot(), repository.snapshot(origin))
        repository.select(origin, DeckSelection.Primary)
        val reopened = NamedDeckRepository(database, workspaces)
        assertEquals(DeckSelection.Primary, reopened.snapshot(origin).selection)
        val ready =
            withTimeout(5_000) { reopened.state.filterIsInstance<NamedDeckState.Ready>().first() }
        assertEquals(origin, ready.origin)
        assertEquals(emptyList<NamedDeck>(), ready.snapshot.decks)
        assertEquals(DeckSelection.Primary, ready.snapshot.selection)
    }

    @Test
    fun membershipAndOrderAreIndependentFromCuration(): Unit = runBlocking {
        val registry = database.workspaceSceneDao()
        val curation =
            SceneRegistryEntity(
                "Camera",
                "ARCHIVE",
                sortOrder = 5,
                connectionProfileId = origin.key.profileId,
                sceneCollectionName = origin.key.collectionName,
            )
        registry.upsert(curation)
        val id = repository.create(origin, "  Interview  ", listOf("Camera", "Wide", "Camera"))
        assertEquals(
            NamedDeck(id, "Interview", listOf("Camera", "Wide")),
            repository.snapshot(origin).decks.single(),
        )
        repository.replaceMembers(
            origin,
            repository.snapshot(origin).decks.single { it.id == id },
            listOf("Missing", "Wide", "Camera", "Wide"),
        )
        val deck = repository.snapshot(origin)
        assertEquals(listOf("Missing", "Wide", "Camera"), deck.decks.single().sceneNames)
        assertEquals(DeckSelection.Named(id), deck.selection)
        assertEquals(
            curation,
            registry.byName(origin.key.profileId, origin.key.collectionName, "Camera"),
        )
        assertEquals(
            listOf(0, 1, 2),
            database
                .namedDeckDao()
                .snapshot(origin.key.profileId, origin.key.collectionName)
                .mapNotNull { it.sortOrder },
        )
    }

    @Test
    fun sameNamesAreIndependentAcrossProfilesAndCollections(): Unit = runBlocking {
        val first = repository.create(origin, "Interview", listOf("Camera"))
        val otherProfile = origin.copy(key = WorkspaceKey(8, "Studio"), targetGeneration = 2)
        workspaces.select(otherProfile)
        val second = repository.create(otherProfile, "Interview", listOf("Wide"))
        val otherCollection = origin.copy(key = WorkspaceKey(7, "Other"), targetGeneration = 3)
        workspaces.select(otherCollection)
        val third = repository.create(otherCollection, "Interview", listOf("Slides"))
        for ((session, expectedId, expectedScene) in
            listOf(
                Triple(origin, first, "Camera"),
                Triple(otherProfile, second, "Wide"),
                Triple(otherCollection, third, "Slides"),
            )) {
            workspaces.select(session)
            val snapshot = repository.snapshot(session)
            assertEquals(expectedId, snapshot.decks.single().id)
            assertEquals(listOf(expectedScene), snapshot.decks.single().sceneNames)
        }
    }

    @Test
    fun namesAreValidatedAndCaseInsensitiveWithinOneWorkspace(): Unit = runBlocking {
        val id = repository.create(origin, "Interview")
        val invalid = listOf("", "  ", "A\nB", "A".repeat(MAX_DECK_NAME_LENGTH + 1))
        invalid.forEach { name ->
            assertEquals(
                DeckEditFailure.INVALID_NAME,
                (runCatching { repository.create(origin, name) }.exceptionOrNull()
                        as DeckEditException)
                    .reason,
            )
        }
        assertEquals(
            DeckEditFailure.DUPLICATE_NAME,
            (runCatching { repository.create(origin, " INTERVIEW ") }.exceptionOrNull()
                    as DeckEditException)
                .reason,
        )
        repository.rename(
            origin,
            repository.snapshot(origin).decks.single { it.id == id },
            "INTERVIEW",
        )
        assertEquals("INTERVIEW", repository.snapshot(origin).decks.single().name)
        repository.rename(origin, repository.snapshot(origin).decks.single { it.id == id }, "Guest")
        assertEquals("Guest", repository.snapshot(origin).decks.single().name)
    }

    @Test
    fun deletingSelectedDeckFallsBackToAllAndOnlyRemovesItsMembers(): Unit = runBlocking {
        val first = repository.create(origin, "First", listOf("Camera"))
        val second = repository.create(origin, "Second", listOf("Wide"))
        repository.delete(origin, repository.snapshot(origin).decks.single { it.id == second })
        val snapshot = repository.snapshot(origin)
        assertEquals(DeckSelection.All, snapshot.selection)
        assertEquals(listOf(NamedDeck(first, "First", listOf("Camera"))), snapshot.decks)
        assertEquals(
            DeckEditFailure.DECK_MISSING,
            (runCatching { repository.select(origin, DeckSelection.Named(second)) }
                    .exceptionOrNull() as DeckEditException)
                .reason,
        )
        database
            .connectionProfileDao()
            .insert(
                ConnectionProfileEntity(
                    id = origin.key.profileId,
                    name = "Studio",
                    host = "studio.local",
                    port = 4455,
                    createdAt = 1,
                )
            )
        database.connectionProfileDao().deleteById(origin.key.profileId)
        assertEquals(snapshot, repository.snapshot(origin))
    }

    @Test
    fun snapshotJoinsFullNamespaceEvenWhenDeckIdsRepeat(): Unit = runBlocking {
        val dao = database.namedDeckDao()
        for (profile in listOf(7L, 8L)) {
            dao.insert(NamedDeckEntity(profile, "Studio", "same-id", "Show", "show"))
            dao.insertMembers(
                listOf(NamedDeckMemberEntity(profile, "Studio", "same-id", "Camera $profile", 0))
            )
            dao.select(WorkspaceDeckSelectionEntity(profile, "Studio", "same-id"))
        }
        assertEquals(listOf("Camera 7"), repository.snapshot(origin).decks.single().sceneNames)
        assertEquals(DeckSelection.Named("same-id"), repository.snapshot(origin).selection)
    }

    @Test
    fun queuedMutationCannotEnterAfterWorkspaceReplacement(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = async {
            database.withTransaction {
                entered.complete(Unit)
                release.await()
            }
        }
        withTimeout(5_000) { entered.await() }
        val queued =
            async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.create(origin, "Late") }
            }
        workspaces.select(origin.copy(targetGeneration = 2))
        release.complete(Unit)
        blocker.await()
        assertTrue(
            withTimeout(5_000) { queued.await() }.exceptionOrNull() is WorkspaceChangedException
        )
        assertTrue(
            database.namedDeckDao().snapshot(origin.key.profileId, origin.key.collectionName).none {
                it.deckId != null
            }
        )
    }

    @Test
    fun workspaceReplacementRollsBackAnAlreadyStartedLocalTransaction(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pending = async {
            runCatching {
                repository.mutate(origin) { dao ->
                    dao.insert(
                        NamedDeckEntity(7, "Studio", "interrupted", "Interrupted", "interrupted")
                    )
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        withTimeout(5_000) { entered.await() }
        workspaces.select(origin.copy(targetGeneration = 2))
        release.complete(Unit)
        assertTrue(
            withTimeout(5_000) { pending.await() }.exceptionOrNull() is WorkspaceChangedException
        )
        assertFalse(
            database.namedDeckDao().snapshot(7, "Studio").any { it.deckId == "interrupted" }
        )
    }

    @Test
    fun obsRenameUpdatesMembershipWithoutAssigningRolesAndDestinationKeepsPosition(): Unit =
        runBlocking {
            val id = repository.create(origin, "Show", listOf("Old", "Middle", "New", "Last"))
            val other = origin.copy(key = WorkspaceKey(8, "Studio"))
            workspaces.select(other)
            repository.create(other, "Show", listOf("Old"))
            workspaces.select(origin)
            // No curation row: the scene-reference migration still applies.
            assertFalse(database.workspaceSceneDao().rename(7, "Studio", "Old", "New"))
            assertEquals(
                listOf("Middle", "New", "Last"),
                repository.snapshot(origin).decks.single().sceneNames,
            )
            assertEquals(
                listOf(0, 1, 2),
                database.namedDeckDao().snapshot(7, "Studio").mapNotNull { it.sortOrder },
            )
            assertTrue(database.workspaceSceneDao().observeAll(7, "Studio").first().isEmpty())
            assertEquals(id, repository.snapshot(origin).decks.single().id)
            workspaces.select(other)
            assertEquals(listOf("Old"), repository.snapshot(other).decks.single().sceneNames)
        }

    @Test
    fun staleEditorsCannotRestoreOldReferencesAfterObsRename(): Unit = runBlocking {
        repository.create(origin, "Show", listOf("Old", "Wide"))
        val target = repository.snapshot(origin).decks.single()
        database.workspaceSceneDao().rename(7, "Studio", "Old", "New")
        val edits =
            listOf<suspend () -> Unit>(
                { repository.replaceMembers(origin, target, listOf("Wide", "Old")) },
                { repository.rename(origin, target, "Guest") },
                { repository.delete(origin, target) },
            )
        edits.forEach { edit ->
            assertEquals(
                DeckEditFailure.DECK_CHANGED,
                (runCatching { edit() }.exceptionOrNull() as DeckEditException).reason,
            )
        }
        assertEquals(listOf("New", "Wide"), repository.snapshot(origin).decks.single().sceneNames)
    }

    @Test
    fun portableRoundTripKeepsMissingReferencesAndOrderWithoutDeviceIdentity(): Unit = runBlocking {
        repository.create(origin, "Interview", listOf("Wide", "Missing", "Camera"))
        repository.create(origin, "Guests", listOf("Zoom", "Wide"))
        val payload = NamedDeckCodec.encode(repository.snapshot(origin))
        assertFalse(payload.contains("profileId"))
        assertFalse(payload.contains("deck:"))
        val destination = origin.copy(key = WorkspaceKey(91, "Travel"))
        workspaces.select(destination)
        val preview = repository.previewImport(destination, payload)
        assertEquals(2, preview.additionCount)
        assertEquals(0, preview.replacementCount)
        repository.applyImport(preview)
        assertEquals(payload, NamedDeckCodec.encode(repository.snapshot(destination)))
        assertEquals(DeckSelection.All, repository.snapshot(destination).selection)
    }

    @Test
    fun importReplacesOnlyPreviewedNamesAndKeepsSelectedIdentity(): Unit = runBlocking {
        val id = repository.create(origin, "Interview", listOf("Camera"))
        repository.create(origin, "Keep", listOf("Missing"))
        repository.select(origin, DeckSelection.Named(id))
        val entries =
            NamedDeckSnapshot(listOf(NamedDeck("ignored", "INTERVIEW", listOf("Wide", "Camera"))))
        val preview = repository.previewImport(origin, NamedDeckCodec.encode(entries))
        assertEquals(1, preview.replacementCount)
        repository.applyImport(preview)
        val result = repository.snapshot(origin)
        assertEquals(DeckSelection.Named(id), result.selection)
        assertEquals(NamedDeck(id, "INTERVIEW", listOf("Wide", "Camera")), result.decks.first())
        assertEquals(listOf("Missing"), result.decks.last().sceneNames)
    }

    @Test
    fun importPreviewRejectsConcurrentEditAndReplacementWorkspaceWithoutPartialWrites(): Unit =
        runBlocking {
            repository.create(origin, "Show", listOf("Camera"))
            val payload =
                NamedDeckCodec.encode(
                    NamedDeckSnapshot(listOf(NamedDeck("x", "New", listOf("Wide"))))
                )
            val preview = repository.previewImport(origin, payload)
            val target = repository.snapshot(origin).decks.single()
            repository.rename(origin, target, "Changed")
            assertEquals(
                DeckEditFailure.DECK_CHANGED,
                (runCatching { repository.applyImport(preview) }.exceptionOrNull()
                        as DeckEditException)
                    .reason,
            )
            assertEquals(listOf("Changed"), repository.snapshot(origin).decks.map { it.name })
            val fresh = repository.previewImport(origin, payload)
            val replacement = origin.copy(targetGeneration = 2)
            workspaces.select(replacement)
            assertTrue(
                runCatching { repository.applyImport(fresh) }.exceptionOrNull()
                    is WorkspaceChangedException
            )
            assertTrue(
                runCatching { repository.select(origin, DeckSelection.Primary) }.exceptionOrNull()
                    is WorkspaceChangedException
            )
            assertTrue(
                runCatching { repository.replaceMembers(origin, target, emptyList()) }
                    .exceptionOrNull() is WorkspaceChangedException
            )
            assertEquals(listOf("Changed"), repository.snapshot(replacement).decks.map { it.name })
        }

    @Test
    fun namedSelectionAndOrderSurviveClosingAndReopeningStorage(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val filename = "named-deck-restoration.db"
        app.deleteDatabase(filename)
        database.close()
        database = Room.databaseBuilder(app, SceneDeckDatabase::class.java, filename).build()
        repository = NamedDeckRepository(database, workspaces)
        try {
            repository.create(origin, "Show", listOf("Wide", "Missing", "Camera"))
            val before = repository.snapshot(origin)
            database.close()
            database = Room.databaseBuilder(app, SceneDeckDatabase::class.java, filename).build()
            val reconnected = origin.copy(targetGeneration = 2)
            workspaces.select(reconnected)
            repository = NamedDeckRepository(database, workspaces)
            assertEquals(before, repository.snapshot(reconnected))
        } finally {
            database.close()
            app.deleteDatabase(filename)
        }
    }
}
