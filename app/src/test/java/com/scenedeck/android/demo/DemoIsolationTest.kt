package com.scenedeck.android.demo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.background.KeepAliveController
import com.scenedeck.android.core.database.BookmarkEntity
import com.scenedeck.android.core.database.ConnectionProfileEntity
import com.scenedeck.android.core.database.HistorySessionEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsConnectionTestingUnavailableException
import com.scenedeck.android.core.obs.demo.DemoObsClient
import com.scenedeck.android.di.AppModule
import com.scenedeck.android.di.ObsProcessModule
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DemoIsolationTest {
    @Test
    fun onlyTheExactPrivateProcessChoosesDemoAndUnknownNamesFailClosed() {
        val name = "com.worxbend.scenedeck"
        assertEquals(AppProcess.LIVE, AppProcess.resolve(name, name))
        assertEquals(AppProcess.DEMO, AppProcess.resolve(name, "$name:demo"))
        listOf(null, "", "local-demo", "$name:demo2", "other:demo").forEach { unknown ->
            assertTrue(runCatching { AppProcess.resolve(name, unknown) }.isFailure)
        }
    }

    @Test
    fun demoProvidersNeverTouchContextFilesCredentialsOrServiceIntents() = runTest {
        // Any accidental Context call fails, including SharedPreferences/Keystore/file access.
        val context = mockk<Context>()
        val settings = AppModule.provideSettingsStore(context, AppProcess.DEMO)
        val background = AppModule.provideBackgroundSettingsStore(context, AppProcess.DEMO)
        val secrets = AppModule.provideSecretsStore(context, AppProcess.DEMO)
        settings.setLastUsedProfileId(1)
        assertEquals(1L, settings.snapshot.first().lastUsedProfileId)
        assertNull(secrets.passwordFor(1))
        secrets.setPassword(1, null)
        assertTrue(runCatching { secrets.setPassword(1, "never-store-this") }.isFailure)
        assertNull(secrets.passwordFor(1))
        val controller = KeepAliveController(context, background, AppProcess.DEMO)
        controller.setEnabled(true)
        assertFalse(controller.start())
        controller.stop()
        controller.setEnabled(false)
        assertFalse(background.settings.first().keepAliveEnabled)
        verify { context wasNot Called }
    }

    @Test
    fun demoClientProviderRejectsRealHostsAndSeparateTestFactoryCannotCreateClients() = runTest {
        val client = ObsProcessModule.provideObsClient(backgroundScope, AppProcess.DEMO)
        assertTrue(client is DemoObsClient)
        client.connect("127.0.0.1", 4455, null)
        assertTrue(client.connectionState.value is ConnectionState.Failed)
        client.connect(DemoObsClient.HOST, 4455, null)
        assertTrue(client.connectionState.value is ConnectionState.Ready)
        val factory = ObsProcessModule.provideObsClientFactory(AppProcess.DEMO)
        assertTrue(
            runCatching { factory.create(backgroundScope) }.exceptionOrNull()
                is ObsConnectionTestingUnavailableException
        )
        client.disconnect()
    }

    @Test
    fun destructiveDemoEditsAndGlobalRetentionLeaveLiveSentinelsUntouched() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("scenedeck.db")
        val live = AppModule.provideDatabase(context, AppProcess.LIVE)
        val demo = AppModule.provideDatabase(context, AppProcess.DEMO)
        try {
            seed(live, "Live label")
            val files = context.databaseList().toSet()
            assertNull(demo.connectionProfileDao().byId(1))
            seed(demo, "Demo label")
            demo.workspaceSceneDao().deleteStale(1, "Studio", emptyList())
            demo.historySessionDao().prune(0)
            demo.bookmarkDao().prune(0)
            withContext(Dispatchers.IO) { demo.clearAllTables() }
            assertEquals(files, context.databaseList().toSet())
            assertEquals(
                "Live label",
                live.workspaceSceneDao().byName(1, "Studio", "Camera")?.localAlias,
            )
            assertNotNull(live.historySessionDao().get("session"))
            assertNotNull(live.bookmarkDao().get("bookmark"))
            assertNotNull(live.connectionProfileDao().byId(1))
        } finally {
            demo.close()
            live.close()
            context.deleteDatabase("scenedeck.db")
        }
    }

    private suspend fun seed(database: SceneDeckDatabase, label: String) {
        database
            .connectionProfileDao()
            .insert(ConnectionProfileEntity(1, "Profile", "host", 4455, 1))
        database
            .workspaceSceneDao()
            .upsert(
                SceneRegistryEntity(
                    "Camera",
                    "PRIMARY",
                    connectionProfileId = 1,
                    sceneCollectionName = "Studio",
                    localAlias = label,
                )
            )
        database
            .historySessionDao()
            .insert(
                HistorySessionEntity(
                    sessionId = "session",
                    ownerId = "owner",
                    generation = 1,
                    connectionProfileId = 1,
                    sceneCollectionName = "Studio",
                    startedAtMs = 1,
                    lastObservedAtMs = 1,
                )
            )
        database
            .bookmarkDao()
            .insert(
                BookmarkEntity(
                    bookmarkId = "bookmark",
                    connectionProfileId = 1,
                    sceneCollectionName = "Studio",
                    observationId = "session",
                    createdAtMs = 1,
                    label = label,
                    times = "{}",
                )
            )
    }
}
