package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.isInputWithoutAudio

/**
 * Doctor probe: for each PRIMARY scene, lists enabled audio-capable inputs whose volume probe OBS
 * itself rejected (`result: false`). Scene name → failing input names.
 *
 * Callers pass only scenes present in the current OBS scene list
 * ([DoctorChecks.primaryScenesToProbe]); a scene OBS rejects anyway (removed mid-scan) is skipped
 * rather than aborting the scan or becoming a finding. Inputs OBS reports as not audio-capable
 * (video-only color/text/image sources) are not audio sources and are never counted as broken.
 *
 * Transport-level failures (disconnect mid-probe, timeout, cancellation) propagate so the scan
 * aborts instead of producing false broken-source findings.
 */
suspend fun probeBrokenAudioInputs(
    client: ObsClient,
    primaryScenes: List<String>,
): Map<String, List<String>> {
    val broken = mutableMapOf<String, List<String>>()
    for (scene in primaryScenes.distinct()) {
        val items = sceneItemsOrNull(client, scene) ?: continue
        val failures =
            items
                .filter { it.inputKind != null && it.enabled }
                .mapNotNull { item -> probeInput(client, item.sourceName) }
        if (failures.isNotEmpty()) broken[scene] = failures
    }
    return broken
}

/** OBS rejected the scene read (e.g. it was removed mid-scan): nothing to probe, no finding. */
@Suppress("SwallowedException") // a vanished scene is not a finding; stale entries are reported
private suspend fun sceneItemsOrNull(client: ObsClient, sceneName: String): List<SceneItemInfo>? =
    try {
        client.getSceneItemList(sceneName)
    } catch (rejected: ObsRequestFailedException) {
        null
    }

/**
 * Only an OBS-level rejection of an audio-capable input marks it as broken; a "does not support
 * audio" rejection means the input is not an audio source. Anything else aborts the probe.
 */
@Suppress("SwallowedException") // the rejection itself is the finding; there is nothing to log
private suspend fun probeInput(client: ObsClient, inputName: String): String? =
    try {
        client.getInputVolume(inputName)
        null
    } catch (failed: ObsRequestFailedException) {
        if (failed.isInputWithoutAudio) null else inputName
    }
