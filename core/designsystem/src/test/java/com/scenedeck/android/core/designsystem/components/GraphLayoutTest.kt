package com.scenedeck.android.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLayoutTest {

    @Test
    fun layersFollowLongestPath() {
        val layout =
            layeredGraphLayout(
                nodeNames = listOf("A", "B", "C", "D"),
                edges = listOf("A" to "B", "B" to "C", "A" to "D", "D" to "C"),
            )
        assertEquals(0, layout.getValue("A").layer)
        assertEquals(1, layout.getValue("B").layer)
        assertEquals(1, layout.getValue("D").layer)
        // C via longest path (A→B→C = 2, not A→D→C = 2 — same here)
        assertEquals(2, layout.getValue("C").layer)
    }

    @Test
    fun cyclesDoNotHangAndGetBoundedLayers() {
        val layout =
            layeredGraphLayout(
                nodeNames = listOf("A", "B", "C"),
                edges = listOf("A" to "B", "B" to "A", "B" to "C"),
            )
        assertTrue(layout.getValue("C").layer < 3)
        assertTrue(layout.getValue("A").layer < 3)
        assertTrue(layout.getValue("B").layer < 3)
    }

    @Test
    fun disconnectedNodesStayAtLayerZero() {
        val layout =
            layeredGraphLayout(
                nodeNames = listOf("A", "Z", "Y"),
                edges = emptyList(),
            )
        assertTrue(layout.values.all { it.layer == 0 })
        // Rows are name-sorted for deterministic goldens.
        assertEquals(0, layout.getValue("A").row)
        assertEquals(1, layout.getValue("Y").row)
        assertEquals(2, layout.getValue("Z").row)
    }

    @Test
    fun layoutIsDeterministic() {
        val nodes = listOf("Root", "M1", "M2", "Leaf")
        val edges = listOf("Root" to "M1", "Root" to "M2", "M1" to "Leaf", "M2" to "Leaf")
        assertEquals(
            layeredGraphLayout(nodes, edges),
            layeredGraphLayout(nodes.shuffled(), edges.shuffled()),
        )
    }
}
