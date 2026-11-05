package com.scenedeck.android.feature.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DiscoveredInput
import com.scenedeck.android.core.data.MeterRefresh
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.WorkspaceChangedException
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.data.selectionBelongsTo
import com.scenedeck.android.core.data.withMeterRefresh
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MixerMode {
    ACTIVE,
    SELECTED,
    PINNED,
}

/** How the mixer strip row surfaces where each channel comes from. */
enum class MixerGrouping {
    /** Flat row; each strip's scope badge includes the source path. */
    SCOPE,

    /** Per-strip header shows the scene path; the scope badge drops it. */
    SCENE_PATH,

    /** Fully flat: no headers and the scope badge shows the scope only. */
    NONE,
}

data class MixerUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val workspace: WorkspaceSession? = null,
    val failed: Boolean = false,
    val mode: MixerMode = MixerMode.ACTIVE,
    val grouping: MixerGrouping = MixerGrouping.SCOPE,
    val search: String = "",
    /** Program scene in OBS right now. */
    val activeScene: String? = null,
    /** Scene whose inputs are displayed (null while pinned/disconnected). */
    val displayedScene: String? = null,
    /** SELECTED mode showing a scene that is not on program. */
    val notInProgram: Boolean = false,
    val inputs: List<MixerInputState> = emptyList(),
    val sceneNames: List<String> = emptyList(),
)

