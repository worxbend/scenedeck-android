package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleRulesTest {

    @Test
    fun liveScenesMayDependOnModuleAndRaw() {
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.PRIMARY, SceneRole.MODULE))
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.PRIMARY, SceneRole.RAW))
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.SECONDARY, SceneRole.MODULE))
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.SECONDARY, SceneRole.RAW))
    }

    @Test
    fun moduleAndRawHierarchies() {
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.MODULE, SceneRole.MODULE))
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.MODULE, SceneRole.RAW))
        assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.RAW, SceneRole.RAW))
    }

    @Test
    fun liveToLiveIsSuspicious() {
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.PRIMARY, SceneRole.PRIMARY),
        )
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.PRIMARY, SceneRole.SECONDARY),
        )
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.SECONDARY, SceneRole.PRIMARY),
        )
    }

    @Test
    fun invertedHierarchyIsSuspicious() {
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.MODULE, SceneRole.PRIMARY),
        )
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.MODULE, SceneRole.SECONDARY),
        )
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.RAW, SceneRole.MODULE),
        )
        assertEquals(
            EdgeVerdict.SUSPICIOUS,
            RoleRules.classifyEdge(SceneRole.RAW, SceneRole.PRIMARY),
        )
    }

    @Test
    fun productionIntoDebugOrArchiveIsForbidden() {
        listOf(SceneRole.PRIMARY, SceneRole.SECONDARY, SceneRole.MODULE, SceneRole.RAW).forEach {
            parent ->
            assertEquals(EdgeVerdict.FORBIDDEN, RoleRules.classifyEdge(parent, SceneRole.DEBUG))
            assertEquals(EdgeVerdict.FORBIDDEN, RoleRules.classifyEdge(parent, SceneRole.ARCHIVE))
        }
    }

    @Test
    fun debugAndArchiveMayReferenceAnything() {
        SceneRole.entries.forEach { child ->
            assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.DEBUG, child))
            assertEquals(EdgeVerdict.OK, RoleRules.classifyEdge(SceneRole.ARCHIVE, child))
        }
    }
}
