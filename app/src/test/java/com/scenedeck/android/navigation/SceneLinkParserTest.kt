package com.scenedeck.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneLinkParserTest {

    @Test
    fun `parses simple scene link`() {
        assertEquals("Screen", SceneLinkParser.sceneName("scenedeck://scene/Screen"))
    }

    @Test
    fun `decodes percent-encoded scene names`() {
        assertEquals("Cam 1", SceneLinkParser.sceneName("scenedeck://scene/Cam%201"))
    }

    @Test
    fun `decodes special characters`() {
        assertEquals(
            "BRB / intermission",
            SceneLinkParser.sceneName("scenedeck://scene/BRB%20%2F%20intermission"),
        )
    }

    @Test
    fun `rejects wrong scheme`() {
        assertNull(SceneLinkParser.sceneName("https://scene/Screen"))
    }

    @Test
    fun `rejects wrong host`() {
        assertNull(SceneLinkParser.sceneName("scenedeck://input/Mic"))
    }

    @Test
    fun `rejects missing scene name`() {
        assertNull(SceneLinkParser.sceneName("scenedeck://scene"))
        assertNull(SceneLinkParser.sceneName("scenedeck://scene/"))
    }

    @Test
    fun `rejects null and blank input`() {
        assertNull(SceneLinkParser.sceneName(null))
        assertNull(SceneLinkParser.sceneName(""))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(SceneLinkParser.sceneName("not a uri at all :://"))
    }

    @Test
    fun `rejects ambiguous authority and null scene characters`() {
        assertNull(SceneLinkParser.sceneName("scenedeck://user@scene/Main"))
        assertNull(SceneLinkParser.sceneName("scenedeck://scene:4455/Main"))
        assertNull(SceneLinkParser.sceneName("scenedeck://scene/Main%00"))
    }
}
