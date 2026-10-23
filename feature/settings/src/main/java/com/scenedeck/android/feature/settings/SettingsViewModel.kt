package com.scenedeck.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.AudioInputCatalog
import com.scenedeck.android.core.data.MeterRefresh
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.withAudioInputAllowed
import com.scenedeck.android.core.designsystem.theme.UiDensity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Layout and Mixer preferences edited on the Settings page (FEATURE_SPEC §9). */
data class LayoutAndAudioSettings(
    val density: UiDensity = UiDensity.COMFORTABLE,
    val meterRefresh: MeterRefresh = MeterRefresh.SMOOTH,
    /** Mixer allow-list; empty shows every discovered input. */
    val audioAllowList: Set<String> = emptySet(),
)

/** Inputs offered by the allow-list editor. */
data class AudioInputChoices(
    val loading: Boolean = false,
    /** Inputs found in OBS plus already allowed names, sorted. */
    val names: List<String> = emptyList(),
)

@HiltViewModel
@Suppress("TooManyFunctions") // one intent per setting
class SettingsViewModel
@Inject
constructor(
    private val settings: SettingsRepository,
    private val audioInputs: AudioInputCatalog,
) : ViewModel() {

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val outputSafety: StateFlow<OutputSafety> =
        settings.settings
            .map {
                OutputSafety(
                    confirmStartStream = it.confirmStartStream,
                    confirmStopStream = it.confirmStopStream,
                    confirmStartRecord = it.confirmStartRecord,
                    confirmStopRecord = it.confirmStopRecord,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutputSafety())

    val layoutAndAudio: StateFlow<LayoutAndAudioSettings> =
        settings.settings
            .map {
                LayoutAndAudioSettings(
                    density = UiDensity.fromName(it.uiDensity),
                    meterRefresh = it.meterRefresh,
                    audioAllowList = it.audioAllowList,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                LayoutAndAudioSettings(),
            )

    private val _audioInputChoices = MutableStateFlow(AudioInputChoices())
    val audioInputChoices: StateFlow<AudioInputChoices> = _audioInputChoices.asStateFlow()
    private var choicesJob: Job? = null

    fun setConfirmStartStream(value: Boolean) {
        launchChange { settings.setConfirmStartStream(value) }
    }

    fun setConfirmStopStream(value: Boolean) {
        launchChange { settings.setConfirmStopStream(value) }
    }

    fun setConfirmStartRecord(value: Boolean) {
        launchChange { settings.setConfirmStartRecord(value) }
    }

    fun setConfirmStopRecord(value: Boolean) {
        launchChange { settings.setConfirmStopRecord(value) }
    }

    fun setDensity(value: UiDensity) {
        launchChange { settings.setUiDensity(value.name) }
    }

    fun setMeterRefresh(value: MeterRefresh) {
        launchChange { settings.setMeterRefresh(value) }
    }

    /** Adds/removes one input atomically, so quick toggles never drop each other. */
    fun setAudioInputAllowed(inputName: String, allowed: Boolean) {
        launchChange {
            settings.update {
                it.copy(
                    audioAllowList = it.audioAllowList.withAudioInputAllowed(inputName, allowed)
                )
            }
        }
    }

    /** Clears the allow-list: the Mixer shows every discovered input again. */
    fun clearAudioAllowList() {
        launchChange { settings.update { it.copy(audioAllowList = emptySet()) } }
    }

    /** Loads the allow-list editor's choices from OBS; the latest request wins. */
    fun loadAudioInputChoices() {
        choicesJob?.cancel()
        _audioInputChoices.value = _audioInputChoices.value.copy(loading = true)
        choicesJob = viewModelScope.launch {
            val found = coroutineResult { audioInputs.audioInputNames() }.getOrDefault(emptyList())
            val allowed = coroutineResult {
                settings.settings.first().audioAllowList
            }
                .getOrDefault(emptySet())
            _audioInputChoices.value =
                AudioInputChoices(
                    loading = false,
                    names = (found + allowed).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
                )
        }
    }

    private fun launchChange(action: suspend () -> Unit) {
        viewModelScope.launch {
            coroutineResult { action() }
                .onFailure {
                    _errors.emit("Couldn't save this setting. Please try again.")
                }
        }
    }
}
