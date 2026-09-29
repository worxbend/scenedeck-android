package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient

/**
 * Doctor probe: for each PRIMARY scene, lists enabled inputs that fail the
 * volume probe (no control state). Scene name → failing input names.
 */
suspend fun probeBrokenAudioInputs(
    client: ObsClient,
    primaryScenes: List<String>,
): Map<String, List<String>> {
    val broken = mutableMapOf<String, List<String>>()
    for (scene in primaryScenes) {
        val items = runCatching { client.getSceneItemList(scene) }.getOrNull() ?: continue
        val failures = items
            .filter { it.inputKind != null && it.enabled }
            .mapNotNull { item ->
                runCatching { client.getInputVolume(item.sourceName) }
                    .getOrNull()
                    ?.let { null }
                    ?: item.sourceName
            }
        if (failures.isNotEmpty()) broken[scene] = failures
    }
    return broken
}
