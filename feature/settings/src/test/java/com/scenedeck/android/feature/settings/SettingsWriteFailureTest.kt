package com.scenedeck.android.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsWriteFailureTest {
    @Test
    fun safetySettingFailuresShowSanitizedError(): Unit = runBlocking {
        val viewModel =
            SettingsViewModel(
                SettingsRepository(SceneDeckSettingsStore.forTesting(FailingSettingsStore()))
            )
        val actions: List<(SettingsViewModel) -> Unit> =
            listOf(
                { it.setConfirmStartStream(true) },
                { it.setConfirmStopStream(false) },
                { it.setConfirmStartRecord(true) },
                { it.setConfirmStopRecord(false) },
            )
        for (action in actions) {
            val message = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
            action(viewModel)
            assertEquals(
                "Couldn't save this setting. Please try again.",
                withTimeout(5_000) { message.await() },
            )
        }
    }

    private class FailingSettingsStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            throw IOException("sensitive/file/path")
        }
    }
}
