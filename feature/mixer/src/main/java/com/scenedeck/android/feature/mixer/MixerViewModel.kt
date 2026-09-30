package com.scenedeck.android.feature.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DiscoveredInput
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

/**
 * Coalesces fader writes to OBS: trailing debounce per input, so a fader drag emits at most one
 * request per window; the final value is sent immediately on drag end.
 */
internal class FaderWriteBatcher(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val delayMs: Long,
    private val send: suspend (String, Double) -> Unit,
) {
    private val jobs = mutableMapOf<String, Job>()

    fun preview(inputName: String, volumeMul: Double) {
        jobs[inputName]?.cancel()
        jobs[inputName] = scope.launch {
            delay(delayMs)
            send(inputName, volumeMul)
        }
    }

    fun commit(inputName: String, volumeMul: Double) {
        jobs[inputName]?.cancel()
        jobs.remove(inputName)
        scope.launch { send(inputName, volumeMul) }
    }
}

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

    private val mode = MutableStateFlow(MixerMode.ACTIVE)
    private val grouping = MutableStateFlow(MixerGrouping.SCOPE)
    private val selectedScene = MutableStateFlow<String?>(null)
    private val selectedInputs = MutableStateFlow<List<DiscoveredInput>>(emptyList())
    private val pinnedInputs = MutableStateFlow<List<MixerInputState>?>(null)
    private val search = MutableStateFlow("")
    private var selectionJob: Job? = null
    private var selectionVersion = 0L
    private val selectedRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val batcher =
        FaderWriteBatcher(viewModelScope, FADER_DEBOUNCE_MS) { name, mul ->
            mixer.setInputVolume(name, mul)
        }

    val uiState: StateFlow<MixerUiState> =
        combine(
                mixer.mixerState,
                combine(mode, grouping, search) { m, g, q -> Triple(m, g, q) },
                combine(selectedScene, selectedInputs, pinnedInputs) { s, si, p ->
                    Triple(s, si, p)
                },
                settings.settings,
                mixer.sceneNames,
            ) { base, (m, g, q), (sel, selInputs, pinned), userSettings, sceneNames ->
                val locks = userSettings.lockedInputs
                val rawInputs =
                    when (m) {
                        MixerMode.ACTIVE -> base.inputs
                        MixerMode.SELECTED -> selInputs.map { it.toUi(locks) }
                        MixerMode.PINNED ->
                            (pinned ?: base.inputs).map { input ->
                                val live = base.inputs.firstOrNull { it.name == input.name }
                                (live ?: input).copy(locked = input.name in locks)
                            }
                    }
                val filtered =
                    if (q.isBlank()) rawInputs
                    else rawInputs.filter { it.name.contains(q, ignoreCase = true) }
                MixerUiState(
                    connection = base.connection,
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
                    sceneNames = sceneNames,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MixerUiState())

    init {
        viewModelScope.launch { client.volumeMeters.collect { levelsStore.update(it) } }
        viewModelScope.launch {
            // Mute/volume events also patch the SELECTED-mode local list.
            client.events.collect { event ->
                when (event) {
                    is ObsEvent.InputMuteStateChanged -> {
                        patchSelected(event.inputName) { it.copy(muted = event.muted) }
                        patchPinned(event.inputName) { it.copy(muted = event.muted) }
                    }

                    is ObsEvent.InputVolumeChanged -> {
                        patchSelected(event.inputName) { it.copy(volumeMul = event.volumeMul) }
                        patchPinned(event.inputName) { it.copy(volumeMul = event.volumeMul) }
                    }

                    // Frozen-scene refresh: lifecycle events re-run discovery for the
                    // SELECTED scene (its members can change while it is off-program).
                    is ObsEvent.SceneItemEnableStateChanged,
                    is ObsEvent.SceneCreated,
                    is ObsEvent.SceneRemoved,
                    is ObsEvent.SceneListChanged,
                    is ObsEvent.InputCreated,
                    is ObsEvent.InputRemoved,
                    is ObsEvent.InputNameChanged -> selectedRefresh.tryEmit(Unit)

                    is ObsEvent.SceneNameChanged -> {
                        if (selectedScene.value == event.oldSceneName) {
                            selectedScene.value = event.sceneName
                            viewModelScope.launch {
                                settings.setMixerSelectedScene(event.sceneName)
                            }
                        }
                        selectedRefresh.tryEmit(Unit)
                    }

                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            selectedRefresh.debounce(REFRESH_DEBOUNCE_MS).collectLatest {
                val scene = selectedScene.value ?: return@collectLatest
                discoverSelectedScene(scene, selectionVersion)
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
            persisted.mixerSelectedScene?.let { selectScene(it, persist = false) }
        }
    }

    fun setMode(newMode: MixerMode) {
        if (newMode == MixerMode.PINNED && mode.value != MixerMode.PINNED) {
            pinnedInputs.value = uiState.value.inputs
        }
        if (newMode == MixerMode.SELECTED && selectedScene.value == null) {
            selectScene(uiState.value.activeScene ?: uiState.value.sceneNames.firstOrNull())
        }
        mode.value = newMode
        viewModelScope.launch { settings.setMixerMode(newMode.name) }
    }

    fun selectScene(sceneName: String?, persist: Boolean = true) {
        selectionJob?.cancel()
        selectionVersion++
        val version = selectionVersion
        selectedScene.value = sceneName
        selectedInputs.value = emptyList()
        selectionJob = viewModelScope.launch {
            if (persist) settings.setMixerSelectedScene(sceneName)
            discoverSelectedScene(sceneName, version)
        }
    }

    private suspend fun discoverSelectedScene(sceneName: String?, version: Long) {
        val inputs = mixer.discoverScene(sceneName)
        currentCoroutineContext().ensureActive()
        if (selectionVersion == version && selectedScene.value == sceneName) {
            selectedInputs.value = inputs
        }
    }

    fun setGrouping(newGrouping: MixerGrouping) {
        grouping.value = newGrouping
        viewModelScope.launch { settings.setMixerGrouping(newGrouping.name) }
    }

    fun setSearch(query: String) {
        search.value = query
    }

    fun onVolumePreview(inputName: String, volumeMul: Double) {
        batcher.preview(inputName, volumeMul)
    }

    fun onVolumeCommit(inputName: String, volumeMul: Double) {
        batcher.commit(inputName, volumeMul)
    }

    fun toggleMute(inputName: String, muted: Boolean) {
        viewModelScope.launch { mixer.setInputMute(inputName, muted) }
    }

    fun toggleLock(inputName: String, locked: Boolean) {
        viewModelScope.launch { mixer.setLocked(inputName, locked) }
    }

    // ── Media controls (M7) ─────────────────────────────────────────────────

    fun mediaPlayPause(inputName: String) {
        val state = mediaStatus.value[inputName]?.state
        viewModelScope.launch {
            mixer.triggerMediaInputAction(
                inputName,
                if (state == MediaStateKind.PLAYING) MediaActionKind.PAUSE
                else MediaActionKind.PLAY,
            )
        }
    }

    fun mediaRestart(inputName: String) {
        viewModelScope.launch { mixer.triggerMediaInputAction(inputName, MediaActionKind.RESTART) }
    }

    // ── Audio extras (M7) ───────────────────────────────────────────────────

    suspend fun loadAudioExtras(inputName: String): AudioExtras =
        AudioExtras(
            balance = coroutineResult { mixer.getInputAudioBalance(inputName) }.getOrDefault(0.5),
            syncOffsetMs =
                coroutineResult { mixer.getInputAudioSyncOffset(inputName) }.getOrDefault(0),
            monitorType =
                coroutineResult { mixer.getInputAudioMonitorType(inputName) }
                    .getOrDefault(MonitorTypeKind.NONE),
        )

    private val extrasBatchers = mutableMapOf<String, FaderWriteBatcher>()

    private fun extrasBatcher(key: String, send: suspend (String, Double) -> Unit) =
        extrasBatchers.getOrPut(key) { FaderWriteBatcher(viewModelScope, FADER_DEBOUNCE_MS, send) }

    fun setAudioBalance(inputName: String, balance: Double) {
        extrasBatcher("balance") { name, value -> mixer.setInputAudioBalance(name, value) }
            .preview(inputName, balance)
    }

    fun setAudioSyncOffset(inputName: String, offsetMs: Int) {
        extrasBatcher("syncOffset") { name, value ->
                mixer.setInputAudioSyncOffset(name, value.toInt())
            }
            .preview(inputName, offsetMs.toDouble())
    }

    fun setAudioMonitorType(inputName: String, monitorType: MonitorTypeKind) {
        viewModelScope.launch { mixer.setInputAudioMonitorType(inputName, monitorType) }
    }

    private inline fun patchPinned(name: String, transform: (MixerInputState) -> MixerInputState) {
        pinnedInputs.value = pinnedInputs.value?.map { if (it.name == name) transform(it) else it }
    }

    private inline fun patchSelected(
        name: String,
        transform: (DiscoveredInput) -> DiscoveredInput,
    ) {
        selectedInputs.value =
            selectedInputs.value.map {
                if (it.name == name) transform(it) else it
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

    private companion object {
        const val FADER_DEBOUNCE_MS = 120L
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