@HiltViewModel
@OptIn(FlowPreview::class)
@Suppress("TooManyFunctions") // one screen's intents; callbacks stay grouped by domain
class MixerViewModel
@Inject
constructor(
    private val mixer: MixerRepository,
    private val settings: SettingsRepository,
    private val client: ObsClient,
) : ViewModel() {

    /** Live meter levels for this screen (read by VolumeMeter in draw phase). */
    val levelsStore = MeterLevelsStore()

    /** Media playback status per media-kind input (polled in the repository). */
    val mediaStatus = mixer.mediaStatus

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val mode = MutableStateFlow(MixerMode.ACTIVE)
    private val grouping = MutableStateFlow(MixerGrouping.SCOPE)

    private data class Selection(
        val workspace: WorkspaceSession? = null,
        val scene: String? = null,
        val inputs: List<DiscoveredInput> = emptyList(),
        val pinned: List<MixerInputState>? = null,
    )

    private data class InputTarget(val workspace: WorkspaceSession, val name: String)

    private val selection = MutableStateFlow(Selection())
    private val search = MutableStateFlow("")
    private var selectionJob: Job? = null
    private var selectionVersion = 0L
    private val selectedRefresh = MutableSharedFlow<WorkspaceSession>(extraBufferCapacity = 1)

    private val batcher =
        FaderWriteBatcher<InputTarget>(viewModelScope, FADER_DEBOUNCE_MS) { target, mul ->
            performAction { mixer.setInputVolume(target.workspace, target.name, mul) }
        }

    val uiState: StateFlow<MixerUiState> =
        combine(
                mixer.mixerState,
                combine(mode, grouping, search) { m, g, q -> Triple(m, g, q) },
                selection,
                settings.settings,
            ) { base, (m, g, q), local, userSettings ->
                val chosen = local.takeIf { it.workspace == base.workspace } ?: Selection()
                val sel = chosen.scene
                val locks = userSettings.lockedInputs
                val rawInputs =
                    when (m) {
                        MixerMode.ACTIVE -> base.inputs
                        MixerMode.SELECTED -> chosen.inputs.map { it.toUi(locks) }
                        MixerMode.PINNED ->
                            (chosen.pinned ?: base.inputs).map { input ->
                                val live = base.inputs.firstOrNull { it.name == input.name }
                                (live ?: input).copy(locked = input.name in locks)
                            }
                    }
                val filtered =
                    if (base.workspace == null) emptyList()
                    else if (q.isBlank()) rawInputs
                    else rawInputs.filter { it.name.contains(q, ignoreCase = true) }
                MixerUiState(
                    connection = base.connection,
                    workspace = base.workspace,
                    failed = base.failed,
                    mode = m,
                    grouping = g,
                    search = q,
                    activeScene = base.activeScene,
                    displayedScene =
                        when (m) {
                            MixerMode.ACTIVE -> base.activeScene
                            MixerMode.SELECTED -> sel
                            MixerMode.PINNED -> null
                        },
                    notInProgram =
                        m == MixerMode.SELECTED && sel != null && sel != base.activeScene,
                    inputs = filtered,
                    sceneNames = base.sceneNames,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MixerUiState())

    private val extrasBatchers = mutableMapOf<String, FaderWriteBatcher<InputTarget>>()

    init {
        viewModelScope.launch {
            combine(mixer.workspaces.state, client.connectionState) { state, _ ->
                    (state as? WorkspaceState.Ready)?.session?.takeIf(mixer::isCurrent)
                }
                .distinctUntilChanged()
                .collectLatest { origin ->
                    resetWorkspace(origin)
                    if (origin != null) restoreSelection(origin)
                }
        }
        viewModelScope.launch {
            combine(
                    settings.settings.map { it.meterRefresh }.distinctUntilChanged(),
                    mixer.workspaces.state,
                    client.connectionState,
                ) { refresh, workspace, _ ->
                    refresh to
                        (workspace as? WorkspaceState.Ready)?.session?.takeIf(mixer::isCurrent)
                }
                .distinctUntilChanged()
                .collectLatest { (refresh, origin) ->
                    levelsStore.clear()
                    if (origin != null && refresh != MeterRefresh.OFF) {
                        client.metersWithContext.withMeterRefresh(refresh).collect { batch ->
                            if (batch.token == origin.token && mixer.isCurrent(origin))
                                levelsStore.update(batch.readings)
                        }
                    }
                }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect { stamped ->
                val origin = selection.value.workspace ?: return@collect
                if (!mixer.isCurrent(origin) || stamped.token != origin.token) return@collect
                when (val event = stamped.event) {
                    is ObsEvent.InputMuteStateChanged -> {
                        patchSelected(origin, event.inputName) { it.copy(muted = event.muted) }
                        patchPinned(origin, event.inputName) { it.copy(muted = event.muted) }
                    }

                    is ObsEvent.InputVolumeChanged -> {
                        patchSelected(origin, event.inputName) {
                            it.copy(volumeMul = event.volumeMul)
                        }
                        patchPinned(origin, event.inputName) {
                            it.copy(volumeMul = event.volumeMul)
                        }
                    }

                    // Frozen-scene refresh: lifecycle events re-run discovery for the
                    // SELECTED scene (its members can change while it is off-program).
                    is ObsEvent.SceneItemEnableStateChanged,
                    is ObsEvent.SceneCreated,
                    is ObsEvent.SceneRemoved,
                    is ObsEvent.SceneListChanged,
                    is ObsEvent.InputCreated,
                    is ObsEvent.InputRemoved,
                    is ObsEvent.InputNameChanged -> selectedRefresh.tryEmit(origin)

                    // ObsStateRepository migrates the persisted selection; follow it in memory.
                    is ObsEvent.SceneNameChanged -> {
                        selection.update { current ->
                            if (current.workspace == origin && current.scene == event.oldSceneName)
                                current.copy(scene = event.sceneName)
                            else current
                        }
                        selectedRefresh.tryEmit(origin)
                    }

                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            selectedRefresh.debounce(REFRESH_DEBOUNCE_MS).collectLatest { origin ->
                val scene =
                    selection.value.takeIf { it.workspace == origin }?.scene ?: return@collectLatest
                performAction { discoverSelectedScene(origin, scene, selectionVersion) }
            }
        }
        viewModelScope.launch {
            val persisted = settings.settings.first()
            mode.value =
                coroutineResult { MixerMode.valueOf(persisted.mixerMode) }
                    .getOrDefault(MixerMode.ACTIVE)
            grouping.value =
                coroutineResult { MixerGrouping.valueOf(persisted.mixerGrouping) }
                    .getOrDefault(MixerGrouping.SCOPE)
        }
    }

    fun retryWorkspace() = mixer.retry()

    private fun resetWorkspace(origin: WorkspaceSession?) {
        selectionJob?.cancel()
        selectionVersion++
        selection.value = Selection(workspace = origin)
        batcher.cancelAll()
        extrasBatchers.values.forEach { it.cancelAll() }
        levelsStore.clear()
    }

    private suspend fun restoreSelection(origin: WorkspaceSession) {
        performAction {
            val persisted = settings.settings.first()
            if (mixer.isCurrent(origin) && persisted.selectionBelongsTo(origin.key)) {
                selectScene(origin, persisted.mixerSelectedScene, persist = false)
            }
        }
    }

    fun setMode(origin: WorkspaceSession, newMode: MixerMode) {
        if (!mixer.isCurrent(origin)) return
        if (newMode == MixerMode.PINNED && mode.value != MixerMode.PINNED) {
            selection.update { it.copy(workspace = origin, pinned = uiState.value.inputs) }
        }
        if (newMode == MixerMode.SELECTED && selection.value.scene == null) {
            selectScene(origin, uiState.value.activeScene ?: uiState.value.sceneNames.firstOrNull())
        }
        mode.value = newMode
        launchAction { settings.setMixerMode(newMode.name) }
    }

    fun selectScene(origin: WorkspaceSession, sceneName: String?, persist: Boolean = true) {
        if (!mixer.isCurrent(origin)) return
        selectionJob?.cancel()
        selectionVersion++
        val version = selectionVersion
        selection.update { it.copy(workspace = origin, scene = sceneName, inputs = emptyList()) }
        selectionJob = viewModelScope.launch {
            performAction {
                if (persist) persistSelection(origin, sceneName)
                discoverSelectedScene(origin, selection.value.scene, version)
            }
        }
    }

    /** The scope and choice are saved atomically; a concurrent rename is reconciled afterward. */
    private suspend fun persistSelection(origin: WorkspaceSession, sceneName: String?) {
        settings.update {
            if (!mixer.isCurrent(origin)) throw WorkspaceChangedException()
            it.copy(
                mixerSelectedScene = sceneName,
                mixerSelectedProfileId = origin.key.profileId,
                mixerSelectedCollection = origin.key.collectionName,
            )
        }
        settings.update { current ->
            val selected = selection.value
            if (
                selectionIsCurrent(origin) &&
                    current.selectionBelongsTo(origin.key) &&
                    current.mixerSelectedScene == sceneName
            ) {
                current.copy(mixerSelectedScene = selected.scene)
            } else current
        }
    }

    private suspend fun discoverSelectedScene(
        origin: WorkspaceSession,
        sceneName: String?,
        version: Long,
    ) {
        val inputs = mixer.discoverScene(origin, sceneName)
        currentCoroutineContext().ensureActive()
        selection.update { current ->
            if (
                selectionIsCurrent(origin) &&
                    selectionVersion == version &&
                    current.scene == sceneName
            )
                current.copy(inputs = inputs)
            else current
        }
    }

    private fun selectionIsCurrent(origin: WorkspaceSession) =
        mixer.isCurrent(origin) && selection.value.workspace == origin

    fun setGrouping(newGrouping: MixerGrouping) {
        grouping.value = newGrouping
        launchAction { settings.setMixerGrouping(newGrouping.name) }
    }

    fun setSearch(query: String) {
        search.value = query
    }

    fun onVolumePreview(origin: WorkspaceSession, inputName: String, volumeMul: Double) {
        batcher.preview(InputTarget(origin, inputName), volumeMul)
    }

    fun onVolumeCommit(origin: WorkspaceSession, inputName: String, volumeMul: Double) {
        batcher.commit(InputTarget(origin, inputName), volumeMul)
    }

    fun toggleMute(origin: WorkspaceSession, inputName: String, muted: Boolean) {
        launchAction { mixer.setInputMute(origin, inputName, muted) }
    }

    fun toggleLock(origin: WorkspaceSession, inputName: String, locked: Boolean) {
        launchAction { mixer.setLocked(origin, inputName, locked) }
    }

    // ── Media controls (M7) ─────────────────────────────────────────────────

    fun mediaPlayPause(origin: WorkspaceSession, inputName: String) {
        val state =
            mediaStatus.value.takeIf { it.workspace == origin }?.inputs?.get(inputName)?.state
        launchAction {
            mixer.triggerMediaInputAction(
                origin,
                inputName,
                if (state == MediaStateKind.PLAYING) MediaActionKind.PAUSE
                else MediaActionKind.PLAY,
            )
        }
    }

    fun mediaRestart(origin: WorkspaceSession, inputName: String) {
        launchAction { mixer.triggerMediaInputAction(origin, inputName, MediaActionKind.RESTART) }
    }

    // ── Audio extras (M7) ───────────────────────────────────────────────────

    suspend fun loadAudioExtras(origin: WorkspaceSession, inputName: String): AudioExtras =
        AudioExtras(
            balance =
                coroutineResult { mixer.getInputAudioBalance(origin, inputName) }.getOrDefault(0.5),
            syncOffsetMs =
                coroutineResult { mixer.getInputAudioSyncOffset(origin, inputName) }
                    .getOrDefault(0),
            monitorType =
                coroutineResult { mixer.getInputAudioMonitorType(origin, inputName) }
                    .getOrDefault(MonitorTypeKind.NONE),
        )

    private fun extrasBatcher(key: String, send: suspend (InputTarget, Double) -> Unit) =
        extrasBatchers.getOrPut(key) {
            FaderWriteBatcher(viewModelScope, FADER_DEBOUNCE_MS) { name, value ->
                performAction { send(name, value) }
            }
        }

    fun setAudioBalance(origin: WorkspaceSession, inputName: String, balance: Double) {
        extrasBatcher("balance") { name, value ->
                mixer.setInputAudioBalance(name.workspace, name.name, value)
            }
            .preview(InputTarget(origin, inputName), balance)
    }

    fun setAudioSyncOffset(origin: WorkspaceSession, inputName: String, offsetMs: Int) {
        extrasBatcher("syncOffset") { name, value ->
                mixer.setInputAudioSyncOffset(name.workspace, name.name, value.toInt())
            }
            .preview(InputTarget(origin, inputName), offsetMs.toDouble())
    }

    fun setAudioMonitorType(
        origin: WorkspaceSession,
        inputName: String,
        monitorType: MonitorTypeKind,
    ) {
        launchAction { mixer.setInputAudioMonitorType(origin, inputName, monitorType) }
    }

    private inline fun patchPinned(
        origin: WorkspaceSession,
        name: String,
        transform: (MixerInputState) -> MixerInputState,
    ) {
        selection.update { current ->
            if (current.workspace == origin)
                current.copy(
                    pinned = current.pinned?.map { if (it.name == name) transform(it) else it }
                )
            else current
        }
    }

    private inline fun patchSelected(
        origin: WorkspaceSession,
        name: String,
        transform: (DiscoveredInput) -> DiscoveredInput,
    ) {
        selection.update { current ->
            if (current.workspace == origin)
                current.copy(
                    inputs = current.inputs.map { if (it.name == name) transform(it) else it }
                )
            else current
        }
    }

    private fun DiscoveredInput.toUi(locks: Set<String>) =
        MixerInputState(
            name = name,
            scope = scope,
            scopePath = scopePath,
            volumeMul = volumeMul,
            muted = muted,
            locked = name in locks,
            inputKind = inputKind,
        )

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch { performAction(action) }
    }

    private suspend fun performAction(action: suspend () -> Unit) {
        coroutineResult { action() }
            .onFailure {
                // OBS and storage exceptions can contain endpoint or credential details.
                _errors.emit(
                    "Couldn't update this channel. Check the OBS connection and try again."
                )
            }
    }

    private companion object {
        const val FADER_DEBOUNCE_MS = 120L
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
