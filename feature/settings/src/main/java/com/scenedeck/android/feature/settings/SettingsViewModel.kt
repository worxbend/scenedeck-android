package com.scenedeck.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) :
    ViewModel() {

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

    private fun launchChange(action: suspend () -> Unit) {
        viewModelScope.launch {
            coroutineResult { action() }
                .onFailure {
                    _errors.emit("Couldn't save this setting. Please try again.")
                }
        }
    }
}
