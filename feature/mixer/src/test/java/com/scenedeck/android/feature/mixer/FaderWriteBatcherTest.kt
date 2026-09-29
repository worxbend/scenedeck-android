package com.scenedeck.android.feature.mixer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaderWriteBatcherTest {

    @Test
    fun rapidPreviewsCoalesceToOneWrite() = runTest {
        val sent = mutableListOf<Pair<String, Double>>()
        val batcher = FaderWriteBatcher(backgroundScope, 120) { name, mul -> sent += name to mul }

        batcher.preview("Mic", 1.0)
        batcher.preview("Mic", 0.8)
        batcher.preview("Mic", 0.6)
        advanceTimeBy(119)
        runCurrent()
        assertEquals(0, sent.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("Mic" to 0.6), sent)
    }

    @Test
    fun commitSendsImmediatelyAndCancelsPending() = runTest {
        val sent = mutableListOf<Pair<String, Double>>()
        val batcher = FaderWriteBatcher(backgroundScope, 120) { name, mul -> sent += name to mul }

        batcher.preview("Mic", 0.9)
        batcher.commit("Mic", 0.5)
        runCurrent()
        assertEquals(listOf("Mic" to 0.5), sent)

        // The cancelled pending preview must not fire afterwards.
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, sent.size)
    }

    @Test
    fun inputsAreBatchedIndependently() = runTest {
        val sent = mutableListOf<Pair<String, Double>>()
        val batcher = FaderWriteBatcher(backgroundScope, 120) { name, mul -> sent += name to mul }

        batcher.preview("A", 0.5)
        batcher.preview("B", 0.25)
        advanceTimeBy(130)
        runCurrent()
        assertEquals(2, sent.size)
        assertEquals(setOf("A" to 0.5, "B" to 0.25), sent.toSet())
    }
}
