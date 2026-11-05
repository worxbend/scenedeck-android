package com.scenedeck.android.feature.live

import com.scenedeck.android.core.data.DeckSelection
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeck
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedDeckProjectionTest {
    private val primary = SceneCardState("Camera", SceneRole.PRIMARY, null, null, 0, true)
    private val secondary = SceneCardState("Wide", SceneRole.ARCHIVE, null, null, 1, false)
    private val state = DeckState(scenes = listOf(primary), allScenes = listOf(primary, secondary))

    @Test
    fun namedOrderIncludesExplicitNonPrimaryAndPreservesMissingAndSearchPositions() {
        val snapshot =
            NamedDeckSnapshot(
                listOf(NamedDeck("show", "Show", listOf("Missing", "Wide", "Camera"))),
                DeckSelection.Named("show"),
            )
        val projection = projectNamedDeck(state, snapshot)
        assertEquals(listOf(secondary, primary), projection.scenes)
        assertEquals(listOf("Missing"), projection.missing)
        assertEquals(listOf(secondary), filterScenes(projection.scenes, "wide"))
        assertEquals(2, projection.positions["Wide"])
        assertFalse(projection.allowsReorder(snapshot.selection, ""))
        assertEquals(listOf(primary), state.scenes)
    }

    @Test
    fun builtinsKeepExistingProjectionAndUnknownDeckStorageShowsNoCommands() {
        assertTrue(projectNamedDeck(state, null).scenes.isEmpty())
        assertEquals(state.allScenes, projectNamedDeck(state, NamedDeckSnapshot()).scenes)
        val primaryProjection =
            projectNamedDeck(state, NamedDeckSnapshot(selection = DeckSelection.Primary))
        assertEquals(state.scenes, primaryProjection.scenes)
        assertFalse(primaryProjection.allowsReorder(DeckSelection.Primary, ""))
        assertFalse(primaryProjection.allowsReorder(DeckSelection.All, "Camera"))
        assertTrue(primaryProjection.allowsReorder(DeckSelection.All, ""))
    }
}
