package com.scenedeck.android.feature.live

import com.scenedeck.android.core.obs.ObsRequestFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportErrorMessageTest {
    @Test
    fun serverFailureKeepsActionContextWithoutPrivateDetails() {
        val failure = ObsRequestFailedException("StopStream", "PRIVATE_STATUS", "private-password")
        val message = failure.toTransportMessage("stream")

        assertTrue(message.contains("stream"))
        assertTrue(message.contains("OBS"))
        assertFalse(message.contains("private-password"))
        assertFalse(message.contains("PRIVATE_STATUS"))
        assertFalse(message.contains("StopStream"))
    }

    @Test
    fun arbitraryTransportMessagesAndCausesStayOutOfSnackbar() {
        val failure = IllegalStateException("ws://private-endpoint", RuntimeException("secret"))
        val message = failure.toTransportMessage("recording")

        assertTrue(message.contains("recording"))
        assertFalse(message.contains("private-endpoint"))
        assertFalse(message.contains("secret"))
        assertEquals(
            RuntimeException("different private detail").toTransportMessage("recording"),
            message,
        )
    }
}
