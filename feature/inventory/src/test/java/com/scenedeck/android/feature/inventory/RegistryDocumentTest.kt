package com.scenedeck.android.feature.inventory

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistryDocumentTest {
    @Test
    fun decodesUtf8Document() {
        val payload = "Scene: Café"
        assertEquals(payload, readRegistryDocument(ByteArrayInputStream(payload.toByteArray())))
    }

    @Test
    fun acceptsExactSizeLimit() {
        val bytes = ByteArray(MAX_REGISTRY_DOCUMENT_BYTES) { 'a'.code.toByte() }
        assertEquals(
            MAX_REGISTRY_DOCUMENT_BYTES,
            readRegistryDocument(ByteArrayInputStream(bytes)).length,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDocument() {
        readRegistryDocument(ByteArrayInputStream(ByteArray(MAX_REGISTRY_DOCUMENT_BYTES + 1)))
    }
}
