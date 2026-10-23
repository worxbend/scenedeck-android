package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorChecksTest {

    private fun entry(name: String, role: SceneRole) =
        SceneRegistryEntry(sceneName = name, role = role)

    private fun edge(from: String, to: String, verdict: EdgeVerdict) =
        SceneGraphEdge(from, to, verdict)

    private fun graph(
        nodes: List<SceneGraphNode>,
        edges: List<SceneGraphEdge> = emptyList(),
        cycles: Set<String> = emptySet(),
    ) = SceneGraph(nodes, edges, cycles)

    @Test
    fun unassignedRolesReportsOnlyObsScenesWithoutEntries() {
        val issues =
            DoctorChecks.unassignedRoles(
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
        val issues =
            DoctorChecks.staleEntries(
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
        val g =
            graph(
                nodes =
                    listOf(
                        SceneGraphNode("A", SceneRole.MODULE),
                        SceneGraphNode("B", SceneRole.MODULE),
                    ),
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
        val g =
            graph(
                nodes =
                    listOf(
                        SceneGraphNode("Live", SceneRole.PRIMARY),
                        SceneGraphNode("OtherLive", SceneRole.PRIMARY),
                        SceneGraphNode("Scratch", SceneRole.DEBUG),
                    ),
                edges =
                    listOf(
                        edge("Live", "OtherLive", EdgeVerdict.SUSPICIOUS),
                        edge("Live", "Scratch", EdgeVerdict.FORBIDDEN),
                    ),
            )
        val issues =
            DoctorChecks.hierarchyInversions(
                g,
                entries =
                    listOf(
                        entry("Live", SceneRole.PRIMARY),
                        entry("OtherLive", SceneRole.PRIMARY),
                        entry("Scratch", SceneRole.DEBUG),
                    ),
            )
        assertEquals(2, issues.size)
        val suspicious = issues.single { !it.title.startsWith("Forbidden") }
        assertEquals(DoctorSeverity.WARNING, suspicious.severity)
        assertEquals(
            DoctorSeverity.ERROR,
            issues.single { it.title.startsWith("Forbidden") }.severity,
        )
    }

    @Test
    fun hierarchyInversionsIgnoreImplicitDefaultRoles() {
        // Fresh install: no registry roles, every scene is only implicitly PRIMARY.
        val g =
            graph(
                nodes =
                    listOf(
                        SceneGraphNode("Camera", SceneRole.PRIMARY),
                        SceneGraphNode("Lower Third", SceneRole.PRIMARY),
                        SceneGraphNode("Screen", SceneRole.PRIMARY),
                    ),
                edges =
                    listOf(
                        edge("Camera", "Lower Third", EdgeVerdict.SUSPICIOUS),
                        edge("Screen", "Lower Third", EdgeVerdict.SUSPICIOUS),
                    ),
            )
        assertTrue(DoctorChecks.hierarchyInversions(g, entries = emptyList()).isEmpty())
        // An entry with curation but no role is still only implicitly PRIMARY.
        assertTrue(
            DoctorChecks.hierarchyInversions(
                    g,
                    entries =
                        listOf(
                            entry("Camera", SceneRole.PRIMARY),
                            SceneRegistryEntry("Lower Third", role = null),
                        ),
                )
                .isEmpty()
        )
        // Once both ends are explicitly assigned, the rule applies again.
        val explicit =
            DoctorChecks.hierarchyInversions(
                g,
                entries =
                    listOf(
                        entry("Camera", SceneRole.PRIMARY),
                        entry("Lower Third", SceneRole.PRIMARY),
                    ),
            )
        assertEquals(listOf("Camera"), explicit.map { it.sceneName })
    }

    @Test
    fun unassignedProductionSceneNestingExplicitDebugSceneIsStillForbidden() {
        val g =
            graph(
                nodes =
                    listOf(
                        SceneGraphNode("Show", SceneRole.PRIMARY),
                        SceneGraphNode("Scratch", SceneRole.DEBUG),
                        SceneGraphNode("Old Intro", SceneRole.ARCHIVE),
                    ),
                edges =
                    listOf(
                        edge("Show", "Scratch", EdgeVerdict.FORBIDDEN),
                        edge("Show", "Old Intro", EdgeVerdict.FORBIDDEN),
                    ),
            )
        val issues =
            DoctorChecks.hierarchyInversions(
                g,
                entries =
                    listOf(
                        entry("Scratch", SceneRole.DEBUG),
                        entry("Old Intro", SceneRole.ARCHIVE),
                    ),
            )
        assertEquals(2, issues.size)
        assertTrue(issues.all { it.severity == DoctorSeverity.ERROR && it.sceneName == "Show" })
        assertTrue(issues.all { "unassigned" in it.detail })
    }

    @Test
    fun primaryScenesToProbeSkipStaleAndImplicitScenes() {
        val scenes =
            DoctorChecks.primaryScenesToProbe(
                obsSceneNames = listOf("Camera", "Screen", "Parked"),
                entries =
                    listOf(
                        entry("Camera", SceneRole.PRIMARY),
                        entry("Ghost", SceneRole.PRIMARY),
                        entry("Parked", SceneRole.SECONDARY),
                        SceneRegistryEntry("Screen", role = null),
                    ),
            )
        assertEquals(listOf("Camera"), scenes)
    }

    @Test
    fun brokenAudioCopyAgreesWithInputCount() {
        val single =
            DoctorChecks.primaryWithBrokenAudio(
                listOf(entry("Camera", SceneRole.PRIMARY)),
                mapOf("Camera" to listOf("Mic")),
            )
        assertTrue(single[0].detail.endsWith("Mic reports no volume/mute state."))
        val plural =
            DoctorChecks.primaryWithBrokenAudio(
                listOf(entry("Camera", SceneRole.PRIMARY)),
                mapOf("Camera" to listOf("Mic", "Tone")),
            )
        assertTrue(plural[0].detail.endsWith("Mic, Tone report no volume/mute state."))
    }

    @Test
    fun unreferencedModulesAreInfo() {
        val g =
            graph(
                nodes =
                    listOf(
                        SceneGraphNode("Show", SceneRole.PRIMARY),
                        SceneGraphNode("LowerThird", SceneRole.MODULE),
                        SceneGraphNode("Orphan", SceneRole.MODULE),
                    ),
                edges = listOf(edge("Show", "LowerThird", EdgeVerdict.OK)),
            )
        val issues =
            DoctorChecks.unreferencedModules(
                obsSceneNames = listOf("Show", "LowerThird", "Orphan"),
                entries =
                    listOf(
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
        val issues =
            DoctorChecks.unreferencedModules(
                obsSceneNames = listOf("Show"),
                entries = listOf(entry("GhostModule", SceneRole.MODULE)),
                graph = g,
            )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun primaryWithBrokenAudioWarnsPerScene() {
        val issues =
            DoctorChecks.primaryWithBrokenAudio(
                entries =
                    listOf(
                        entry("Live", SceneRole.PRIMARY),
                        entry("Parked", SceneRole.SECONDARY),
                    ),
                brokenProbes =
                    mapOf(
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
        val g =
            graph(
                nodes =
                    listOf(
                        SceneGraphNode("A", SceneRole.PRIMARY),
                        SceneGraphNode("M", SceneRole.MODULE),
                    ),
                edges = listOf(edge("A", "M", EdgeVerdict.OK)),
            )
        assertTrue(DoctorChecks.circularReferences(g).isEmpty())
        assertTrue(
            DoctorChecks.hierarchyInversions(
                    g,
                    listOf(entry("A", SceneRole.PRIMARY), entry("M", SceneRole.MODULE)),
                )
                .isEmpty()
        )
        assertTrue(
            DoctorChecks.staleEntries(listOf("A"), listOf(entry("A", SceneRole.PRIMARY))).isEmpty()
        )
        assertTrue(
            DoctorChecks.unassignedRoles(listOf("A"), listOf(entry("A", SceneRole.PRIMARY)))
                .isEmpty()
        )
        assertTrue(
            DoctorChecks.primaryWithBrokenAudio(listOf(entry("A", SceneRole.PRIMARY)), emptyMap())
                .isEmpty()
        )
    }
}
