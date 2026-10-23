package com.scenedeck.android.navigation

import com.scenedeck.android.background.SceneSwitchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLinkFeedbackTest {

    @Test
    fun `success is silent`() {
        assertNull(SceneLinkFeedback.messageFor(SceneSwitchResult.Success, "Cam 1"))
    }

    @Test
    fun `failures name the problem`() {
        assertEquals(
            "Can't switch scenes yet: add an OBS connection in SceneDeck first.",
            SceneLinkFeedback.messageFor(SceneSwitchResult.NoProfile, "Cam 1"),
        )
        assertEquals(
            "Couldn't reach OBS to switch to “Cam 1”.",
            SceneLinkFeedback.messageFor(SceneSwitchResult.NotConnected, "Cam 1"),
        )
        assertEquals(
            "OBS couldn't switch to “Ghost”. Check that the scene exists.",
            SceneLinkFeedback.messageFor(SceneSwitchResult.RequestFailed, "Ghost"),
        )
    }

    @Test
    fun `scene names are sanitized and capped`() {
        assertEquals("Cam 1", SceneLinkFeedback.displayName(" Cam 1\u0007 "))
        assertEquals("BadName", SceneLinkFeedback.displayName("Bad\u001bName"))
        val long = SceneLinkFeedback.displayName("x".repeat(200))
        assertEquals(40, long.length)
        assertTrue(long.endsWith("…"))
    }
}
