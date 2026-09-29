package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryExportTest {

    private val fixtures = listOf(
        SceneRegistryEntry("Cam 1", SceneRole.PRIMARY, 0xFF7E57C2, "CAMERA", 0),
        SceneRegistryEntry("Quiet A", SceneRole.ARCHIVE, null, null, 5),
        SceneRegistryEntry("Nested Audio", SceneRole.MODULE, null, "MUSIC", 3),
    )

    @Test
    fun roundTripPreservesEverything() {
        val yaml = RegistryExportCodec.encode(fixtures)
        val decoded = RegistryExportCodec.decode(yaml)
        assertEquals(fixtures, decoded)
    }

    @Test
    fun exportHasYamlShapeAndFormatMarker() {
        val yaml = RegistryExportCodec.encode(fixtures)
        assertTrue(yaml.contains("scenedeck-registry"))
        assertTrue(yaml.contains("sceneName"))
        assertTrue(yaml.contains("Cam 1"))
    }

    @Test
    fun decodeRejectsGarbage() {
        assertFailsWithRegistryError { RegistryExportCodec.decode("not: [valid") }
        assertFailsWithRegistryError { RegistryExportCodec.decode("just a string") }
    }

    @Test
    fun decodeRejectsForeignFormat() {
        assertFailsWithRegistryError {
            RegistryExportCodec.decode("format: other-tool\nversion: 1\nentries: []\n")
        }
    }

    @Test
    fun unknownRoleFallsBackToSecondary() {
        val yaml = """
            format: scenedeck-registry
            version: 1
            entries:
            - sceneName: X
              role: HERO
              sortOrder: 0
        """.trimIndent()
        assertEquals(SceneRole.SECONDARY, RegistryExportCodec.decode(yaml).single().role)
    }

    private inline fun assertFailsWithRegistryError(block: () -> Unit) {
        try {
            block()
        } catch (e: RegistryImportException) {
            org.junit.Assert.assertTrue(e.message != null)
            return
        }
        throw AssertionError("expected RegistryImportException")
    }
}
