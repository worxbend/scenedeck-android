package com.scenedeck.android.navigation

import java.net.URI

/**
 * Parses `scenedeck://scene/{name}` automation deep links (Tasker etc.). Pure JVM (java.net.URI
 * decodes percent-encoding) so the rules are unit-testable; MainActivity feeds it
 * `intent.dataString`.
 */
object SceneLinkParser {
    const val SCHEME = "scenedeck"
    const val HOST_SCENE = "scene"

    /** Returns the decoded scene name, or null when the link is not a scene link. */
    fun sceneName(link: String?): String? =
        link
            ?.takeUnless { it.isBlank() }
            ?.let { runCatching { URI(it) }.getOrNull() }
            ?.takeIf {
                it.scheme == SCHEME &&
                    it.host == HOST_SCENE &&
                    it.rawUserInfo == null &&
                    it.port == -1
            }
            ?.path
            ?.removePrefix("/")
            ?.takeIf { it.isNotBlank() && !it.contains('\u0000') }
}
