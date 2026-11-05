package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus

data class MediaTarget(val workspace: WorkspaceSession, val inputName: String, val revision: Long)

data class MediaSeekIntent(val target: MediaTarget, val durationMs: Long, val cursorMs: Long)

enum class MediaSeekResult {
    CONFIRMED,
    DIFFERENT,
    REJECTED,
    UNKNOWN,
    CHANGED,
}

data class MediaPlayback(
    val target: MediaTarget,
    val status: MediaStatus? = null,
    val receivedAtMs: Long? = null,
    val seeking: Boolean = false,
    val result: MediaSeekResult? = null,
) {
    val durationMs: Long?
        get() = status?.durationMs?.takeIf { it > 0 && status.hasPosition() }

    val cursorMs: Long?
        get() =
            status?.cursorMs?.takeIf {
                it >= 0 && status.hasPosition() && (durationMs == null || it <= durationMs!!)
            }

    val remainingMs: Long?
        get() = durationMs?.let { duration -> cursorMs?.let { duration - it } }

    fun isFresh(nowMs: Long): Boolean =
        receivedAtMs?.let { nowMs - it in 0..MAX_MEDIA_AGE_MS } == true

    fun canSeek(nowMs: Long): Boolean =
        !seeking && isFresh(nowMs) && durationMs != null && cursorMs != null
}

data class MixerMediaStatus(
    val workspace: WorkspaceSession? = null,
    val readings: Map<String, MediaPlayback> = emptyMap(),
    val seekSupported: Boolean = false,
) {
    /** Compatibility projection for the compact transport; only fresh successful reads appear. */
    val inputs: Map<String, MediaStatus>
        get() =
            readings.mapNotNull { (name, reading) -> reading.status?.let { name to it } }.toMap()
}

internal data class MediaSources(val workspace: WorkspaceSession?, val names: Set<String>)

internal const val MAX_MEDIA_AGE_MS = 5_000L
internal const val MEDIA_POLL_MS = 2_000L
internal val MEDIA_INPUT_KINDS = setOf("ffmpeg_source", "vlc_source")

private fun MediaStatus.hasPosition() =
    state == MediaStateKind.PLAYING || state == MediaStateKind.PAUSED
