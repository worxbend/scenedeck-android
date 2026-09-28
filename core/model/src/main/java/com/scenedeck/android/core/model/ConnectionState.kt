package com.scenedeck.android.core.model

/** Connection session state machine (docs/ARCHITECTURE.md — OBS lane design). */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    /** First connection attempt: TCP/WebSocket connect in progress. */
    data object Connecting : ConnectionState

    /** WebSocket up; Hello/Identify handshake in progress. */
    data object Identifying : ConnectionState

    /** Handshake complete; requests and events flow. */
    data class Ready(val sessionInfo: ObsVersionInfo) : ConnectionState

    /** Unexpected socket loss; waiting [attempt]-th backoff before retrying. */
    data class Reconnecting(val attempt: Int) : ConnectionState

    /** Terminal failure (no automatic retry). */
    data class Failed(val error: ConnectionError) : ConnectionState
}

/** Typed connection failures. */
sealed interface ConnectionError {
    /** Authentication failed (bad password / password required). Never retried. */
    data class Auth(val message: String? = null) : ConnectionError

    /** Host unreachable, connection refused, DNS failure, timeout. */
    data class Unreachable(val cause: Throwable? = null) : ConnectionError

    /** Server violated the protocol (unexpected op codes, bad RPC version). */
    data class Protocol(val message: String) : ConnectionError

    /** Server closed the session (carries the obs-websocket close code/reason). */
    data class Closed(val code: Int, val reason: String) : ConnectionError
}
