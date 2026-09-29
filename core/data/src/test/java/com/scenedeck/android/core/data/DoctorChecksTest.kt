package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorChecksTest {

    private fun entry(name: String, role: SceneRole) = SceneRegistryEntry(sceneName = name, role = role)

    private fun edge(from: String, to: String, verdict: EdgeVerdict) =
        SceneGraphEdge(from, to, verdict)

    private fun graph(
        nodes: List<SceneGraphNode>,
        edges: List<SceneGraphEdge> = emptyList(),
        cycles: Set<String> = emptySet(),
    ) = SceneGraph(nodes, edges, cycles)

    @Test
    fun unassignedRolesReportsOnlyObsScenesWithoutEntries() {
        val issues = DoctorChecks.unassignedRoles(
            obsSceneNames = listOf("A", "B", "C"),
            entries = listOf(entry("B", SceneRole.PRIMARY)),
        )
        assertEquals(2, issues.size)
        assertTrue(issues.all { it.severity == DoctorSeverity.INFO })
        assertEquals(listOf("A", "C"), issues.map { it.sceneName })
        assertTrue(issues.all { it.fix is DoctorFix.AssignRole })
    }

    @Test
    fun staleEntriesWarnAndOfferRemoval() {
        val issues = DoctorChecks.staleEntries(
            obsSceneNames = listOf("A"),
            entries = listOf(entry("A", SceneRole.PRIMARY), entry("Ghost", SceneRole.MODULE)),
        )
        assertEquals(1, issues.size)
        assertEquals(DoctorSeverity.WARNING, issues[0].severity)
        assertEquals("Ghost", issues[0].sceneName)
        assertEquals(DoctorFix.RemoveStaleEntry("Ghost"), issues[0].fix)
    }

    @Test
    fun circularReferencesIsAnError() {
        val g = graph(
            nodes = listOf(SceneGraphNode("A", SceneRole.MODULE), SceneGraphNode("B", SceneRole.MODULE)),
            cycles = setOf("A", "B"),
        )
        val issues = DoctorChecks.circularReferences(g)
        assertEquals(1, issues.size)
        assertEquals(DoctorSeverity.ERROR, issues[0].severity)
        assertTrue(issues[0].detail.contains("A") && issues[0].detail.contains("B"))
    }

    @Test
    fun noCycleNoIssue() {
        assertTrue(DoctorChecks.circularReferences(graph(emptyList())).isEmpty())
    }

    @Test
    fun hierarchyInversionsSeverityPerVerdict() {
        val g = graph(
            nodes = listOf(
                SceneGraphNode("Live", SceneRole.PRIMARY),
                SceneGraphNode("OtherLive", SceneRole.PRIMARY),
                SceneGraphNode("Scratch", SceneRole.DEBUG),
            ),
            edges = listOf(
                edge("Live", "OtherLive", EdgeVerdict.SUSPICIOUS),
                edge("Live", "Scratch", EdgeVerdict.FORBIDDEN),
            ),
        )
        val issues = DoctorChecks.hierarchyInversions(g)
        assertEquals(2, issues.size)
        val suspicious = issues.single { !it.title.startsWith("Forbidden") }
        assertEquals(DoctorSeverity.WARNING, suspicious.severity)
        assertEquals(DoctorSeverity.ERROR, issues.single { it.title.startsWith("Forbidden") }.severity)
    }

    @Test
    fun unreferencedModulesAreInfo() {
        val g = graph(
            nodes = listOf(
                SceneGraphNode("Show", SceneRole.PRIMARY),
                SceneGraphNode("LowerThird", SceneRole.MODULE),
                SceneGraphNode("Orphan", SceneRole.MODULE),
            ),
            edges = listOf(edge("Show", "LowerThird", EdgeVerdict.OK)),
        )
        val issues = DoctorChecks.unreferencedModules(
            obsSceneNames = listOf("Show", "LowerThird", "Orphan"),
            entries = listOf(
                entry("Show", SceneRole.PRIMARY),
                entry("LowerThird", SceneRole.MODULE),
                entry("Orphan", SceneRole.MODULE),
            ),
            graph = g,
        )
        assertEquals(listOf("Orphan"), issues.map { it.sceneName })
        assertTrue(issues.all { it.severity == DoctorSeverity.INFO })
    }

    @Test
    fun staleModuleIsNotReportedAsUnreferenced() {
        val g = graph(nodes = emptyList())
        val issues = DoctorChecks.unreferencedModules(
            obsSceneNames = listOf("Show"),
            entries = listOf(entry("GhostModule", SceneRole.MODULE)),
            graph = g,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun primaryWithBrokenAudioWarnsPerScene() {
        val issues = DoctorChecks.primaryWithBrokenAudio(
            entries = listOf(
                entry("Live", SceneRole.PRIMARY),
                entry("Parked", SceneRole.SECONDARY),
            ),
            brokenProbes = mapOf(
                "Live" to listOf("Scratch Tone"),
                "Parked" to listOf("Ignored Tone"),
            ),
        )
        assertEquals(1, issues.size)
        assertEquals(DoctorSeverity.WARNING, issues[0].severity)
        assertEquals("Live", issues[0].sceneName)
        assertTrue(issues[0].detail.contains("Scratch Tone"))
    }

    @Test
    fun cleanFixturesProduceNoIssues() {
        val g = graph(
            nodes = listOf(SceneGraphNode("A", SceneRole.PRIMARY), SceneGraphNode("M", SceneRole.MODULE)),
            edges = listOf(edge("A", "M", EdgeVerdict.OK)),
        )
        assertTrue(DoctorChecks.circularReferences(g).isEmpty())
        assertTrue(DoctorChecks.hierarchyInversions(g).isEmpty())
        assertTrue(DoctorChecks.staleEntries(listOf("A"), listOf(entry("A", SceneRole.PRIMARY))).isEmpty())
        assertTrue(DoctorChecks.unassignedRoles(listOf("A"), listOf(entry("A", SceneRole.PRIMARY))).isEmpty())
        assertTrue(DoctorChecks.primaryWithBrokenAudio(listOf(entry("A", SceneRole.PRIMARY)), emptyMap()).isEmpty())
    }
}
