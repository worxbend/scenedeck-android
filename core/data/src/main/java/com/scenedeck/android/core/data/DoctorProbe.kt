package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException

/**
 * Doctor probe: for each PRIMARY scene, lists enabled inputs whose volume probe OBS itself rejected
 * (`result: false`). Scene name → failing input names.
 *
 * Transport-level failures (disconnect mid-probe, cancellation) propagate so the scan aborts
 * instead of producing false broken-source findings.
 */
suspend fun probeBrokenAudioInputs(
    client: ObsClient,
    primaryScenes: List<String>,
): Map<String, List<String>> {
    val broken = mutableMapOf<String, List<String>>()
    for (scene in primaryScenes) {
        val items = client.getSceneItemList(scene)
        val failures =
            items
                .filter { it.inputKind != null && it.enabled }
                .mapNotNull { item -> probeInput(client, item.sourceName) }
        if (failures.isNotEmpty()) broken[scene] = failures
    }
    return broken
}

/** Only an OBS-level request failure marks an input as broken; anything else aborts the probe. */
@Suppress("SwallowedException") // the rejection itself is the finding; there is nothing to log
private suspend fun probeInput(client: ObsClient, inputName: String): String? =
    try {
        client.getInputVolume(inputName)
        null
    } catch (failed: ObsRequestFailedException) {
        inputName
    }
