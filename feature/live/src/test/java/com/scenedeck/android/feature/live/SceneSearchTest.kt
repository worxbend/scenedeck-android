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
    fun blankQueryReturnsOriginalCollection() {
        assertSame(scenes, filterScenes(scenes, "  "))
    }
}
