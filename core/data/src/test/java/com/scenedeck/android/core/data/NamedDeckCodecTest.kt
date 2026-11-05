package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedDeckCodecTest {
    @Test
    fun rejectsWrongFormatsVersionsDuplicatesAndOversizedInputWithSafeErrors() {
        val documents =
            listOf(
                "secret database error",
                """{"format":"other","version":1,"decks":[]}""",
                """{"format":"scenedeck-decks","version":2,"decks":[]}""",
                """{"format":"scenedeck-decks","version":1,"decks":[
                    {"name":"Show","scenes":[]},{"name":" SHOW ","scenes":[]}]}""",
                " ".repeat(MAX_DECK_DOCUMENT_BYTES + 1),
            )
        documents.forEach { payload ->
            val error = runCatching { NamedDeckCodec.decode(payload) }.exceptionOrNull()
            assertTrue(error is DeckEditException)
            assertTrue(error?.message in DeckEditFailure.entries.map { it.name })
        }
    }

    @Test
    fun preservesLiteralSceneNamesAndFirstDuplicateOrder() {
        val entries =
            NamedDeckCodec.decode(
                """{"format":"scenedeck-decks","version":1,"decks":[
                    {"name":" Show ","scenes":["  Camera  ","Wide","  Camera  "]}]}"""
            )
        assertEquals(listOf(DeckDocumentEntry("Show", listOf("  Camera  ", "Wide"))), entries)
    }
}
