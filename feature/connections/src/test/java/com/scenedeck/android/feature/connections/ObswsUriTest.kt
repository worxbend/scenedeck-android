package com.scenedeck.android.feature.connections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObswsUriTest {

    @Test
    fun plainHostGetsDefaultPort() {
        val target = parseObswsUri("obsws://192.168.1.20")
        assertEquals(ObswsTarget(host = "192.168.1.20", port = 4455), target)
    }

    @Test
    fun hostWithPort() {
        val target = parseObswsUri("obsws://studio.local:4456")
        assertEquals("studio.local", target?.host)
        assertEquals(4456, target?.port)
        assertNull(target?.password)
    }

    @Test
    fun passwordAndNameParams() {
        val target = parseObswsUri("obsws://studio.local:4455?password=s3cret&name=Studio%20rig")
        assertEquals("s3cret", target?.password)
        assertEquals("Studio rig", target?.suggestedName)
    }

    @Test
    fun urlEncodedPasswordIsDecoded() {
        val target = parseObswsUri("obsws://host?password=p%40ss%26w0rd")
        assertEquals("p@ss&w0rd", target?.password)
    }

    @Test
    fun rejectsForeignSchemes() {
        assertNull(parseObswsUri("http://192.168.1.20:4455"))
        assertNull(parseObswsUri("https://evil.example/obsws"))
    }

    @Test
    fun rejectsMissingHost() {
        assertNull(parseObswsUri("obsws://"))
        assertNull(parseObswsUri("obsws://:4455"))
    }

    @Test
    fun rejectsInvalidPort() {
        assertNull(parseObswsUri("obsws://host:0"))
        assertNull(parseObswsUri("obsws://host:99999"))
        assertNull(parseObswsUri("obsws://host:notaport"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(parseObswsUri(""))
        assertNull(parseObswsUri("not a uri at all"))
    }
}
