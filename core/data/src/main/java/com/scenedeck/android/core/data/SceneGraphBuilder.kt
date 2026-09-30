package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient

/** A node in the scene dependency graph. */
data class SceneGraphNode(
    val name: String,
    val role: SceneRole,
    /** Registry entry exists but the scene vanished from OBS. */
    val isStale: Boolean = false,
)

/** A parent→child dependency edge (parent scene contains child as a scene source). */
data class SceneGraphEdge(
    val from: String,
    val to: String,
    val verdict: EdgeVerdict,
)

/** Scene dependency DAG (+ detected cycles). */
data class SceneGraph(
    val nodes: List<SceneGraphNode>,
    val edges: List<SceneGraphEdge>,
    /** Scene names participating in reference cycles. */
    val cycleMembers: Set<String> = emptySet(),
)

/**
 * Builds the scene dependency graph from `GetSceneItemList` traversal (mirrors the AudioDiscovery
 * traversal; cycle-guarded). Edges are classified against [RoleRules].
 */
class SceneGraphBuilder(private val client: ObsClient) {

    suspend fun build(registryEntries: List<SceneRegistryEntry>): SceneGraph {
        val scenes = requestResult { client.getSceneList().scenes }.getOrDefault(emptyList())
        val sceneNames = scenes.map { it.name }.toSet()
        val roleOf = { name: String ->
            registryEntries.firstOrNull { it.sceneName == name }?.role ?: SceneRole.PRIMARY
        }

        val walk = EdgeWalk(roleOf)
        for (scene in scenes) {
            walk.collect(scene.name, depth = 0)
        }
        val edges: List<SceneGraphEdge> = walk.edges

        val edgeTargets = edges.flatMap { listOf(it.from, it.to) }.toSet()
        val nodes =
            (sceneNames + edgeTargets)
                .map { name ->
                    SceneGraphNode(
                        name = name,
                        role = roleOf(name),
                        isStale = name !in sceneNames,
                    )
                }
                .sortedBy { it.name }

        return SceneGraph(
            nodes = nodes,
            edges = edges,
            cycleMembers = findCycleMembers(nodes.map { it.name }.toSet(), edges),
        )
    }

    /**
     * Per-build traversal context: role lookup, collected edges, and the recursion guard. Keeping
     * them as properties reduces the recursion to just (sceneName, depth).
     */
    private inner class EdgeWalk(private val roleOf: (String) -> SceneRole) {
        val edges = mutableListOf<SceneGraphEdge>()
        private val visited = mutableSetOf<String>()

        suspend fun collect(sceneName: String, depth: Int) {
            if (depth >= MAX_DEPTH || !visited.add(sceneName)) return
            val items = requestResult { client.getSceneItemList(sceneName) }.getOrNull() ?: return
            for (item in items) {
                // Scene sources only (inputKind == null, not a group, enabled).
                if (item.inputKind != null || item.isGroup || !item.enabled) continue
                edges +=
                    SceneGraphEdge(
                        from = sceneName,
                        to = item.sourceName,
                        verdict =
                            RoleRules.classifyEdge(roleOf(sceneName), roleOf(item.sourceName)),
                    )
                collect(item.sourceName, depth + 1)
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 8
    }
}

/** Finds all cycle members via strongly connected components, including overlapping cycles. */
internal fun findCycleMembers(nodes: Set<String>, edges: List<SceneGraphEdge>): Set<String> {
    val adjacency = edges.groupBy { it.from }.mapValues { (_, list) -> list.map { it.to } }
    val reverse = edges.groupBy { it.to }.mapValues { (_, list) -> list.map { it.from } }
    val names = nodes + edges.flatMap { listOf(it.from, it.to) }
    val assigned = mutableSetOf<String>()
    val cycles = mutableSetOf<String>()
    for (node in finishOrder(names, adjacency).asReversed()) {
        if (node in assigned) continue
        val component = collectComponent(node, reverse, assigned)
        if (component.size > 1 || node in adjacency[node].orEmpty()) cycles += component
    }
    return cycles
}

/** Iterative DFS avoids stack overflow on large scene collections. */
private fun finishOrder(nodes: Set<String>, adjacency: Map<String, List<String>>): List<String> {
    val visited = mutableSetOf<String>()
    val ordered = mutableListOf<String>()
    nodes.forEach { appendFinishOrder(it, adjacency, visited, ordered) }
    return ordered
}

private fun appendFinishOrder(
    start: String,
    adjacency: Map<String, List<String>>,
    visited: MutableSet<String>,
    ordered: MutableList<String>,
) {
    val stack = ArrayDeque<Pair<String, Boolean>>()
    stack.addLast(start to false)
    while (stack.isNotEmpty()) {
        val (current, finished) = stack.removeLast()
        if (finished) {
            ordered += current
        } else if (visited.add(current)) {
            stack.addLast(current to true)
            adjacency[current].orEmpty().forEach { stack.addLast(it to false) }
        }
    }
}

private fun collectComponent(
    start: String,
    adjacency: Map<String, List<String>>,
    assigned: MutableSet<String>,
): Set<String> {
    val component = mutableSetOf<String>()
    val stack = ArrayDeque<String>()
    stack.addLast(start)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (!assigned.add(node)) continue
        component += node
        adjacency[node].orEmpty().forEach { stack.addLast(it) }
    }
    return component
}
