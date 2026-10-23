package com.scenedeck.android

import com.scenedeck.android.background.KeepAliveController
import com.scenedeck.android.background.SceneSwitchResult
import com.scenedeck.android.background.SceneSwitcher
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.data.UserSettings
import com.scenedeck.android.core.model.ConnectionState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** M13: scene deep-link outcomes reach the UI instead of failing silently. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelSceneLinkTest {

    private val switcher = mockk<SceneSwitcher>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): AppViewModel {
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns flowOf(UserSettings())
        val session = mockk<ObsSessionHolder>()
        every { session.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        val keepAlive = mockk<KeepAliveController>()
        every { keepAlive.keepAliveEnabled } returns flowOf(false)
        val stats = mockk<StatsRepository>()
        every { stats.telemetry } returns MutableStateFlow(Telemetry())
        return AppViewModel(settings, session, switcher, keepAlive, stats)
    }

    @Test
    fun `unknown scene surfaces a sanitized message until shown`() {
        coEvery { switcher.switchTo("Ghost") } returns SceneSwitchResult.RequestFailed
        val vm = viewModel()

        vm.onSceneLink("Ghost")

        val message = vm.sceneLinkMessage.value
        assertEquals("OBS couldn't switch to “Ghost”. Check that the scene exists.", message)
        vm.onSceneLinkMessageShown(message!!)
        assertNull(vm.sceneLinkMessage.value)
    }

    @Test
    fun `no profile and not connected are reported`() {
        coEvery { switcher.switchTo("A") } returns SceneSwitchResult.NoProfile
        coEvery { switcher.switchTo("B") } returns SceneSwitchResult.NotConnected
        val vm = viewModel()

        vm.onSceneLink("A")
        assertEquals(
            "Can't switch scenes yet: add an OBS connection in SceneDeck first.",
            vm.sceneLinkMessage.value,
        )
        vm.onSceneLink("B")
        assertEquals("Couldn't reach OBS to switch to “B”.", vm.sceneLinkMessage.value)
    }

    @Test
    fun `success stays silent and stale acknowledgement keeps newer message`() {
        coEvery { switcher.switchTo("Cam") } returns SceneSwitchResult.Success
        coEvery { switcher.switchTo("Ghost") } returns SceneSwitchResult.RequestFailed
        val vm = viewModel()

        vm.onSceneLink("Cam")
        assertNull(vm.sceneLinkMessage.value)

        vm.onSceneLink("Ghost")
        vm.onSceneLinkMessageShown("older message")
        assertEquals(
            "OBS couldn't switch to “Ghost”. Check that the scene exists.",
            vm.sceneLinkMessage.value,
        )
    }
}
