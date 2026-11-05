package com.scenedeck.android.demo

import androidx.lifecycle.ViewModelStore
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.UserSettings
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.di.ObsProcessModule
import com.scenedeck.android.feature.connections.ConnectionsViewModel
import com.scenedeck.android.feature.connections.TestConnectionState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoConnectionTestBoundaryTest {
    @Test
    fun unsavedAndStoredCredentialTestsAreRefusedBeforeCredentialLookup() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val profiles = mockk<ProfileRepository>()
            val settings = mockk<SettingsRepository>()
            val holder = mockk<ObsSessionHolder>()
            val secrets = mockk<SecretsStore>()
            every { profiles.profiles } returns flowOf(emptyList())
            every { settings.settings } returns flowOf(UserSettings())
            every { holder.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
            val vm =
                ConnectionsViewModel(
                    profiles,
                    secrets,
                    settings,
                    holder,
                    ObsProcessModule.provideObsClientFactory(AppProcess.DEMO),
                )
            store.put("connections", vm)
            listOf(null, "typed-secret").forEach { password ->
                vm.testConnection("private-host", 4455, password, profileId = 99)
                assertEquals(
                    TestConnectionState.Failure(
                        ConnectionError.Protocol("Connection tests are unavailable in demo")
                    ),
                    vm.testState.value,
                )
                vm.resetTestState()
            }
            coVerify(exactly = 0) { secrets.passwordFor(any()) }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
