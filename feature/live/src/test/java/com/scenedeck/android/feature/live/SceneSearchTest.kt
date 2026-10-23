package com.scenedeck.android.feature.live

import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SceneSearchTest {
    private val scenes =
        listOf("Camera B", "Break [live]", "Camera A").mapIndexed { index, name ->
            SceneCardState(name, SceneRole.PRIMARY, null, null, index, false)
        }

    @Test
    fun caseInsensitiveSearchPreservesCuratedOrder() {
        assertEquals(
            listOf("Camera B", "Camera A"),
            filterScenes(scenes, "  CAMERA  ").map { it.name },
        )
    }

    @Test
    fun searchIsLiteralRatherThanRegex() {
        assertEquals(listOf("Break [live]"), filterScenes(scenes, "[live]").map { it.name })
        assertEquals(emptyList<SceneCardState>(), filterScenes(scenes, "nonexistent"))
    }

    @Test
    fun deckPositionsStayStableWhileFiltering() {
        val positions = scenePositions(scenes)
        val filtered = filterScenes(scenes, "camera a")
        assertEquals(listOf("Camera A"), filtered.map { it.name })
        assertEquals(listOf(3), filtered.map { positions.getValue(it.name) })
        assertEquals(mapOf("Camera B" to 1, "Break [live]" to 2, "Camera A" to 3), positions)
    }

    @Test
    fun blankQueryReturnsOriginalCollection() {
        assertSame(scenes, filterScenes(scenes, "  "))
    }
}
