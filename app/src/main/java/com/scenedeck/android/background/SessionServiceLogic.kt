package com.scenedeck.android.background

import com.scenedeck.android.core.model.ConnectionState

/**
 * Pure start/stop decision logic for [ObsSessionService] (unit-tested; the service itself is thin
 * glue over this + Hilt singletons).
 */
class SessionServiceLogic {

    /** What the service should do next. */
    enum class Effect {
        GO_FOREGROUND,
        SHUTDOWN,
        NONE,
    }

    /** True once the session has reached Ready at least once while running. */
    var wasReady = false
        private set

    /**
     * Intent routing: null action = START_STICKY restart after process death (go foreground +
     * reconnect), [ObsSessionService.ACTION_DISCONNECT] stops.
     */
    fun onStartAction(action: String?): Effect =
        when (action) {
            ObsSessionService.ACTION_DISCONNECT -> Effect.SHUTDOWN
            else -> Effect.GO_FOREGROUND
        }

    /**
     * A Disconnected state is only terminal once we have been Ready — before that it is the normal
     * pre-connect state and must NOT kill the service. Failed is kept alive so the notification can
     * surface it (ObsClient does not retry terminal failures); Reconnecting is handled by the
     * client's backoff loop.
     */
    fun onConnectionState(state: ConnectionState): Effect {
        if (state is ConnectionState.Ready) wasReady = true
        return if (state is ConnectionState.Disconnected && wasReady) Effect.SHUTDOWN
        else Effect.NONE
    }
}
