package com.scenedeck.android.navigation

import com.scenedeck.android.background.SceneSwitchResult

/**
 * User-facing feedback for a `scenedeck://scene/{name}` link. Pure (unit-tested): success stays
 * silent because the deck itself shows the new program scene; failures get a short, sanitized
 * message that never includes OBS server comments or transport details.
 */
object SceneLinkFeedback {
    private const val MAX_NAME_LENGTH = 40

    fun messageFor(result: SceneSwitchResult, sceneName: String): String? =
        when (result) {
            SceneSwitchResult.Success -> null
            SceneSwitchResult.NoProfile ->
                "Can't switch scenes yet: add an OBS connection in SceneDeck first."
            SceneSwitchResult.NotConnected ->
                "Couldn't reach OBS to switch to “${displayName(sceneName)}”."
            SceneSwitchResult.RequestFailed ->
                "OBS couldn't switch to “${displayName(sceneName)}”. Check that the scene exists."
        }

    /** Link-supplied names are untrusted: drop control characters and cap the length. */
    internal fun displayName(sceneName: String): String {
        val printable = sceneName.filterNot { it.isISOControl() }.trim()
        return if (printable.length > MAX_NAME_LENGTH) {
            printable.take(MAX_NAME_LENGTH - 1).trimEnd() + "…"
        } else {
            printable
        }
    }
}
