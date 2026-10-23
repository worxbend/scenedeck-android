package com.scenedeck.android.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = SettingsRepository(newIsolatedStore())
    }

    @Test
    fun defaultsAreApplied(): Unit = runBlocking {
        val settings = repository.settings.first()
        assertEquals("SCENEDECK", settings.themeFamily)
        assertEquals(DarkMode.SYSTEM, settings.darkMode)
        assertFalse(settings.dynamicColor)
        assertEquals("FULL", settings.motionLevel)
        assertTrue(settings.haptics)
        assertFalse(settings.keepScreenOn)
        assertFalse(settings.onboardingCompleted)
        assertEquals(null, settings.lastUsedProfileId)
    }

    @Test
    fun settersRoundTrip(): Unit = runBlocking {
        repository.setThemeFamily("NORD")
        repository.setDarkMode(DarkMode.DARK)
        repository.setDynamicColor(true)
        repository.setMotionLevel("REDUCED")
        repository.setHaptics(false)
        repository.setKeepScreenOn(true)
        repository.setLastUsedProfileId(42L)
        repository.setOnboardingCompleted(true)

        val settings = repository.settings.first()
        assertEquals("NORD", settings.themeFamily)
        assertEquals(DarkMode.DARK, settings.darkMode)
        assertTrue(settings.dynamicColor)
        assertEquals("REDUCED", settings.motionLevel)
        assertFalse(settings.haptics)
        assertTrue(settings.keepScreenOn)
        assertEquals(42L, settings.lastUsedProfileId)
        assertTrue(settings.onboardingCompleted)
    }

    @Test
    fun updateTransformsOnlyTouchedFields(): Unit = runBlocking {
        repository.setThemeFamily("DRACULA")
        repository.setHaptics(false)

        repository.update { it.copy(keepScreenOn = true) }

        val settings = repository.settings.first()
        assertEquals("DRACULA", settings.themeFamily)
        assertFalse(settings.haptics)
        assertTrue(settings.keepScreenOn)
    }

    @Test
    fun densityAndMeterRefreshRoundTrip(): Unit = runBlocking {
        val defaults = repository.settings.first()
        assertEquals("COMFORTABLE", defaults.uiDensity)
        assertEquals(MeterRefresh.SMOOTH, defaults.meterRefresh)
        assertEquals(emptySet<String>(), defaults.audioAllowList)

        repository.setUiDensity("COMPACT")
        repository.setMeterRefresh(MeterRefresh.SAVER)
        repository.update { it.copy(audioAllowList = setOf("Mic/Aux")) }

        val settings = repository.settings.first()
        assertEquals("COMPACT", settings.uiDensity)
        assertEquals(MeterRefresh.SAVER, settings.meterRefresh)
        assertEquals(setOf("Mic/Aux"), settings.audioAllowList)
    }

    @Test
    fun corruptMeterRefreshFallsBackToSmooth(): Unit = runBlocking {
        val store = newIsolatedStore()
        store.setMeterRefresh("NOPE")
        assertEquals(MeterRefresh.SMOOTH, SettingsRepository(store).settings.first().meterRefresh)
    }

    @Test
    fun corruptEnumValueFallsBackToSystem(): Unit = runBlocking {
        val store = newIsolatedStore()
        store.setDarkMode("NOPE")
        assertEquals(DarkMode.SYSTEM, SettingsRepository(store).settings.first().darkMode)
    }

    @Test
    fun concurrentTransformsPreserveEveryLock(): Unit = runBlocking {
        coroutineScope {
            repeat(40) { index ->
                launch {
                    repository.update { it.copy(lockedInputs = it.lockedInputs + "Input $index") }
                }
            }
        }
        assertEquals(40, repository.settings.first().lockedInputs.size)
    }

    @Test
    fun concurrentLockCommandsPreserveEveryInput(): Unit = runBlocking {
        coroutineScope {
            repeat(40) { index -> launch { repository.setInputLocked("Input $index", true) } }
        }
        assertEquals(40, repository.settings.first().lockedInputs.size)
    }

    internal companion object {
        fun newIsolatedStore(): SceneDeckSettingsStore =
            SceneDeckSettingsStore.forTesting(
                PreferenceDataStoreFactory.create(
                    produceFile = { File.createTempFile("settings_test", ".preferences_pb") }
                )
            )
    }
}
