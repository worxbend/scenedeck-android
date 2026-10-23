package com.scenedeck.android.feature.live

import com.scenedeck.android.core.common.coroutineResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Virtual camera transition requested from the deck and not yet confirmed by OBS. */
enum class VirtualCamPending {
    STARTING,
    STOPPING,
}

/**
 * Tracks a virtual camera start/stop in flight. OBS can take seconds to bring the camera up, so the
 * pending state is set on tap and cleared once [activeUpdates] reports the new state, on failure,
 * or after [timeoutMs]. Taps are ignored while pending so a slow start is not toggled back off.
 * Timed-out toggles are never retried automatically.
 */
internal class VirtualCamToggle(
    private val scope: CoroutineScope,
    private val currentActive: () -> Boolean,
    private val activeUpdates: Flow<Boolean>,
    private val toggle: suspend () -> Unit,
    private val onFailure: () -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val _pending = MutableStateFlow<VirtualCamPending?>(null)
    val pending: StateFlow<VirtualCamPending?> = _pending.asStateFlow()

    fun request() {
        if (_pending.value != null) return
        val wasActive = currentActive()
        _pending.value = if (wasActive) VirtualCamPending.STOPPING else VirtualCamPending.STARTING
        scope.launch {
            try {
                coroutineResult { toggle() }
                    .onSuccess {
                        withTimeoutOrNull(timeoutMs) { activeUpdates.first { it != wasActive } }
                    }
                    .onFailure { onFailure() }
            } finally {
                _pending.value = null
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
