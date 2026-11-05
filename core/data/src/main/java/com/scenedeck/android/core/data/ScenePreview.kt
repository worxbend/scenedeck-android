package com.scenedeck.android.core.data

import android.graphics.Bitmap
import android.os.SystemClock

/** Time of successful receipt/decode, never the failed-attempt throttle timestamp. */
data class SceneThumbnail(val bitmap: Bitmap, val capturedAtMs: Long)

data class SceneThumbnails(
    val workspace: WorkspaceSession? = null,
    val frames: Map<String, SceneThumbnail> = emptyMap(),
    val failedScenes: Set<String> = emptySet(),
)

enum class PreviewFreshness {
    FRESH,
    STALE,
    PENDING,
    UNAVAILABLE,
    DISCONNECTED,
}

data class ScenePreviewStatus(val freshness: PreviewFreshness, val ageSeconds: Long? = null)

fun previewMonotonicTimeMs(): Long = SystemClock.elapsedRealtime()

/** Only a display calculation: it never schedules or accelerates screenshot requests. */
fun scenePreviewStatus(
    capturedAtMs: Long?,
    nowMs: Long,
    failed: Boolean,
    connected: Boolean,
): ScenePreviewStatus {
    val ageMs = capturedAtMs?.let { (nowMs - it).coerceAtLeast(0L) }
    val freshness =
        when {
            !connected -> PreviewFreshness.DISCONNECTED
            ageMs == null -> if (failed) PreviewFreshness.UNAVAILABLE else PreviewFreshness.PENDING
            failed || ageMs >= STALE_PREVIEW_MS -> PreviewFreshness.STALE
            else -> PreviewFreshness.FRESH
        }
    return ScenePreviewStatus(freshness, if (connected) ageMs?.div(1_000L) else null)
}

private const val STALE_PREVIEW_MS = 20_000L
