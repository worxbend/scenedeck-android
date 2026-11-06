package com.scenedeck.android

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppearanceStatePersistenceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shellObservesCommittedAppearanceAndCannotOverwriteItWithOtherControls() {
        val repository = SettingsRepository(SceneDeckSettingsStore.ephemeral())
        lateinit var state: SceneDeckAppState
        compose.setContent {
            state = rememberSceneDeckAppState(repository)
            Text(state.themeFamily.name)
        }
        runBlocking {
            repository.update {
                it.copy(themeFamily = "OCEAN", themeAccent = "TEAL", trueBlack = true)
            }
        }
        compose.waitUntil(5_000) { state.themeFamily == ThemeFamily.OCEAN }
        compose.runOnIdle {
            assertEquals(ThemeAccent.TEAL, state.themeAccent)
            assertTrue(state.trueBlack)
            // Simulate an obsolete local shell projection; only the appearance editor owns saves.
            state.themeFamily = ThemeFamily.OBS
            state.haptics = false
        }
        compose.waitForIdle()
        val saved = runBlocking {
            withTimeout(5_000) { repository.settings.first { !it.haptics } }
        }
        assertEquals("OCEAN", saved.themeFamily)
        assertEquals("TEAL", saved.themeAccent)
        assertTrue(saved.trueBlack)
    }
}
