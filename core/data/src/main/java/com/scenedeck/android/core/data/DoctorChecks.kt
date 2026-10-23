package com.scenedeck.android.core.data

/** Severity buckets for the Doctor report (FEATURE_SPEC §7). */
enum class DoctorSeverity {
    ERROR,
    WARNING,
    INFO,
}

/** One-tap remediation offered for an issue. */
sealed interface DoctorFix {
    /** Remove a stale registry entry. */
    data class RemoveStaleEntry(val sceneName: String) : DoctorFix

    /** Assign a role (jumps to Inventory pre-selected). */
    data class AssignRole(val sceneName: String) : DoctorFix
}

/** A single diagnostic finding. */
data class DoctorIssue(
    val severity: DoctorSeverity,
    val checkId: String,
    val title: String,
    val detail: String,
    val sceneName: String? = null,
    val fix: DoctorFix? = null,
)

/**
 * Pure diagnostic checks (FEATURE_SPEC §7) — every check is a pure function over fixtures so it's
 * fully unit-testable; orchestration lives in the ViewModel.
 */
object DoctorChecks {

    /** INFO: OBS scenes with no registry entry (they implicitly render as PRIMARY). */
    fun unassignedRoles(
        obsSceneNames: List<String>,
        entries: List<SceneRegistryEntry>,
    ): List<DoctorIssue> {
        val assigned = entries.filter { it.role != null }.map { it.sceneName }.toSet()
        return obsSceneNames
            .filter { it !in assigned }
            .map { name ->
                DoctorIssue(
                    severity = DoctorSeverity.INFO,
                    checkId = "unassigned-role",
                    title = "No role assigned",
                    detail = "“$name” has no role and is treated as Primary on the deck.",
                    sceneName = name,
                    fix = DoctorFix.AssignRole(name),
                )
            }
    }

    /** WARNING: registry entries whose scene no longer exists in OBS. */
    fun staleEntries(
        obsSceneNames: List<String>,
        entries: List<SceneRegistryEntry>,
    ): List<DoctorIssue> {
        val present = obsSceneNames.toSet()
        return entries
            .filter { it.sceneName !in present }
            .map { entry ->
                DoctorIssue(
                    severity = DoctorSeverity.WARNING,
                    checkId = "stale-entry",
                    title = "Stale registry entry",
                    detail =
                        "“${entry.sceneName}” has curation metadata but no matching OBS scene.",
                    sceneName = entry.sceneName,
                    fix = DoctorFix.RemoveStaleEntry(entry.sceneName),
                )
            }
    }

    /** ERROR: scene reference cycles. */
    fun circularReferences(graph: SceneGraph): List<DoctorIssue> {
        if (graph.cycleMembers.isEmpty()) return emptyList()
        return listOf(
            DoctorIssue(
                severity = DoctorSeverity.ERROR,
                checkId = "cycle",
                title = "Circular scene reference",
                detail =
                    "These scenes reference each other in a loop: " +
                        graph.cycleMembers.sorted().joinToString(" → ") +
                        ". " +
                        "OBS will silently refuse to render the loop — break one edge.",
            )
        )
    }

    /**
     * WARNING/ERROR: edges violating the role rules (SUSPICIOUS/FORBIDDEN).
     *
     * Only verdicts that follow from explicitly assigned roles are reported: a scene without a
     * registry role is only *implicitly* Primary, so flagging it would turn every nested scene of a
     * fresh, uncurated collection into noise. Production scenes nesting a scene explicitly marked
     * Debug/Archive stay FORBIDDEN even when the parent is unassigned, because that verdict depends
     * on the child's explicit role alone.
     */
    fun hierarchyInversions(
        graph: SceneGraph,
        entries: List<SceneRegistryEntry>,
    ): List<DoctorIssue> {
        // First duplicate entry wins, as in the graph metadata.
        val explicitRoles =
            buildMap<String, SceneRole?> {
                    entries.forEach { if (it.sceneName !in this) put(it.sceneName, it.role) }
                }
                .filterValues { it != null }
        val roleOf = { name: String ->
            explicitRoles[name]?.name ?: "unassigned, treated as ${SceneRole.PRIMARY}"
        }
        return graph.edges
            .filter { it.verdict != EdgeVerdict.OK }
            .filter { reportsInversion(it, explicitRoles) }
            .map { edge ->
                val forbidden = edge.verdict == EdgeVerdict.FORBIDDEN
                DoctorIssue(
                    severity = if (forbidden) DoctorSeverity.ERROR else DoctorSeverity.WARNING,
                    checkId = "hierarchy-inversion",
                    title =
                        if (forbidden) "Forbidden scene dependency"
                        else "Surprising scene dependency",
                    detail =
                        "“${edge.from}” (${roleOf(edge.from)}) depends on " +
                            "“${edge.to}” (${roleOf(edge.to)}), which the role rules " +
                            (if (forbidden) "forbid." else "flag as suspicious."),
                    sceneName = edge.from,
                )
            }
    }

    private fun reportsInversion(
        edge: SceneGraphEdge,
        explicitRoles: Map<String, SceneRole?>,
    ): Boolean {
        val child = explicitRoles[edge.to]
        val bothExplicit = edge.from in explicitRoles && child != null
        val explicitNonProductionChild =
            edge.verdict == EdgeVerdict.FORBIDDEN &&
                (child == SceneRole.DEBUG || child == SceneRole.ARCHIVE)
        return bothExplicit || explicitNonProductionChild
    }

    /** INFO: MODULE scenes not referenced by any other scene. */
    fun unreferencedModules(
        obsSceneNames: List<String>,
        entries: List<SceneRegistryEntry>,
        graph: SceneGraph,
    ): List<DoctorIssue> {
        val referenced = graph.edges.map { it.to }.toSet()
        return entries
            .filter { it.role == SceneRole.MODULE && it.sceneName in obsSceneNames }
            .filter { it.sceneName !in referenced }
            .map { entry ->
                DoctorIssue(
                    severity = DoctorSeverity.INFO,
                    checkId = "unreferenced-module",
                    title = "Module scene never used",
                    detail = "“${entry.sceneName}” is marked Module but no scene nests it.",
                    sceneName = entry.sceneName,
                )
            }
    }

    /**
     * Scenes the broken-audio probe should visit: explicitly PRIMARY entries that still exist in
     * OBS (stale entries are reported separately and must not abort the scan).
     */
    fun primaryScenesToProbe(
        obsSceneNames: List<String>,
        entries: List<SceneRegistryEntry>,
    ): List<String> {
        val present = obsSceneNames.toSet()
        return entries
            .filter { it.role == SceneRole.PRIMARY && it.sceneName in present }
            .map { it.sceneName }
            .distinct()
    }

    /** WARNING: PRIMARY scenes whose audio inputs failed volume/mute probing. */
    fun primaryWithBrokenAudio(
        entries: List<SceneRegistryEntry>,
        brokenProbes: Map<String, List<String>>,
    ): List<DoctorIssue> =
        entries
            .filter { it.role == SceneRole.PRIMARY }
            .mapNotNull { entry ->
                val broken = brokenProbes[entry.sceneName].orEmpty()
                if (broken.isEmpty()) {
                    null
                } else {
                    DoctorIssue(
                        severity = DoctorSeverity.WARNING,
                        checkId = "broken-audio",
                        title = "Audio source without control state",
                        detail =
                            "“${entry.sceneName}” is on the deck but " +
                                "${broken.joinToString()} " +
                                (if (broken.size == 1) "reports" else "report") +
                                " no volume/mute state.",
                        sceneName = entry.sceneName,
                    )
                }
            }
}
