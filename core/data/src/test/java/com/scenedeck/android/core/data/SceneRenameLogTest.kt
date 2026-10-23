package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneRenameLogTest {

    @Test
    fun followsOnlyRenamesRecordedAfterTheMark() {
        val log = SceneRenameLog()
        log.record("Intro", "Opening")
        val mark = log.mark()
        log.record("Cam 1", "Camera")
        log.record("Camera", "Main camera")

        assertEquals("Main camera", log.follow("Cam 1", mark))
        // Before the mark: a read taken afterwards already has the new name.
        assertEquals("Intro", log.follow("Intro", mark))
        assertEquals("Screen", log.follow("Screen", mark))
        assertNull(log.follow(null, mark))
    }

    @Test
    fun followingAnAlreadyRenamedNameIsIdempotent() {
        val log = SceneRenameLog()
        val mark = log.mark()
        log.record("A", "B")

        assertEquals("B", log.follow("A", mark))
        assertEquals("B", log.follow("B", mark))
    }

    @Test
    fun swapsThroughATemporaryNameResolvePerScene() {
        val log = SceneRenameLog()
        val mark = log.mark()
        log.record("A", "tmp")
        log.record("B", "A")
        log.record("tmp", "B")

        assertEquals("B", log.follow("A", mark))
        assertEquals("A", log.follow("B", mark))
    }

    @Test
    fun boundedCapacityDropsOldestAndClearForgetsAll() {
        val log = SceneRenameLog(capacity = 2)
        val mark = log.mark()
        log.record("A", "B")
        log.record("X", "Y")
        log.record("B", "C")

        // "A -> B" fell out of the window; the retained renames still apply in order.
        assertEquals("A", log.follow("A", mark))
        assertEquals("C", log.follow("B", mark))

        log.clear()
        assertEquals("B", log.follow("B", mark))
    }

    @Test
    fun followScenesKeepsAReadThatWouldOtherwiseDuplicateANewName() {
        val log = SceneRenameLog()
        val mark = log.mark()
        log.record("Cam 1", "Camera")

        val stale = listOf(SceneSummary("Cam 1", 0), SceneSummary("Intro", 1))
        assertEquals(listOf("Camera", "Intro"), log.followScenes(stale, mark).map { it.name })

        // Answered after the rename and after a new scene reused the old name.
        val fresh = listOf(SceneSummary("Camera", 0), SceneSummary("Cam 1", 1))
        assertEquals(fresh, log.followScenes(fresh, mark))
    }
}
