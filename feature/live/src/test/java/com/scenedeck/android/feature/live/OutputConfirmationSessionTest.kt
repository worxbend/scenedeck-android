package com.scenedeck.android.feature.live

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputConfirmationSessionTest {
    @Test
    fun sameHandshakeAcceptsConsent() {
        val session = ready()
        val guard = OutputConfirmationSession()
        guard.bind(session)
        assertTrue(guard.matches(session))
    }

    @Test
    fun reconnectToIdenticalServerDoesNotReuseConsent() {
        val guard = OutputConfirmationSession()
        guard.bind(ready())
        assertFalse(guard.matches(ready()))
    }

    @Test
    fun disconnectRejectsConsent() {
        val guard = OutputConfirmationSession()
        guard.bind(ready())
        assertFalse(guard.matches(ConnectionState.Disconnected))
    }

    @Test
    fun dismissalRejectsConsent() {
        val session = ready()
        val guard = OutputConfirmationSession()
        guard.bind(session)
        guard.clear()
        assertFalse(guard.matches(session))
    }

    private fun ready() = ConnectionState.Ready(ObsVersionInfo("32", "5", 1, "test"))
}
