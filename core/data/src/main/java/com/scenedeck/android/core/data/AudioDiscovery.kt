package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MixerScope
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.obs.ObsClient

/** One discovered audio-capable input with its curation-relevant metadata. */
data class DiscoveredInput(
    val name: String,
    val scope: MixerScope,
    /** Display path for nested/group inputs, e.g. "Starting Soon › Nested Audio". */
    val scopePath: String?,
    val volumeMul: Double,
    val muted: Boolean,
    /** OBS input kind (e.g. ffmpeg_source); null for special inputs. */
    val inputKind: String? = null,
)

/**
 * Audio discovery, ported 1:1 from the desktop rules (.kimi/skills/obs-websocket-v5): special
 * inputs → active-scene audio inputs → recurse into ENABLED nested scenes/groups → dedupe by name →
 * skip sources without volume/mute state → optional allow-list. Never throws: per-source failures
 * just skip that source.
 */
internal class AudioDiscovery(private val client: ObsClient) {

    suspend fun discover(activeScene: String?, allowList: Set<String>): List<DiscoveredInput> {
        val result = linkedMapOf<String, DiscoveredInput>()
        val visitedScenes = mutableSetOf<String>()

        // 1. Global "special" inputs first.
        requestResult { client.getSpecialInputs() }
            .getOrNull()
            ?.names
            ?.forEach { name ->
                probe(name)?.let { (mul, muted) ->
                    result[name] =
                        DiscoveredInput(
                            name,
                            MixerScope.GLOBAL,
                            scopePath = null,
                            volumeMul = mul,
                            muted = muted,
                            inputKind = null,
                        )
                }
            }

        // 2. Active-scene inputs, recursing into enabled nested scenes/groups.
        if (activeScene != null) {
            walkScene(
                sceneName = activeScene,
                path = activeScene,
                scopeForItems = MixerScope.SCENE,
                walk = Walk(result, visitedScenes),
                depth = 0,
            )
        }

        // 3. Optional allow-list (empty = all).
        return result.values.toList().let { inputs ->
            if (allowList.isEmpty()) inputs else inputs.filter { it.name in allowList }
        }
    }

    private suspend fun walkScene(
        sceneName: String,
        path: String,
        scopeForItems: MixerScope,
        walk: Walk,
        depth: Int,
    ) {
        if (depth >= MAX_DEPTH || !walk.visitedScenes.add(sceneName)) return
        val items = requestResult { client.getSceneItemList(sceneName) }.getOrNull() ?: return
        for (item in items) {
            if (!item.enabled) continue // prune disabled nested scenes/groups/items
            when {
                // Groups are scenes in OBS: recurse with GROUP scope.
                item.isGroup ->
                    walkScene(
                        sceneName = item.sourceName,
                        path = "$path › ${item.sourceName}",
                        scopeForItems = MixerScope.GROUP,
                        walk = walk,
                        depth = depth + 1,
                    )

                item.inputKind != null -> addInput(item, path, scopeForItems, walk.result)

                // Scene source: recurse (only enabled ones reach here).
                else ->
                    walkScene(
                        sceneName = item.sourceName,
                        path = "$path › ${item.sourceName}",
                        scopeForItems = MixerScope.NESTED,
                        walk = walk,
                        depth = depth + 1,
                    )
            }
        }
    }

    /** Per-discovery-call traversal state (result map + recursion guard). */
    private class Walk(
        val result: LinkedHashMap<String, DiscoveredInput>,
        val visitedScenes: MutableSet<String>,
    )

    private suspend fun addInput(
        item: SceneItemInfo,
        path: String,
        scope: MixerScope,
        result: LinkedHashMap<String, DiscoveredInput>,
    ) {
        if (result.containsKey(item.sourceName)) return // dedupe by input name
        probe(item.sourceName)?.let { (mul, muted) ->
            result[item.sourceName] =
                DiscoveredInput(
                    name = item.sourceName,
                    scope = scope,
                    scopePath = if (scope == MixerScope.SCENE) null else path,
                    volumeMul = mul,
                    muted = muted,
                    inputKind = item.inputKind,
                )
        }
    }

    /** Skip sources without volume/mute state by tolerating request failures. */
    private suspend fun probe(inputName: String): Pair<Double, Boolean>? = requestResult {
        client.getInputVolume(inputName) to client.getInputMute(inputName)
    }
        .getOrNull()

    private companion object {
        const val MAX_DEPTH = 8
    }
}
