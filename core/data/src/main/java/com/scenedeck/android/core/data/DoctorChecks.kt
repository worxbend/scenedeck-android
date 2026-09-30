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
        val known = entries.map { it.sceneName }.toSet()
        return obsSceneNames
            .filter { it !in known }
            .map { name ->
                DoctorIssue(
                    severity = DoctorSeverity.INFO,
                    checkId = "unassigned-role",
                    title = "No role assigned",
                    detail = "“$name” has no registry entry and is treated as Primary on the deck.",
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

    /** WARNING/ERROR: edges violating the role rules (SUSPICIOUS/FORBIDDEN). */
    fun hierarchyInversions(graph: SceneGraph): List<DoctorIssue> {
        val roleOf = graph.nodes.associate { it.name to it.role }
        return graph.edges
            .filter { it.verdict != EdgeVerdict.OK }
            .map { edge ->
                val forbidden = edge.verdict == EdgeVerdict.FORBIDDEN
                DoctorIssue(
                    severity = if (forbidden) DoctorSeverity.ERROR else DoctorSeverity.WARNING,
                    checkId = "hierarchy-inversion",
                    title =
                        if (forbidden) "Forbidden scene dependency"
                        else "Surprising scene dependency",
                    detail =
                        "“${edge.from}” (${roleOf[edge.from]}) depends on " +
                            "“${edge.to}” (${roleOf[edge.to]}), which the role rules " +
                            (if (forbidden) "forbid." else "flag as suspicious."),
                    sceneName = edge.from,
                )
            }
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
                                "${broken.joinToString()} report no volume/mute state.",
                        sceneName = entry.sceneName,
                    )
                }
            }
}
