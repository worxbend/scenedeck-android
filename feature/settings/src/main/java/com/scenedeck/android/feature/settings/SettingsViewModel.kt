package com.scenedeck.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) :
    ViewModel() {

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
        viewModelScope.launch { settings.setConfirmStartStream(value) }
    }

    fun setConfirmStopStream(value: Boolean) {
        viewModelScope.launch { settings.setConfirmStopStream(value) }
    }

    fun setConfirmStartRecord(value: Boolean) {
        viewModelScope.launch { settings.setConfirmStartRecord(value) }
    }

    fun setConfirmStopRecord(value: Boolean) {
        viewModelScope.launch { settings.setConfirmStopRecord(value) }
    }
}
