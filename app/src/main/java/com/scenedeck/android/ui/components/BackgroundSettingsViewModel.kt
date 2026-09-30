package com.scenedeck.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.background.KeepAliveController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-level background behavior (keep-alive foreground service). */
@HiltViewModel
class BackgroundSettingsViewModel
@Inject
constructor(private val keepAliveController: KeepAliveController) : ViewModel() {

    val keepAlive: StateFlow<Boolean> =
        keepAliveController.keepAliveEnabled.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            false,
        )

    fun setKeepAlive(enabled: Boolean) {
        viewModelScope.launch { keepAliveController.setEnabled(enabled) }
    }
}
