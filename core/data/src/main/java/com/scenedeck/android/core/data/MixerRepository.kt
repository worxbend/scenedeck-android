package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Snapshot of one mixer input: static metadata + current control values. */
data class MixerInputState(
    val name: String,
    val scope: MixerScope,
    val scopePath: String?,
    val volumeMul: Double,
    val muted: Boolean,
    /** LOCAL UI-only lock: disables fader/mute interaction (persisted in settings). */
    val locked: Boolean,
    /** OBS input kind (e.g. ffmpeg_source); null for special inputs. */
    val inputKind: String? = null,
)

/** Everything the mixer renders. */
data class MixerState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** Program scene the discovery followed (null while disconnected). */
    val activeScene: String? = null,
    val inputs: List<MixerInputState> = emptyList(),
)

/**
 * Owns audio discovery + live meter levels + mixer controls (docs/ARCHITECTURE.md rule 2; discovery
 * algorithm in [AudioDiscovery]).
 */
@Singleton
@OptIn(FlowPreview::class)
@Suppress("TooManyFunctions") // cohesive OBS-audio surface; splitting would scatter one concern
class MixerRepository
@Inject
constructor(
    private val client: ObsClient,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val discovery = AudioDiscovery(client)
    private val discoveryMutex = Mutex()
    private val controlLock = Any()
    private var controlRevision = 0L
    private val volumeControls = mutableMapOf<String, OptimisticControl<Double>>()
    private val muteControls = mutableMapOf<String, OptimisticControl<Boolean>>()
    private val discoveryRequests = MutableStateFlow(0L)

    private val discovered = MutableStateFlow<List<DiscoveredInput>>(emptyList())
    private val activeScene = MutableStateFlow<String?>(null)

    /** All OBS scene names (for the mixer scene picker), refreshed on discovery. */
    private val _sceneNames = MutableStateFlow<List<String>>(emptyList())
    val sceneNames: StateFlow<List<String>> = _sceneNames.asStateFlow()

    val mixerState: StateFlow<MixerState> =
        combine(
                client.connectionState,
                activeScene,
                discovered,
                settings.settings,
            ) { connection, scene, inputs, userSettings ->
                MixerState(
                    connection = connection,
                    activeScene = scene,
                    inputs =
                        inputs.map { input ->
                            MixerInputState(
                                name = input.name,
                                scope = input.scope,
                                scopePath = input.scopePath,
                                volumeMul = input.volumeMul,
                                muted = input.muted,
                                locked = input.name in userSettings.lockedInputs,
                                inputKind = input.inputKind,
                            )
                        },
                )
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), MixerState())

    private val _mediaStatus = MutableStateFlow<Map<String, MediaStatus>>(emptyMap())

    /**
     * Playback status per media-kind input, polled at ~2 s only while connected AND a collector is
     * subscribed (i.e. the mixer UI is actually visible).
     */
    val mediaStatus: StateFlow<Map<String, MediaStatus>> = _mediaStatus.asStateFlow()

    init {
        scope.launch {
            client.connectionState.collect { state ->
                synchronized(controlLock) {
                    requestDiscovery()
                    if (state !is ConnectionState.Ready) {
                        discovered.value = emptyList()
                        activeScene.value = null
                        _sceneNames.value = emptyList()
                        volumeControls.clear()
                        muteControls.clear()
                    }
                }
            }
        }
        scope.launch {
            discoveryRequests
                .debounce {
                    if (client.connectionState.value is ConnectionState.Ready) REFRESH_DEBOUNCE_MS
                    else 0L
                }
                .collectLatest { refreshDiscovery(it) }
        }
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ObsEvent.CurrentProgramSceneChanged -> requestDiscovery()

                    is ObsEvent.SceneNameChanged,
                    is ObsEvent.SceneItemEnableStateChanged,
                    is ObsEvent.SceneCreated,
                    is ObsEvent.SceneRemoved,
                    is ObsEvent.SceneListChanged,
                    is ObsEvent.InputCreated,
                    is ObsEvent.InputRemoved,
                    is ObsEvent.InputNameChanged -> requestDiscovery()

                    is ObsEvent.InputMuteStateChanged ->
                        observeInputMute(event.inputName, event.muted)

                    is ObsEvent.InputVolumeChanged ->
                        previewInputVolume(event.inputName, event.volumeMul)

                    else -> Unit
                }
            }
        }
        scope.launch {
            // Re-run discovery only when the allow-list changes (not on lock toggles).
            settings.settings
                .map { it.audioAllowList }
                .distinctUntilChanged()
                .collect { requestDiscovery() }
        }
        scope.launch {
            // Poll only while connected AND subscribed; mixerState emissions (e.g. per-frame
            // fader previews) must not restart the loop.
            combine(
                    client.connectionState,
                    _mediaStatus.subscriptionCount.map { it > 0 }.distinctUntilChanged(),
                ) { state, subscribed ->
                    state is ConnectionState.Ready && subscribed
                }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) {
                        pollMediaLoop()
                    } else {
                        _mediaStatus.value = emptyMap()
                    }
                }
        }
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ObsEvent.MediaInputPlaybackStarted,
                    is ObsEvent.MediaInputPlaybackEnded ->
                        refreshMediaStatus(
                            (event as? ObsEvent.MediaInputPlaybackStarted)?.inputName
                                ?: (event as ObsEvent.MediaInputPlaybackEnded).inputName
                        )

                    else -> Unit
                }
            }
        }
    }

    private fun requestDiscovery() =
        synchronized(controlLock) {
            discoveryRequests.value++
        }

    suspend fun refreshDiscovery() = refreshDiscovery(discoveryRequests.value)

    private suspend fun refreshDiscovery(generation: Long) = discoveryMutex.withLock {
        if (client.connectionState.value !is ConnectionState.Ready) return@withLock
        val startedAt = synchronized(controlLock) { controlRevision }
        val list = requestResult { client.getSceneList() }.getOrNull() ?: return@withLock
        val inputs = discoverScene(list.currentProgramScene)
        currentCoroutineContext().ensureActive()
        synchronized(controlLock) {
            if (
                client.connectionState.value !is ConnectionState.Ready ||
                    discoveryRequests.value != generation
            )
                return@synchronized
            activeScene.value = list.currentProgramScene
            _sceneNames.value = list.scenes.map { it.name }
            val names = inputs.mapTo(mutableSetOf()) { it.name }
            volumeControls.keys.retainAll(names)
            muteControls.keys.retainAll(names)
            discovered.value = inputs.map { input ->
                val volume =
                    volumeControls.getOrPut(input.name) {
                        OptimisticControl(input.volumeMul, startedAt)
                    }
                val mute =
                    muteControls.getOrPut(input.name) { OptimisticControl(input.muted, startedAt) }
                input.copy(
                    volumeMul = volume.observe(input.volumeMul, startedAt),
                    muted = mute.observe(input.muted, startedAt),
                )
            }
        }
    }

    /** Runs discovery for an arbitrary scene (mixer SELECTED mode). */
    suspend fun discoverScene(sceneName: String?): List<DiscoveredInput> =
        discovery.discover(sceneName, settings.settings.first().audioAllowList)

    suspend fun setInputVolume(inputName: String, volumeMul: Double) {
        val revision =
            synchronized(controlLock) {
                val revision = ++controlRevision
                volumeControls[inputName]?.begin(revision, volumeMul)?.let { value ->
                    updateInput(inputName) { it.copy(volumeMul = value) }
                }
                revision
            }
        var succeeded = false
        try {
            client.setInputVolume(inputName, volumeMul)
            succeeded = true
        } finally {
            synchronized(controlLock) {
                volumeControls[inputName]?.complete(revision, succeeded)?.let { value ->
                    updateInput(inputName) { it.copy(volumeMul = value) }
                }
            }
        }
    }

    /** Local preview or OBS event supersedes any older optimistic write. */
    fun previewInputVolume(inputName: String, volumeMul: Double) {
        synchronized(controlLock) {
            val revision = ++controlRevision
            volumeControls
                .getOrPut(inputName) { OptimisticControl(volumeMul, revision) }
                .observe(volumeMul, revision)
            updateInput(inputName) { it.copy(volumeMul = volumeMul) }
        }
    }

    private fun observeInputMute(inputName: String, muted: Boolean) {
        synchronized(controlLock) {
            val revision = ++controlRevision
            muteControls
                .getOrPut(inputName) { OptimisticControl(muted, revision) }
                .observe(muted, revision)
            updateInput(inputName) { it.copy(muted = muted) }
        }
    }

    suspend fun setInputMute(inputName: String, muted: Boolean) {
        val revision =
            synchronized(controlLock) {
                val revision = ++controlRevision
                muteControls[inputName]?.begin(revision, muted)?.let { value ->
                    updateInput(inputName) { it.copy(muted = value) }
                }
                revision
            }
        var succeeded = false
        try {
            client.setInputMute(inputName, muted)
            succeeded = true
        } finally {
            synchronized(controlLock) {
                muteControls[inputName]?.complete(revision, succeeded)?.let { value ->
                    updateInput(inputName) { it.copy(muted = value) }
                }
            }
        }
    }

    suspend fun setLocked(inputName: String, locked: Boolean) {
        settings.setInputLocked(inputName, locked)
    }

    // ── Media inputs (M7) ───────────────────────────────────────────────────

    suspend fun triggerMediaInputAction(inputName: String, action: MediaActionKind) {
        client.triggerMediaInputAction(inputName, action)
        refreshMediaStatus(inputName)
    }

    suspend fun setMediaInputCursor(inputName: String, cursorMs: Long) =
        client.setMediaInputCursor(inputName, cursorMs)

    private suspend fun refreshMediaStatus(inputName: String) {
        requestResult {
            val status = client.getMediaInputStatus(inputName)
            _mediaStatus.update { it + (inputName to status) }
        }
    }

    private suspend fun pollMediaLoop() {
        while (currentCoroutineContext().isActive) {
            val mediaInputs = discovered.value.filter { it.inputKind in MEDIA_INPUT_KINDS }
            val liveNames = mediaInputs.mapTo(mutableSetOf()) { it.name }
            // Prune inputs removed mid-session so stale statuses can't linger.
            _mediaStatus.update { statuses -> statuses.filterKeys { it in liveNames } }
            mediaInputs.forEach { input -> refreshMediaStatus(input.name) }
            delay(MEDIA_POLL_MS)
        }
    }

    // ── Audio extras (M7) ───────────────────────────────────────────────────

    suspend fun getInputAudioBalance(inputName: String) = client.getInputAudioBalance(inputName)

    suspend fun setInputAudioBalance(inputName: String, balance: Double) =
        client.setInputAudioBalance(inputName, balance)

    suspend fun getInputAudioSyncOffset(inputName: String) =
        client.getInputAudioSyncOffset(inputName)

    suspend fun setInputAudioSyncOffset(inputName: String, offsetMs: Int) =
        client.setInputAudioSyncOffset(inputName, offsetMs)

    suspend fun getInputAudioMonitorType(inputName: String) =
        client.getInputAudioMonitorType(inputName)

    suspend fun setInputAudioMonitorType(inputName: String, monitorType: MonitorTypeKind) =
        client.setInputAudioMonitorType(inputName, monitorType)

    private inline fun updateInput(name: String, transform: (DiscoveredInput) -> DiscoveredInput) {
        discovered.update { inputs ->
            inputs.map { if (it.name == name) transform(it) else it }
        }
    }

    private companion object {
        val MEDIA_INPUT_KINDS = setOf("ffmpeg_source", "vlc_source")

        /** Media-status poll cadence; mirrored in MixerRepositoryTest. */
        const val MEDIA_POLL_MS = 2_000L
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
