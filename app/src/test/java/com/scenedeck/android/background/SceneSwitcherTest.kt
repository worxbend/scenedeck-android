package com.scenedeck.android.background

import com.scenedeck.android.core.data.ConnectionProfile
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.UserSettings
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SceneSwitcherTest {

    private val ready =
        ConnectionState.Ready(
            ObsVersionInfo(obsVersion = "31.0.0", obsWebSocketVersion = "5.5.2", rpcVersion = 1)
        )

    private lateinit var connectionFlow: MutableStateFlow<ConnectionState>
    private val client = mockk<ObsClient>()
    private val sessionHolder = mockk<ObsSessionHolder>()
    private val settings = mockk<SettingsRepository>()
    private val profiles = mockk<ProfileRepository>()
    private val deck = mockk<ObsStateRepository>()

    private lateinit var switcher: SceneSwitcher

    @Before
    fun setUp() {
        connectionFlow = MutableStateFlow(ConnectionState.Disconnected)
        every { client.connectionState } returns connectionFlow
        coEvery { deck.setCurrentProgramScene(TEST_WORKSPACE, any()) } returns Unit
        switcher =
            SceneSwitcher(
                client,
                sessionHolder,
                settings,
                profiles,
                deck,
                MutableWorkspaceContext(),
            )
    }

    @Test
    fun `already ready switches without reconnecting`() = runTest {
        connectionFlow.value = ready

        val result = switcher.switchTo("Cam 1")

        assertEquals(SceneSwitchResult.Success, result)
        coVerify(exactly = 0) { sessionHolder.connectAndAwait(any()) }
        coVerify { deck.setCurrentProgramScene(TEST_WORKSPACE, "Cam 1") }
    }

    @Test
    fun `disconnected connects to last used profile then switches`() = runTest {
        every { settings.settings } returns flowOf(UserSettings(lastUsedProfileId = 7L))
        coEvery { sessionHolder.connectAndAwait(7L) } coAnswers
            {
                connectionFlow.value = ready
                ready
            }

        val result = switcher.switchTo("Screen")

        assertEquals(SceneSwitchResult.Success, result)
        coVerify { sessionHolder.connectAndAwait(7L) }
        coVerify { deck.setCurrentProgramScene(TEST_WORKSPACE, "Screen") }
    }

    @Test
    fun `falls back to most recently used profile when setting is absent`() = runTest {
        every { settings.settings } returns flowOf(UserSettings(lastUsedProfileId = null))
        coEvery { profiles.lastUsed() } returns profile(id = 3L)
        coEvery { sessionHolder.connectAndAwait(3L) } coAnswers
            {
                connectionFlow.value = ready
                ready
            }

        val result = switcher.switchTo("Screen")

        assertEquals(SceneSwitchResult.Success, result)
        coVerify { sessionHolder.connectAndAwait(3L) }
    }

    @Test
    fun `no profile at all returns NoProfile without touching OBS`() = runTest {
        every { settings.settings } returns flowOf(UserSettings(lastUsedProfileId = null))
        coEvery { profiles.lastUsed() } returns null

        val result = switcher.switchTo("Screen")

        assertEquals(SceneSwitchResult.NoProfile, result)
        coVerify(exactly = 0) { sessionHolder.connectAndAwait(any()) }
        coVerify(exactly = 0) { deck.setCurrentProgramScene(TEST_WORKSPACE, any()) }
    }

    @Test
    fun `terminal connection failure returns NotConnected`() = runTest {
        every { settings.settings } returns flowOf(UserSettings(lastUsedProfileId = 1L))
        coEvery { sessionHolder.connectAndAwait(1L) } returns
            ConnectionState.Failed(ConnectionError.Unreachable())

        val result = switcher.switchTo("Screen")

        assertEquals(SceneSwitchResult.NotConnected, result)
        coVerify(exactly = 0) { deck.setCurrentProgramScene(TEST_WORKSPACE, any()) }
    }

    @Test
    fun `OBS rejecting the switch returns RequestFailed`() = runTest {
        connectionFlow.value = ready
        coEvery { deck.setCurrentProgramScene(TEST_WORKSPACE, "Ghost") } throws
            ObsRequestFailedException("SetCurrentProgramScene", "ResourceNotFound", null)

        val result = switcher.switchTo("Ghost")

        assertEquals(SceneSwitchResult.RequestFailed, result)
    }

    @Test
    fun `retry ignores previous failed connection state`() = runTest {
        connectionFlow.value = ConnectionState.Failed(ConnectionError.Auth())
        every { settings.settings } returns flowOf(UserSettings(lastUsedProfileId = 7L))
        coEvery { sessionHolder.connectAndAwait(7L) } coAnswers
            {
                connectionFlow.value = ready
                ready
            }
        assertEquals(SceneSwitchResult.Success, switcher.switchTo("Screen"))
        coVerify { deck.setCurrentProgramScene(TEST_WORKSPACE, "Screen") }
    }

    @Test
    fun `widget never switches a same named scene on another workspace`() = runTest {
        connectionFlow.value = ready
        assertEquals(
            SceneSwitchResult.RequestFailed,
            switcher.switchTo("Cam 1", WorkspaceKey(99, "Studio")),
        )
        assertEquals(
            SceneSwitchResult.RequestFailed,
            switcher.switchTo("Cam 1", WorkspaceKey(7, "Other collection")),
        )
        coVerify(exactly = 0) { deck.setCurrentProgramScene(any(), any()) }
        coVerify(exactly = 0) { sessionHolder.connectAndAwait(any()) }
    }

    @Test
    fun `cold widget uses its own profile without reading last used preferences`() = runTest {
        coEvery { sessionHolder.connectAndAwait(7) } coAnswers
            {
                connectionFlow.value = ready
                ready
            }
        assertEquals(SceneSwitchResult.Success, switcher.switchTo("Cam 1", TEST_WORKSPACE.key))
        coVerify { sessionHolder.connectAndAwait(7) }
        coVerify { deck.setCurrentProgramScene(TEST_WORKSPACE, "Cam 1") }
    }

    private fun profile(id: Long) =
        ConnectionProfile(
            id = id,
            name = "Living room",
            host = "10.0.2.2",
            port = 4455,
            createdAt = 1L,
        )
}
