package com.scenedeck.android.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.ConnectionProfileEntity
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ObsSessionHolderTest {

    private lateinit var settings: SettingsRepository
    private lateinit var client: FakeObsClient
    private lateinit var profiles: ProfileRepository
    private lateinit var secrets: FakeSecretsStore
    private lateinit var holderScope: CoroutineScope

    @Before
    fun setUp() {
        settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
        client = FakeObsClient()
        profiles = ProfileRepository(FakeProfileDao())
        secrets = FakeSecretsStore()
        // Not the runBlocking scope: the holder's auto-connect watcher suspends forever
        // while onboarding is incomplete, which would hang runBlocking at its end.
        holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        holderScope.cancel()
    }

    @Test
    fun connectFetchesProfileAndSecret(): Unit = runBlocking {
        val holder = ObsSessionHolder(client, profiles, secrets, settings, holderScope)

        holder.connect(FakeProfileDao.PROFILE_ID)

        withTimeout(5_000) { while (client.connectCalls.isEmpty()) delay(25) }
        val call = client.connectCalls.single()
        assertEquals("studio.local", call.host)
        assertEquals(4456, call.port)
        assertEquals("secret-pw", call.password)
        // markUsed persisted.
        withTimeout(5_000) {
            while (settings.settings.first().lastUsedProfileId != FakeProfileDao.PROFILE_ID) delay(25)
        }
    }

    @Test
    fun autoConnectsToLastUsedProfileAfterOnboarding(): Unit = runBlocking {
        settings.setOnboardingCompleted(true)
        settings.setLastUsedProfileId(FakeProfileDao.PROFILE_ID)

        ObsSessionHolder(client, profiles, secrets, settings, holderScope)

        withTimeout(5_000) { while (client.connectCalls.isEmpty()) delay(25) }
        assertEquals("studio.local", client.connectCalls.single().host)
    }

    @Test
    fun noAutoConnectBeforeOnboarding(): Unit = runBlocking {
        ObsSessionHolder(client, profiles, secrets, settings, holderScope)
        delay(500)
        assertEquals(0, client.connectCalls.size)
    }

    @Test
    fun disconnectDelegatesToClient(): Unit = runBlocking {
        val holder = ObsSessionHolder(client, profiles, secrets, settings, holderScope)
        holder.disconnect()
        withTimeout(5_000) { while (!client.disconnectCalled) delay(25) }
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private class FakeSecretsStore : SecretsStore {
        override suspend fun passwordFor(profileId: Long): String? = "secret-pw"
        override suspend fun setPassword(profileId: Long, password: String?) = Unit
    }

    private class FakeProfileDao : ConnectionProfileDao {
        private val entity = ConnectionProfileEntity(
            id = PROFILE_ID,
            name = "Studio",
            host = "studio.local",
            port = 4456,
            createdAt = 1L,
        )

        override fun observeAll(): Flow<List<ConnectionProfileEntity>> = flowOf(listOf(entity))
        override suspend fun byId(id: Long): ConnectionProfileEntity? =
            entity.takeIf { it.id == id }

        override suspend fun lastUsed(): ConnectionProfileEntity? = entity
        override suspend fun insert(entity: ConnectionProfileEntity): Long = entity.id
        override suspend fun update(entity: ConnectionProfileEntity) = Unit
        override suspend fun markUsed(id: Long, usedAt: Long) = Unit
        override suspend fun delete(entity: ConnectionProfileEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit

        companion object {
            const val PROFILE_ID = 7L
        }
    }

    @Suppress("TooManyFunctions")
    private class FakeObsClient : ObsClient {
        data class ConnectCall(val host: String, val port: Int, val password: String?)

        val connectCalls = mutableListOf<ConnectCall>()

        @Volatile var disconnectCalled = false

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val events: SharedFlow<ObsEvent> = MutableSharedFlow()
        override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

        override suspend fun connect(host: String, port: Int, password: String?) {
            connectCalls += ConnectCall(host, port, password)
            _connectionState.value = ConnectionState.Ready(
                ObsVersionInfo("31.0.1", "5.6.1", 1, "test"),
            )
        }

        override suspend fun disconnect() {
            disconnectCalled = true
            _connectionState.value = ConnectionState.Disconnected
        }

        private fun unused(): Nothing = throw NotImplementedError("not needed by these tests")

        override suspend fun getVersion() = unused()
        override suspend fun getStats(): ObsStats = unused()
        override suspend fun getSceneList() = unused()
        override suspend fun getCurrentProgramScene(): String = unused()
        override suspend fun setCurrentProgramScene(sceneName: String) = unused()
        override suspend fun getSceneItemList(sceneName: String) = unused()
        override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = unused()
        override suspend fun getSpecialInputs() = unused()
        override suspend fun getInputMute(inputName: String) = unused()
        override suspend fun setInputMute(inputName: String, muted: Boolean) = unused()
        override suspend fun getInputVolume(inputName: String) = unused()
        override suspend fun setInputVolume(inputName: String, volumeMul: Double) = unused()
        override suspend fun getStreamStatus() = unused()
        override suspend fun startStream() = unused()
        override suspend fun stopStream() = unused()
        override suspend fun getRecordStatus() = unused()
        override suspend fun startRecord() = unused()
        override suspend fun stopRecord() = unused()
        override suspend fun getProfileList() = unused()
        override suspend fun setCurrentProfile(profileName: String) = unused()
        override suspend fun getSceneCollectionList() = unused()
        override suspend fun setCurrentSceneCollection(collectionName: String) = unused()

        override suspend fun getStudioModeEnabled(): Boolean = unused()
        override suspend fun setStudioModeEnabled(enabled: Boolean) = unused()
        override suspend fun getCurrentPreviewScene(): String = unused()
        override suspend fun setCurrentPreviewScene(sceneName: String) = unused()
        override suspend fun triggerStudioModeTransition() = unused()
        override suspend fun getSceneTransitionList() = unused()
        override suspend fun getCurrentSceneTransition() = unused()
        override suspend fun setCurrentSceneTransition(transitionName: String) = unused()
        override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = unused()
        override suspend fun getSourceScreenshot(
            sourceName: String,
            format: String,
            compressionQuality: Int,
            width: Int?,
            height: Int?,
        ): ByteArray = unused()
    }
}
