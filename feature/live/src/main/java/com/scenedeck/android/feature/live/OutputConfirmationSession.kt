package com.scenedeck.android.feature.live

import com.scenedeck.android.core.model.ConnectionState

/** Confirmation consent belongs to one handshake, even when server version metadata matches. */
internal class OutputConfirmationSession {
    private var session: ConnectionState.Ready? = null

    fun bind(connection: ConnectionState.Ready) {
        session = connection
    }

    fun matches(connection: ConnectionState): Boolean = session != null && session === connection

    fun clear() {
        session = null
    }
}
