package com.scenedeck.android.feature.preflight

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.PreflightRepository
import com.scenedeck.android.core.data.PreflightRunner
import com.scenedeck.android.core.data.PreflightSetupState
import com.scenedeck.android.core.data.PreflightStatus
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreflightViewModelTest {
    private lateinit var database: SceneDeckDatabase
    private lateinit var repository: PreflightRepository
    private lateinit var model: PreflightViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = ViewModelStore()
    private val context = MutableWorkspaceContext()
    private val fake = PreflightTestClient()
    private val settingsFile = File.createTempFile("preflight_settings", ".preferences_pb")

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        repository = PreflightRepository(database, context)
        val settings =
            SettingsRepository(
                SceneDeckSettingsStore.forTesting(
                    PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile })
                )
            )
        val registry =
            RegistryRepository(database.workspaceSceneDao(), context, database.sceneRegistryDao())
        model =
            PreflightViewModel(
                repository,
                PreflightRunner(fake.client, context),
                fake.client,
                ObsStateRepository(fake.client, registry, scope, settings),
                MixerRepository(fake.client, settings, context, scope),
                ProfileRepository(database.connectionProfileDao()),
            )
        store.put("preflight", model)
        scope.launch { model.uiState.collect {} }
    }

    @After
    fun close() {
        store.clear()
        scope.cancel()
        database.close()
        settingsFile.delete()
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(predicate: (PreflightUiState) -> Boolean) =
        withTimeout(5_000) { model.uiState.first(predicate) }

    @Test
    fun rerunClearsOldSuccessGatesDuplicatesAndPublishesUnknownFailure(): Unit = runBlocking {
        val target =
            awaitState { it.setup is PreflightSetupState.Ready }.setup as PreflightSetupState.Ready
        model.run(target, false)
        val first = awaitState { it.run.report != null }
        assertEquals(
            PreflightStatus.PASS,
            first.run.report?.findings?.single { it.id == "disk" }?.status,
        )
        fake.statsGate = CompletableDeferred()
        fake.failStats = true
        model.run(target, false)
        model.run(target, false)
        val running = awaitState { it.run.running }
        assertNull(running.run.report)
        assertEquals(2, fake.statsCalls)
        fake.statsGate?.complete(Unit)
        val failed = awaitState { !it.run.running && it.run.report != null }
        assertEquals(
            PreflightStatus.UNKNOWN,
            failed.run.report?.findings?.single { it.id == "disk" }?.status,
        )
        assertFalse(failed.toString().contains("private server details"))
    }

    @Test
    fun contextAndConfigurationChangesInvalidateReportsAndRejectOldEditors(): Unit = runBlocking {
        val target =
            awaitState { it.setup is PreflightSetupState.Ready }.setup as PreflightSetupState.Ready
        model.run(target, false)
        awaitState { it.run.report != null }
        assertTrue(model.save(target, target.config.copy(minimumDiskMb = 1_024)))
        val changed = awaitState {
            (it.setup as? PreflightSetupState.Ready)?.config?.minimumDiskMb == 1_024L
        }
        assertNull(changed.run.report)
        assertFalse(model.save(target, target.config))
        fake.statsGate = CompletableDeferred()
        model.run(changed.setup as PreflightSetupState.Ready, false)
        awaitState { it.run.running }
        context.select(TEST_WORKSPACE.copy(targetGeneration = 2))
        val replaced = awaitState {
            (it.setup as? PreflightSetupState.Ready)?.origin?.targetGeneration == 2L
        }
        assertFalse(replaced.run.running)
        assertNull(replaced.run.report)
        fake.statsGate?.complete(Unit)
        assertFalse(model.save(target, target.config))
    }

    @Test
    fun cancelRemovesResultsAndStopsPendingRead(): Unit = runBlocking {
        val target =
            awaitState { it.setup is PreflightSetupState.Ready }.setup as PreflightSetupState.Ready
        fake.statsGate = CompletableDeferred()
        model.run(target, false)
        awaitState { it.run.running }
        model.cancel()
        val canceled = awaitState { it.run.error != null }
        assertFalse(canceled.run.running)
        assertNull(canceled.run.report)
    }
}
