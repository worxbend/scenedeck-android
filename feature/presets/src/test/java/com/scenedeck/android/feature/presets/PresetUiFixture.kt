package com.scenedeck.android.feature.presets

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.ActiveConnection
import com.scenedeck.android.core.data.HistoryBookmarkLink
import com.scenedeck.android.core.data.HistoryRepository
import com.scenedeck.android.core.data.HistorySeed
import com.scenedeck.android.core.data.LocalProcessIdentity
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.MonotonicClock
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.PresetDraft
import com.scenedeck.android.core.data.PresetPreparation
import com.scenedeck.android.core.data.PresetRepository
import com.scenedeck.android.core.data.PresetRunLauncher
import com.scenedeck.android.core.data.PresetRunner
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.PresetTargetsRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.RecordingToolsRepository
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.SharedPresetExecutionSource
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.data.StoredPresetRunHistory
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.withPresets(block: suspend PresetUiFixture.() -> Unit) {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val android = ApplicationProvider.getApplicationContext<Context>()
    val database =
        Room.inMemoryDatabaseBuilder(android, SceneDeckDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .allowMainThreadQueries()
            .build()
    val settingsFile = File.createTempFile("presets-test", ".preferences_pb", android.cacheDir)
    val store = ViewModelStore()
    try {
        val settings =
            SettingsRepository(
                SceneDeckSettingsStore.forTesting(
                    PreferenceDataStoreFactory.create(
                        scope = backgroundScope,
                        produceFile = { settingsFile },
                    )
                )
            )
        val fixture = PresetUiFixture(this, database, settings)
        store.put("presets", fixture.model)
        backgroundScope.launch { fixture.model.uiState.collect() }
        block(fixture)
    } finally {
        store.clear()
        runCurrent()
        backgroundScope.coroutineContext.job.cancelAndJoin()
        database.close()
        settingsFile.delete()
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class PresetUiFixture(
    scope: TestScope,
    val database: SceneDeckDatabase,
    val settings: SettingsRepository,
) {
    val origin = TEST_WORKSPACE
    val workspaces = MutableWorkspaceContext()
    val repository = PresetRepository(database, workspaces)
    val wire = PresetTestClient()
    private val clock = MonotonicClock { scope.testScheduler.currentTime }
    val process = LocalProcessIdentity()
    val history = HistoryRepository(database, process)
    val seed = HistorySeed("observation", "capture", 0, origin.key, 1_000)
    private val savedRuns =
        StoredPresetRunHistory(database, process) { HistoryBookmarkLink(seed.id, 50) }
    private val mixer =
        MixerRepository(wire.client, settings, workspaces, scope.backgroundScope, clock)
    private val stats = StatsRepository(wire.client, scope.backgroundScope, clock)
    private val recording =
        RecordingToolsRepository(
            wire.client,
            stats,
            ActiveConnection(),
            clock,
            scope.backgroundScope,
        )
    private val source =
        SharedPresetExecutionSource(wire.client, repository, settings, mixer, recording)
    private val preparation = PresetPreparation(wire.client, repository, settings, clock)
    val launcher =
        PresetRunLauncher(PresetRunner(source, savedRuns, clock, { 1_050 }), scope.backgroundScope)
    val editor = PresetEditorController(repository, scope.backgroundScope)
    val review =
        PresetReviewController(
            repository,
            preparation,
            source,
            launcher,
            clock,
            scope.backgroundScope,
        )
    private val registry =
        RegistryRepository(database.workspaceSceneDao(), workspaces, database.sceneRegistryDao())
    private val scenes = ObsStateRepository(wire.client, registry, scope.backgroundScope, settings)
    private val workspace =
        PresetWorkspace(
            repository,
            PresetTargetsRepository(wire.client, workspaces),
            scenes,
            ProfileRepository(database.connectionProfileDao()),
        )
    val model = PresetsViewModel(repository, preparation, source, launcher, clock, workspace)

    suspend fun save(
        steps: List<PresetStep> = listOf(PresetStep.Mute("Mic", true)),
        name: String = "Interview",
    ) = repository.save(origin, null, PresetDraft(name = name, steps = steps))
}
