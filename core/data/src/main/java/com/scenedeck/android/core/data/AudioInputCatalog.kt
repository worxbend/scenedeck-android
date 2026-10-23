package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject

/**
 * Lists the audio inputs a user can put on the Mixer allow-list (FEATURE_SPEC §9). Read-only: it
 * uses the same discovery rules as the Mixer but across every scene and without the allow-list.
 */
class AudioInputCatalog @Inject constructor(private val client: ObsClient) {
    private val discovery = AudioDiscovery(client)

    /** Sorted audio input names; empty while disconnected or when the scene list fails. */
    suspend fun audioInputNames(): List<String> {
        val scenes =
            if (client.connectionState.value is ConnectionState.Ready) {
                requestResult { client.getSceneList().scenes }.getOrNull()
            } else {
                null
            } ?: return emptyList()
        return discovery
            .discoverAll(scenes.map { it.name })
            .map { it.name }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}

/** Adds or removes [inputName] from an allow-list; an emptied list means "show every input". */
fun Set<String>.withAudioInputAllowed(inputName: String, allowed: Boolean): Set<String> =
    if (allowed) this + inputName else this - inputName
