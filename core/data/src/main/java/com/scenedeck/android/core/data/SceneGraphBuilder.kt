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

/** Returns the set of node names participating in at least one cycle (DFS 3-color). */
internal fun findCycleMembers(nodes: Set<String>, edges: List<SceneGraphEdge>): Set<String> {
    val adjacency = edges.groupBy { it.from }.mapValues { e -> e.value.map { it.to } }
    val cycles = mutableSetOf<String>()
    val visiting = LinkedHashSet<String>()
    val finished = mutableSetOf<String>()

    fun dfs(node: String) {
        if (node in finished) return
        if (node in visiting) {
            // Back edge: every stack member from `node` onward is on a cycle.
            cycles += visiting.dropWhile { it != node }
            cycles += node
            return
        }
        visiting += node
        adjacency[node].orEmpty().forEach(::dfs)
        visiting -= node
        finished += node
    }

    nodes.forEach(::dfs)
    return cycles
}
