package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneGraphCycleTest {

    private fun edges(vararg pairs: Pair<String, String>) = pairs.map { (from, to) ->
        SceneGraphEdge(from, to, EdgeVerdict.OK)
    }

    @Test
    fun dagHasNoCycleMembers() {
        val result =
            findCycleMembers(
                nodes = setOf("A", "B", "C", "D"),
                edges = edges("A" to "B", "B" to "C", "A" to "D"),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun selfLoopIsACycle() {
        val result = findCycleMembers(setOf("A"), edges("A" to "A"))
        assertEquals(setOf("A"), result)
    }

    @Test
    fun twoNodeCycle() {
        val result =
            findCycleMembers(
                nodes = setOf("A", "B", "C"),
                edges = edges("A" to "B", "B" to "A", "B" to "C"),
            )
        assertEquals(setOf("A", "B"), result)
    }

    @Test
    fun threeNodeCycleIncludesAllMembers() {
        val result =
            findCycleMembers(
                nodes = setOf("A", "B", "C", "D"),
                edges = edges("A" to "B", "B" to "C", "C" to "A", "D" to "A"),
            )
        assertEquals(setOf("A", "B", "C"), result)
    }

    @Test
    fun cycleInBranchDoesNotMarkUpstream() {
        val result =
            findCycleMembers(
                nodes = setOf("Root", "A", "B"),
                edges = edges("Root" to "A", "A" to "B", "B" to "A"),
            )
        assertEquals(setOf("A", "B"), result)
    }

    @Test
    fun overlappingCyclesIncludePreviouslyFinishedBranch() {
        val result =
            findCycleMembers(
                setOf("A", "B", "C"),
                edges("A" to "B", "B" to "A", "A" to "C", "C" to "B"),
            )
        assertEquals(setOf("A", "B", "C"), result)
    }

    @Test
    fun largeDependencyChainDoesNotOverflowCallStack() {
        val nodes = (0..10000).map { "Scene$it" }.toSet()
        val edges =
            (0 until 10000).map { SceneGraphEdge("Scene$it", "Scene${it + 1}", EdgeVerdict.OK) }
        assertTrue(findCycleMembers(nodes, edges).isEmpty())
    }
}
