package com.scenedeck.android.feature.alerts

import androidx.lifecycle.ViewModel
import com.scenedeck.android.core.data.LiveAlert
import com.scenedeck.android.core.data.LiveAlertsRepository
import com.scenedeck.android.core.data.SpeechTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(private val alerts: LiveAlertsRepository) : ViewModel() {
    val uiState = alerts.state

    fun armSpeech(target: SpeechTarget): Boolean = alerts.armSpeech(target)

    fun stopSpeech() = alerts.stopSpeech()

    fun dismiss(alert: LiveAlert) = alerts.dismiss(alert)

    fun setHaptics(enabled: Boolean) = alerts.setHaptics(enabled)
}

enum class AlertDestination {
    STATS,
    MIXER,
    CONNECTIONS,
    PREFLIGHT,
}
