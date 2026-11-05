package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val workspace: WorkspaceSession? = null,
    val sceneNames: List<String> = emptyList(),
    val failed: Boolean = false,
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
    val workspaces: WorkspaceContext,
    @ApplicationScope private val scope: CoroutineScope,
    clock: MonotonicClock,
) {
    private val discovery = AudioDiscovery(client)
    private val discoveryMutex = Mutex()
    private val controlLock = Any()
    private var controlRevision = 0L
    private val volumeControls = mutableMapOf<String, OptimisticControl<Double>>()
    private val muteControls = mutableMapOf<String, OptimisticControl<Boolean>>()
    private val discoveryRequests = MutableStateFlow(0L)

    private data class Snapshot(
        val workspace: WorkspaceSession? = null,
        val activeScene: String? = null,
        val inputs: List<DiscoveredInput> = emptyList(),
        val sceneNames: List<String> = emptyList(),
        val loaded: Boolean = false,
        val failed: Boolean = false,
    )

    private val snapshot = MutableStateFlow(Snapshot())

    val mixerState: StateFlow<MixerState> =
        combine(client.connectionState, workspaces.state, snapshot, settings.settings) {
                connection,
                workspace,
                discovery,
                userSettings ->
                val current = (workspace as? WorkspaceState.Ready)?.session
                val valid = current != null && isCurrent(current) && discovery.workspace == current
                val loaded = valid && discovery.loaded
                MixerState(
                    connection = connection,
                    workspace = current.takeIf { loaded },
                    activeScene = discovery.activeScene.takeIf { loaded },
                    sceneNames = if (loaded) discovery.sceneNames else emptyList(),
                    failed = workspace == WorkspaceState.Failed || (valid && discovery.failed),
                    inputs =
                        if (loaded)
                            discovery.inputs.map { input ->
                                MixerInputState(
                                    name = input.name,
                                    scope = input.scope,
                                    scopePath = input.scopePath,
                                    volumeMul = input.volumeMul,
                                    muted = input.muted,
                                    locked = input.name in userSettings.lockedInputs,
                                    inputKind = input.inputKind,
                                )
                            }
                        else emptyList(),
                )
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), MixerState())

    private val media =
        MixerMediaController(
            client,
            workspaces,
            clock,
            scope,
            snapshot.map { current ->
                MediaSources(
                    current.workspace,
                    current.inputs
                        .filter { it.inputKind in MEDIA_INPUT_KINDS }
                        .mapTo(mutableSetOf()) { it.name },
                )
            },
        )

    /** Tagged playback status, polled only while this workspace has an active collector. */
    val mediaStatus: StateFlow<MixerMediaStatus> = media.state

    fun isCurrent(origin: WorkspaceSession): Boolean {
        val ready = client.connectionState.value as? ConnectionState.Ready
        return workspaces.isCurrent(origin) &&
            ready?.token == origin.token &&
            !ready.collectionChanging
    }

    fun retry() {
        workspaces.retry()
        requestDiscovery()
    }

    private fun requireCurrent(origin: WorkspaceSession) {
        if (!isCurrent(origin)) throw WorkspaceChangedException()
    }

    private suspend fun <T> inWorkspace(
        origin: WorkspaceSession,
        action: suspend ObsClient.() -> T,
    ): T {
        requireCurrent(origin)
        val result = client.withSession(origin.token, action)
        requireCurrent(origin)
        return result
    }

    init {
        scope.launch {
            combine(workspaces.state, client.connectionState) { workspace, _ ->
                    (workspace as? WorkspaceState.Ready)?.session?.takeIf(::isCurrent)
                }
                .distinctUntilChanged()
                .collect { origin ->
                    synchronized(controlLock) {
                        snapshot.value = Snapshot(workspace = origin)
                        volumeControls.clear()
                        muteControls.clear()
                        requestDiscovery()
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
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect { stamped ->
                val origin = workspaces.currentSession() ?: return@collect
                if (stamped.token != origin.token || !isCurrent(origin)) return@collect
                when (val event = stamped.event) {
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
                        observeInputMute(origin, event.inputName, event.muted)

                    is ObsEvent.InputVolumeChanged ->
                        previewInputVolume(origin, event.inputName, event.volumeMul)

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
    }

    private fun requestDiscovery() =
        synchronized(controlLock) {
            discoveryRequests.value++
        }

    suspend fun refreshDiscovery() = refreshDiscovery(discoveryRequests.value)

    private suspend fun refreshDiscovery(generation: Long) {
        val origin = workspaces.currentSession() ?: return
        discoveryMutex.withLock {
            if (!isCurrent(origin)) return@withLock
            val startedAt = synchronized(controlLock) { controlRevision }
            val result = requestResult {
                inWorkspace(origin) {
                    val list = getSceneList()
                    list to discoverScene(origin, list.currentProgramScene)
                }
            }
            currentCoroutineContext().ensureActive()
            synchronized(controlLock) {
                if (!isCurrent(origin) || discoveryRequests.value != generation) return@synchronized
                val discovered = result.getOrNull()
                snapshot.value =
                    if (discovered == null) {
                        Snapshot(workspace = origin, failed = true)
                    } else {
                        val (list, inputs) = discovered
                        Snapshot(
                            origin,
                            list.currentProgramScene,
                            reconcileControls(inputs, startedAt),
                            list.scenes.map { it.name },
                            loaded = true,
                        )
                    }
            }
        }
    }

    /** Called under controlLock; new events and pending controls take precedence over a read. */
    private fun reconcileControls(
        inputs: List<DiscoveredInput>,
        startedAt: Long,
    ): List<DiscoveredInput> {
        val names = inputs.mapTo(mutableSetOf()) { it.name }
        volumeControls.keys.retainAll(names)
        muteControls.keys.retainAll(names)
        return inputs.map { input ->
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

    /** Runs discovery for a scene in the caller's displayed workspace. */
    suspend fun discoverScene(origin: WorkspaceSession, sceneName: String?): List<DiscoveredInput> =
        inWorkspace(origin) {
            discovery.discover(sceneName, settings.settings.first().audioAllowList)
        }

    suspend fun setInputVolume(origin: WorkspaceSession, inputName: String, volumeMul: Double) {
        val revision =
            synchronized(controlLock) {
                requireCurrent(origin)
                val revision = ++controlRevision
                volumeControls[inputName]?.begin(revision, volumeMul)?.let { value ->
                    updateInput(inputName) { it.copy(volumeMul = value) }
                }
                revision
            }
        var succeeded = false
        try {
            inWorkspace(origin) { setInputVolume(inputName, volumeMul) }
            succeeded = true
        } finally {
            synchronized(controlLock) {
                if (!isCurrent(origin)) return@synchronized
                volumeControls[inputName]?.complete(revision, succeeded)?.let { value ->
                    updateInput(inputName) { it.copy(volumeMul = value) }
                }
            }
        }
    }

    /** Local preview or OBS event supersedes any older optimistic write. */
    fun previewInputVolume(origin: WorkspaceSession, inputName: String, volumeMul: Double) {
        synchronized(controlLock) {
            if (!isCurrent(origin)) return@synchronized
            val revision = ++controlRevision
            volumeControls
                .getOrPut(inputName) { OptimisticControl(volumeMul, revision) }
                .observe(volumeMul, revision)
            updateInput(inputName) { it.copy(volumeMul = volumeMul) }
        }
    }

    private fun observeInputMute(origin: WorkspaceSession, inputName: String, muted: Boolean) {
        synchronized(controlLock) {
            if (!isCurrent(origin)) return@synchronized
            val revision = ++controlRevision
            muteControls
                .getOrPut(inputName) { OptimisticControl(muted, revision) }
                .observe(muted, revision)
            updateInput(inputName) { it.copy(muted = muted) }
        }
    }

    suspend fun setInputMute(origin: WorkspaceSession, inputName: String, muted: Boolean) {
        val revision =
            synchronized(controlLock) {
                requireCurrent(origin)
                val revision = ++controlRevision
                muteControls[inputName]?.begin(revision, muted)?.let { value ->
                    updateInput(inputName) { it.copy(muted = value) }
                }
                revision
            }
        var succeeded = false
        try {
            inWorkspace(origin) { setInputMute(inputName, muted) }
            succeeded = true
        } finally {
            synchronized(controlLock) {
                if (!isCurrent(origin)) return@synchronized
                muteControls[inputName]?.complete(revision, succeeded)?.let { value ->
                    updateInput(inputName) { it.copy(muted = value) }
                }
            }
        }
    }

    suspend fun setLocked(origin: WorkspaceSession, inputName: String, locked: Boolean) {
        settings.update { current ->
            requireCurrent(origin)
            current.copy(
                lockedInputs =
                    if (locked) current.lockedInputs + inputName
                    else current.lockedInputs - inputName
            )
        }
    }

    suspend fun triggerMediaInputAction(
        origin: WorkspaceSession,
        inputName: String,
        action: MediaActionKind,
    ) {
        requireCurrent(origin)
        if (media.isSeeking(origin, inputName)) return
        inWorkspace(origin) { triggerMediaInputAction(inputName, action) }
        media.refresh(origin, inputName)
    }

    fun watchMediaInput(origin: WorkspaceSession, inputName: String): AutoCloseable =
        media.watch(origin, inputName)

    suspend fun seekMedia(intent: MediaSeekIntent): Boolean =
        media.seek(intent.target, intent.cursorMs, intent.durationMs)

    suspend fun getInputAudioBalance(origin: WorkspaceSession, inputName: String) =
        inWorkspace(origin) { getInputAudioBalance(inputName) }

    suspend fun setInputAudioBalance(origin: WorkspaceSession, inputName: String, balance: Double) =
        inWorkspace(origin) { setInputAudioBalance(inputName, balance) }

    suspend fun getInputAudioSyncOffset(origin: WorkspaceSession, inputName: String) =
        inWorkspace(origin) { getInputAudioSyncOffset(inputName) }

    suspend fun setInputAudioSyncOffset(
        origin: WorkspaceSession,
        inputName: String,
        offsetMs: Int,
    ) = inWorkspace(origin) { setInputAudioSyncOffset(inputName, offsetMs) }

    suspend fun getInputAudioMonitorType(origin: WorkspaceSession, inputName: String) =
        inWorkspace(origin) { getInputAudioMonitorType(inputName) }

    suspend fun setInputAudioMonitorType(
        origin: WorkspaceSession,
        inputName: String,
        monitorType: MonitorTypeKind,
    ) = inWorkspace(origin) { setInputAudioMonitorType(inputName, monitorType) }

    private inline fun updateInput(name: String, transform: (DiscoveredInput) -> DiscoveredInput) {
        snapshot.update { current ->
            current.copy(inputs = current.inputs.map { if (it.name == name) transform(it) else it })
        }
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
