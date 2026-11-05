package com.scenedeck.android.demo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.ActiveConnection
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.database.ConnectionProfileEntity
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsWorkspaceChangedException
import com.scenedeck.android.core.obs.demo.DemoObsClient
import com.scenedeck.android.core.obs.withSession
import com.scenedeck.android.di.AppModule
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DemoRuntimeTest {
    @Test
    fun retainedVisitPreservesOutputsButExitInvalidatesCommandsAndReentryStartsIdle() =
        runBlocking {
            fixture { f ->
                val visit = Any()
                f.open(visit)
                val token = f.ready().token
                f.client.startStream()
                f.client.startRecord()
                f.client.pauseRecord()
                f.runtime.open(visit)
                assertEquals(token, f.ready().token)
                assertTrue(f.client.getStreamStatus().active)
                assertTrue(f.client.getRecordStatus().paused)
                val collection = f.client.getSceneCollectionList().currentCollection
                val name = f.client.getSceneList().scenes.first().name
                val row = requireNotNull(f.database.workspaceSceneDao().byName(1, collection, name))
                f.database.workspaceSceneDao().upsert(row.copy(localAlias = "My practice label"))
                f.runtime.close(visit)
                assertEquals(ConnectionState.Disconnected, f.client.connectionState.value)
                assertNull(f.active.target.value.profileId)
                assertEquals(DemoPhase.CLOSED, f.runtime.state.value)
                f.open(Any())
                assertNotEquals(token, f.ready().token)
                assertFalse(f.client.getStreamStatus().active)
                assertFalse(f.client.getRecordStatus().active)
                assertEquals(
                    "My practice label",
                    f.database.workspaceSceneDao().byName(1, collection, name)?.localAlias,
                )
                assertEquals(1, f.database.connectionProfileDao().observeAll().first().size)
                assertTrue(
                    runCatching { f.client.withSession(token) { f.client.startStream() } }
                        .exceptionOrNull() is ObsWorkspaceChangedException
                )
                assertFalse(f.client.getStreamStatus().active)
            }
        }

    @Test
    fun obsoleteOwnerCannotDisconnectReplacementAndFreshGraphsDoNotRestoreDemoActions() =
        runBlocking {
            fixture { f ->
                val old = Any()
                f.open(old)
                f.client.startStream()
                f.open(Any())
                val token = f.ready().token
                f.runtime.close(old)
                assertEquals(DemoPhase.READY, f.runtime.state.value)
                assertEquals(token, f.ready().token)
                assertTrue(f.active.target.value.confirmed)
                assertFalse(f.client.getStreamStatus().active)
            }
            fixture { fresh ->
                fresh.open(Any())
                assertFalse(fresh.client.getStreamStatus().active)
                assertFalse(fresh.client.getRecordStatus().active)
                assertEquals(5, fresh.client.getSceneList().scenes.size)
                assertTrue(fresh.settings.settings.first().confirmStartStream)
                assertTrue(fresh.settings.settings.first().onboardingCompleted)
            }
        }

    @Test
    fun exitDuringStartupCancelsPreparationWithoutLeavingReadyOrDuplicatingProfiles() =
        runBlocking {
            fixture(blockConnect = true) { f ->
                val visit = Any()
                f.runtime.open(visit)
                withTimeout(5_000) { f.connectStarted.await() }
                f.runtime.close(visit)
                assertNull(f.active.target.value.profileId)
                withTimeout(5_000) { f.connectCancelled.await() }
                assertEquals(DemoPhase.CLOSED, f.runtime.state.value)
                assertFalse(f.client.connectionState.value is ConnectionState.Ready)
                f.blockConnect = false
                f.open(Any())
                assertEquals(1, f.database.connectionProfileDao().observeAll().first().size)
                assertFalse(f.client.getStreamStatus().active)
            }
        }

    @Test
    fun runtimeCannotBeOpenedInLiveGraph() {
        val session = mockk<ObsSessionHolder>()
        val runtime =
            DemoRuntime(AppProcess.LIVE, mockk(), mockk(), session, CoroutineScope(SupervisorJob()))
        assertTrue(runCatching { runtime.open(Any()) }.isFailure)
        assertEquals(DemoPhase.CLOSED, runtime.state.value)
        verify { session wasNot Called }
    }

    @Test
    fun failedStartupRemainsLocalAndRetryReusesItsDraftProfile() = runBlocking {
        fixture { f ->
            val invalid = ConnectionProfileEntity(1, "Invalid demo fixture", "127.0.0.1", 4455, 1)
            f.database.connectionProfileDao().insert(invalid)
            val visit = Any()
            f.runtime.open(visit)
            withTimeout(5_000) { f.runtime.state.first { it == DemoPhase.FAILED } }
            assertFalse(f.client.connectionState.value is ConnectionState.Ready)
            f.database.connectionProfileDao().update(invalid.copy(host = DemoObsClient.HOST))
            f.open(visit)
            assertEquals(1, f.database.connectionProfileDao().observeAll().first().size)
            assertFalse(f.client.getStreamStatus().active)
        }
    }

    private suspend fun fixture(blockConnect: Boolean = false, block: suspend (Fixture) -> Unit) {
        val f = Fixture(blockConnect)
        try {
            block(f)
        } finally {
            f.scope.cancel()
            f.scope.coroutineContext.job.join()
            f.database.close()
        }
    }

    private class Fixture(delayConnect: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val database =
            AppModule.provideDatabase(
                ApplicationProvider.getApplicationContext<Context>(),
                AppProcess.DEMO,
            )
        val settings = SettingsRepository(SceneDeckSettingsStore.ephemeral())
        val active = ActiveConnection()
        private val simulated = DemoObsClient(scope, { System.nanoTime() / 1_000_000 })
        @Volatile var blockConnect = delayConnect
        val connectStarted = CompletableDeferred<Unit>()
        val connectCancelled = CompletableDeferred<Unit>()
        val client: ObsClient =
            if (!delayConnect) simulated
            else
                object : ObsClient by simulated {
                    override suspend fun connect(host: String, port: Int, password: String?) {
                        if (blockConnect) {
                            connectStarted.complete(Unit)
                            try {
                                CompletableDeferred<Unit>().await()
                            } finally {
                                connectCancelled.complete(Unit)
                            }
                        }
                        simulated.connect(host, port, password)
                    }
                }
        private val holder =
            ObsSessionHolder(
                client,
                ProfileRepository(database.connectionProfileDao()),
                DemoSecretsStore,
                settings,
                scope,
                active,
            )
        val runtime = DemoRuntime(AppProcess.DEMO, database, settings, holder, scope)

        suspend fun open(owner: Any) {
            runtime.open(owner)
            withTimeout(5_000) {
                runtime.state.first { it == DemoPhase.READY || it == DemoPhase.FAILED }
            }
            assertEquals(DemoPhase.READY, runtime.state.value)
        }

        fun ready() = client.connectionState.value as ConnectionState.Ready
    }
}
