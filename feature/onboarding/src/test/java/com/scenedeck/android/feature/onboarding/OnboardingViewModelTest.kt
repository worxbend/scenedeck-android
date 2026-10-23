package com.scenedeck.android.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.model.ConnectionError
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** H5: repeated Connect presses (and a recreated ViewModel) reuse one draft profile. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dao = MemoryProfiles()
    private val secrets = MemorySecrets()
    private val profiles = ProfileRepository(dao)
    private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val connectHosts = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var holderScope: CoroutineScope
    private lateinit var client: ObsClient
    private lateinit var holder: ObsSessionHolder
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        client = failingClient()
        settings =
            SettingsRepository(
                SceneDeckSettingsStore.forTesting(
                    PreferenceDataStoreFactory.create(
                        produceFile = {
                            File.createTempFile("onboarding_settings", ".preferences_pb")
                        }
                    )
                )
            )
        holder = ObsSessionHolder(client, profiles, secrets, settings, holderScope)
    }

    @After
    fun tearDown() {
        holderScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun failedRetriesAndRecreationReuseOneDraftProfile(): Unit = runBlocking {
        val savedState = SavedStateHandle()
        val first = newViewModel(savedState)

        first.connectAndAwaitFailure("My OBS", "10.0.0.9", "wrong")
        first.connectAndAwaitFailure("My OBS", "192.168.1.20", "right")

        assertEquals(1, dao.rows.size)
        val id = dao.rows.keys.single()
        assertEquals(listOf("10.0.0.9", "192.168.1.20"), connectHosts.toList())
        assertEquals("right", secrets.passwords[id])

        // Process death: only the non-secret draft id survives in saved state.
        assertEquals(setOf(KEY_DRAFT_PROFILE_ID), savedState.keys())
        val recreated = newViewModel(SavedStateHandle(mapOf(KEY_DRAFT_PROFILE_ID to id)))
        recreated.connectAndAwaitFailure("Studio", "studio.local", null)

        assertEquals(setOf(id), dao.rows.keys)
        assertEquals("studio.local", dao.rows.getValue(id).host)
        assertEquals(null, secrets.passwords[id])
    }

    private fun newViewModel(savedState: SavedStateHandle) =
        OnboardingViewModel(
            profiles = profiles,
            secrets = secrets,
            settings = settings,
            sessionHolder = holder,
            client = client,
            savedState = savedState,
        )

    private suspend fun OnboardingViewModel.connectAndAwaitFailure(
        name: String,
        host: String,
        password: String?,
    ) {
        val attempts = connectHosts.size
        connect(name, host, 4455, password)
        withTimeout(5_000) {
            connectState.first {
                it is OnboardingConnectState.Failed && connectHosts.size > attempts
            }
        }
        resetConnect()
    }

    /** Every connect fails as unreachable; any other call is unexpected. */
    private fun failingClient(): ObsClient {
        val events = MutableSharedFlow<Any>()
        return Proxy.newProxyInstance(
            ObsClient::class.java.classLoader,
            arrayOf(ObsClient::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getConnectionState" -> connection
                "getEvents",
                "getVolumeMeters" -> events
                "connect" -> {
                    connectHosts += args[0] as String
                    connection.value = ConnectionState.Failed(ConnectionError.Unreachable())
                    Unit
                }
                "disconnect" -> {
                    connection.value = ConnectionState.Disconnected
                    Unit
                }
                "retryNow" -> false
                "hashCode" -> System.identityHashCode(this)
                "toString" -> "FailingObsClient"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as ObsClient
    }

    private companion object {
        // Mirrors OnboardingViewModel's saved-state key.
        const val KEY_DRAFT_PROFILE_ID = "onboarding_draft_profile_id"
    }
}
