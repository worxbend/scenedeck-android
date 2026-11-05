package com.scenedeck.android.feature.live

import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneAliasUiTest {
    private val card =
        SceneCardState("OBS Camera", SceneRole.ARCHIVE, null, null, 5, false, localAlias = "Host")

    @Test
    fun searchMatchesBothNamesWithoutRenumberingOrChangingAddress() {
        val other = card.copy(name = "Remote Camera", localAlias = "Host", sortOrder = 6)
        val scenes = listOf(card, other)
        assertEquals(scenes, filterScenes(scenes, "HOST"))
        assertEquals(listOf(card), filterScenes(scenes, "OBS Camera"))
        assertEquals(mapOf("OBS Camera" to 1, "Remote Camera" to 2), scenePositions(scenes))
    }

    @Test
    fun aliasEditPreservesUnchangedArchiveAndUnassignedRoles() {
        assertEquals(SceneRole.ARCHIVE, card.metadataEdit(false, null, null, "Guest").role)
        val unassigned = card.copy(role = SceneRole.PRIMARY, roleAssigned = false)
        assertNull(unassigned.metadataEdit(true, null, null, "Guest").role)
        assertEquals(SceneRole.SECONDARY, unassigned.metadataEdit(false, null, null, "Guest").role)
        assertEquals(SceneRole.PRIMARY, card.metadataEdit(true, null, null, "Guest").role)
    }
}
