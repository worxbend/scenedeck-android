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
            val walk = SceneWalk(result, visitedScenes)
            walk.scene(
                SceneFrame(
                    sceneName = activeScene,
                    path = activeScene,
                    scopeForItems = MixerScope.SCENE,
                )
            )
        }

        // 3. Optional allow-list (empty = all).
        return result.values.toList().let { inputs ->
            if (allowList.isEmpty()) inputs else inputs.filter { it.name in allowList }
        }
    }

    /** One recursion frame: the scene being walked, its display path, item scope, and depth. */
    private data class SceneFrame(
        val sceneName: String,
        val path: String,
        val scopeForItems: MixerScope,
        val depth: Int = 0,
    ) {
        fun child(sourceName: String, scope: MixerScope) =
            SceneFrame(
                sceneName = sourceName,
                path = "$path › $sourceName",
                scopeForItems = scope,
                depth = depth + 1,
            )
    }

    /**
     * Per-discovery-call traversal state (result map + recursion guard) together with the walker
     * itself; keeps the recursion parameters to just the current [SceneFrame].
     */
    private inner class SceneWalk(
        val result: LinkedHashMap<String, DiscoveredInput>,
        val visitedScenes: MutableSet<String>,
    ) {

        suspend fun scene(frame: SceneFrame) {
            if (frame.depth >= MAX_DEPTH || !visitedScenes.add(frame.sceneName)) return
            val items =
                requestResult { client.getSceneItemList(frame.sceneName) }.getOrNull() ?: return
            for (item in items) {
                if (!item.enabled) continue // prune disabled nested scenes/groups/items
                when {
                    // Groups are scenes in OBS: recurse with GROUP scope.
                    item.isGroup -> scene(frame.child(item.sourceName, MixerScope.GROUP))

                    item.inputKind != null -> addInput(item, frame)

                    // Scene source: recurse (only enabled ones reach here).
                    else -> scene(frame.child(item.sourceName, MixerScope.NESTED))
                }
            }
        }

        private suspend fun addInput(item: SceneItemInfo, frame: SceneFrame) {
            if (result.containsKey(item.sourceName)) return // dedupe by input name
            probe(item.sourceName)?.let { (mul, muted) ->
                result[item.sourceName] =
                    DiscoveredInput(
                        name = item.sourceName,
                        scope = frame.scopeForItems,
                        scopePath =
                            if (frame.scopeForItems == MixerScope.SCENE) null else frame.path,
                        volumeMul = mul,
                        muted = muted,
                        inputKind = item.inputKind,
                    )
            }
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
