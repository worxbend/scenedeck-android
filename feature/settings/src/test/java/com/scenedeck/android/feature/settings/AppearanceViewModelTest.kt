package com.scenedeck.android.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun previewCancelAndAtomicSavePreserveOtherSettings() = runBlocking {
        val repository = SettingsRepository(SceneDeckSettingsStore.ephemeral())
        val vm = AppearanceViewModel(repository)
        try {
            vm.open()
            vm.edit {
                it.copy(family = ThemeFamily.OCEAN, accent = ThemeAccent.ROSE, trueBlack = true)
            }
            assertEquals("SCENEDECK", repository.settings.first().themeFamily)
            vm.close()
            assertNull(vm.state.value.draft)
            vm.open()
            vm.edit {
                it.copy(
                    family = ThemeFamily.PAPER,
                    mode = DarkMode.DARK,
                    accent = ThemeAccent.TEAL,
                    trueBlack = true,
                )
            }
            repository.update { it.copy(lastUsedProfileId = 42, lockedInputs = setOf("Mic")) }
            vm.save()
            withTimeout(5_000) { vm.state.first { it.draft == null } }
            val saved = repository.settings.first()
            assertEquals("PAPER", saved.themeFamily)
            assertEquals("TEAL", saved.themeAccent)
            assertTrue(saved.trueBlack)
            assertEquals(42L, saved.lastUsedProfileId)
            assertEquals(setOf("Mic"), saved.lockedInputs)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun concurrentAppearanceChangeRejectsSaveAndKeepsDraft() = runBlocking {
        val repository = SettingsRepository(SceneDeckSettingsStore.ephemeral())
        val vm = AppearanceViewModel(repository)
        try {
            vm.open()
            vm.edit { it.copy(family = ThemeFamily.AMBER) }
            repository.setThemeFamily("OBS")
            vm.save()
            withTimeout(5_000) { vm.state.first { it.error != null } }
            assertEquals(ThemeFamily.AMBER, vm.state.value.draft?.family)
            assertEquals("OBS", repository.settings.first().themeFamily)
            vm.close()
            vm.open()
            assertEquals(ThemeFamily.OBS, vm.state.value.draft?.family)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun failedSaveGatesDuplicatesRetainsDraftAndSanitizesError() = runBlocking {
        val storage = DelayedFailure()
        val vm = AppearanceViewModel(SettingsRepository(SceneDeckSettingsStore.forTesting(storage)))
        try {
            vm.open()
            vm.edit { it.copy(accent = ThemeAccent.VIOLET) }
            vm.save()
            vm.save()
            vm.close()
            assertTrue(vm.state.value.saving)
            assertEquals(1, storage.writes)
            storage.release.complete(Unit)
            val failed = withTimeout(5_000) { vm.state.first { it.error != null } }
            assertEquals(ThemeAccent.VIOLET, failed.draft?.accent)
            assertFalse(failed.error.orEmpty().contains("private"))
            assertFalse(failed.saving)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private class DelayedFailure : DataStore<Preferences> {
        val release = CompletableDeferred<Unit>()
        var writes = 0
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            writes++
            release.await()
            throw IOException("private/password/path")
        }
    }
}
