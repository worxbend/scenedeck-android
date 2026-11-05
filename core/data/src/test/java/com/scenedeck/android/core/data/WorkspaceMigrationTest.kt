package com.scenedeck.android.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneDeckMigrations
import com.scenedeck.android.core.database.SceneRegistryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: SceneDeckDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun upgradeFromVersionTwoPreservesEveryLegacyFieldAndAllowsScopedDuplicates(): Unit =
        runBlocking {
            createHistoricalDatabase(2)
            val upgraded = openMigratedDatabase()
            assertEquals("Studio", upgraded.connectionProfileDao().byId(7)?.name)
            val legacy =
                SceneRegistryEntity(
                    sceneName = "Camera",
                    role = "SECONDARY",
                    accentColorArgb = 0xFF7E57C2,
                    iconName = "CAMERA",
                    sortOrder = 4,
                )
            assertEquals(legacy, upgraded.sceneRegistryDao().byName("Camera"))
            assertEquals(
                listOf("Camera", "Unplaced"),
                upgraded.sceneRegistryDao().observeAll().first().map { it.sceneName },
            )
            assertEquals(
                SceneRegistryEntity("Unplaced", "UNASSIGNED", sortOrder = Int.MAX_VALUE),
                upgraded.sceneRegistryDao().byName("Unplaced"),
            )
            assertTrue(upgraded.workspaceSceneDao().observeAll(7, "Show").first().isEmpty())
            val scoped = legacy.copy(connectionProfileId = 7, sceneCollectionName = "Show")
            upgraded.workspaceSceneDao().upsert(scoped)
            assertEquals(scoped, upgraded.workspaceSceneDao().byName(7, "Show", "Camera"))
            assertEquals(legacy, upgraded.sceneRegistryDao().byName("Camera"))
        }

    @Test
    fun upgradeFromVersionOnePreservesProfilesThroughBothMigrations(): Unit = runBlocking {
        createHistoricalDatabase(1)
        val upgraded = openMigratedDatabase()

        val profile = upgraded.connectionProfileDao().byId(7)!!
        assertEquals("Studio", profile.name)
        assertEquals("studio.local", profile.host)
        assertEquals(4456, profile.port)
        assertEquals(123L, profile.createdAt)
        assertEquals(456L, profile.lastUsedAt)
        assertTrue(upgraded.sceneRegistryDao().observeAll().first().isEmpty())
        assertTrue(upgraded.workspaceSceneDao().observeAll(7, "Show").first().isEmpty())
    }

    @Test
    fun upgradeFromVersionThreePreservesBothRegistryScopesAndStartsWithAllDeck(): Unit =
        runBlocking {
            createHistoricalDatabase(3)
            context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
                sqlite.execSQL(
                    "INSERT INTO scene_registry VALUES ('Camera', 'PRIMARY', NULL, NULL, 3, 0, '')"
                )
                sqlite.execSQL(
                    "INSERT INTO scene_registry VALUES ('Camera', 'ARCHIVE', NULL, NULL, 1, 7, 'Studio')"
                )
            }
            val upgraded = openMigratedDatabase()
            assertEquals("PRIMARY", upgraded.sceneRegistryDao().byName("Camera")?.role)
            assertEquals(
                "ARCHIVE",
                upgraded.workspaceSceneDao().byName(7, "Studio", "Camera")?.role,
            )
            val decks = upgraded.namedDeckDao().snapshot(7, "Studio").toDeckSnapshot()
            assertEquals(NamedDeckSnapshot(), decks)
        }

    @Test
    fun upgradeFromVersionFourPreservesDeckSelectionAndAddsEmptyAliases(): Unit = runBlocking {
        createHistoricalDatabase(4)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO scene_registry VALUES ('Camera', 'ARCHIVE', NULL, NULL, 3, 7, 'Studio')"
            )
            sqlite.execSQL("INSERT INTO named_decks VALUES (7, 'Studio', 'show', 'Show', 'show')")
            sqlite.execSQL(
                "INSERT INTO named_deck_members VALUES (7, 'Studio', 'show', 'Camera', 0)"
            )
            sqlite.execSQL("INSERT INTO workspace_deck_selection VALUES (7, 'Studio', 'show')")
        }
        val upgraded = openMigratedDatabase()
        val entry = requireNotNull(upgraded.workspaceSceneDao().byName(7, "Studio", "Camera"))
        assertEquals(null, entry.localAlias)
        assertEquals("ARCHIVE", entry.role)
        val decks = upgraded.namedDeckDao().snapshot(7, "Studio").toDeckSnapshot()
        assertEquals(DeckSelection.Named("show"), decks.selection)
        assertEquals(listOf("Camera"), decks.decks.single().sceneNames)
    }

    @Test
    fun upgradeFromVersionFiveKeepsAliasesAndStartsWithNoPreflightConfig(): Unit = runBlocking {
        createHistoricalDatabase(5)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO scene_registry VALUES ('Camera', 'ARCHIVE', NULL, NULL, 3, 7, 'Studio', 'Host')"
            )
        }
        val upgraded = openMigratedDatabase()
        assertEquals("Host", upgraded.workspaceSceneDao().byName(7, "Studio", "Camera")?.localAlias)
        assertEquals(null, upgraded.preflightConfigDao().get(7, "Studio"))
    }

    @Test
    fun upgradeFromVersionSixPreservesPreflightAndAddsEmptyBookmarks(): Unit = runBlocking {
        createHistoricalDatabase(6)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL("INSERT INTO preflight_config VALUES (7, 'Studio', '{preserved}')")
        }
        val upgraded = openMigratedDatabase()
        assertEquals("{preserved}", upgraded.preflightConfigDao().get(7, "Studio")?.payload)
        assertTrue(upgraded.bookmarkDao().observe(500).first().isEmpty())
        assertEquals("Studio", upgraded.connectionProfileDao().byId(7)?.name)
    }

    @Test
    fun upgradeFromVersionSevenPreservesBookmarksAndAddsEmptyHistory(): Unit = runBlocking {
        createHistoricalDatabase(7)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO bookmarks VALUES " +
                    "(42, 'mark', 7, 'Studio', 'old-observation', 123, 'Interview', '{preserved}')"
            )
        }
        val upgraded = openMigratedDatabase()
        val bookmark = requireNotNull(upgraded.bookmarkDao().get("mark"))
        assertEquals(42L, bookmark.sequence)
        assertEquals("old-observation", bookmark.observationId)
        assertEquals("Interview", bookmark.label)
        assertEquals("{preserved}", bookmark.times)
        val state =
            HistoryRepository(upgraded).state.first { it is HistoryState.Ready }
                as HistoryState.Ready
        assertEquals(HistoryPreferences(), state.preferences)
        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun upgradeFromVersionEightPreservesHistoryWithoutInventingBookmarkLinks(): Unit = runBlocking {
        createHistoricalDatabase(8)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO history_sessions VALUES " +
                    "(9, 'observation', 'owner', 0, 7, 'Studio', 1000, 1100, 100, NULL, NULL, 1, 0, 0)"
            )
            sqlite.execSQL(
                "INSERT INTO history_events VALUES ('observation', 1, 1100, 100, 0, ?)",
                arrayOf("{\"event\":{\"type\":\"gap\",\"reason\":\"OBSERVATION_INTERRUPTED\"}}"),
            )
            sqlite.execSQL(
                "INSERT INTO bookmarks VALUES (42, 'mark', 7, 'Studio', 'legacy', 1200, 'Old mark', '{}')"
            )
        }
        val snapshot = HistoryRepository(openMigratedDatabase()).exportSnapshot()
        val observation = snapshot.observations.single()
        assertEquals("observation", observation.session.id)
        assertEquals(1, observation.events.size)
        assertTrue(observation.bookmarks.isEmpty())
        assertEquals("Old mark", snapshot.unlinkedBookmarks.single().label)
    }

    private fun openMigratedDatabase(): SceneDeckDatabase =
        Room.databaseBuilder(context, SceneDeckDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                SceneDeckMigrations.MIGRATION_1_2,
                SceneDeckMigrations.MIGRATION_2_3,
                SceneDeckMigrations.MIGRATION_3_4,
                SceneDeckMigrations.MIGRATION_4_5,
                SceneDeckMigrations.MIGRATION_5_6,
                SceneDeckMigrations.MIGRATION_6_7,
                SceneDeckMigrations.MIGRATION_7_8,
                SceneDeckMigrations.MIGRATION_8_9,
            )
            .build()
            .also { database = it }

    /** Use committed historical schemas, not SQL copied from the migration under test. */
    private fun createHistoricalDatabase(version: Int) {
        val resource = "/com.scenedeck.android.core.database.SceneDeckDatabase/$version.json"
        val schema =
            requireNotNull(javaClass.getResourceAsStream(resource)).bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("database")
            }
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                sqlite.execSQL(
                    entity
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName"))
                )
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (indexId in 0 until indices.length()) sqlite.execSQL(
                    indices
                        .getJSONObject(indexId)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName"))
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) sqlite.execSQL(setup.getString(index))
            sqlite.execSQL(
                "INSERT INTO connection_profiles VALUES (7, 'Studio', 'studio.local', 4456, 123, 456)"
            )
            if (version == 2) {
                sqlite.execSQL(
                    "INSERT INTO scene_registry VALUES ('Camera', 'SECONDARY', ?, 'CAMERA', 4)",
                    arrayOf(0xFF7E57C2),
                )
                sqlite.execSQL(
                    "INSERT INTO scene_registry VALUES ('Unplaced', 'UNASSIGNED', NULL, NULL, ?)",
                    arrayOf(Int.MAX_VALUE),
                )
            }
            sqlite.version = version
        }
    }

    private companion object {
        const val DATABASE_NAME = "workspace-migration-test.db"
    }
}
