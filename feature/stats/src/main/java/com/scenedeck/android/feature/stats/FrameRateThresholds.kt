package com.scenedeck.android.feature.stats

import androidx.compose.runtime.Immutable

/**
 * FPS and render-time gauge thresholds derived from the OBS canvas frame rate [targetFps] (30,
 * 59.94, ...), so a healthy 30 fps studio is not judged against a 60 fps target.
 * - FPS (falling: low is bad): max = target, warn = 90 %, crit = 75 % of target.
 * - Average render time vs the frame budget `1000 / targetFps` ms: warn 50 %, crit 90 %.
 */
@Immutable
data class FrameRateThresholds(val targetFps: Float) {
    val fpsMax: Float
        get() = targetFps

    val fpsWarn: Float
        get() = targetFps * FPS_WARN_RATIO

    val fpsCrit: Float
        get() = targetFps * FPS_CRIT_RATIO

    val frameBudgetMs: Float
        get() = MS_PER_SECOND / targetFps

    val renderWarnMs: Float
        get() = frameBudgetMs * RENDER_WARN_RATIO

    val renderCritMs: Float
        get() = frameBudgetMs * RENDER_CRIT_RATIO

    val renderMaxMs: Float
        get() = frameBudgetMs * RENDER_MAX_RATIO

    companion object {
        private const val MS_PER_SECOND = 1000f
        private const val FPS_WARN_RATIO = 0.9f
        private const val FPS_CRIT_RATIO = 0.75f
        private const val RENDER_WARN_RATIO = 0.5f
        private const val RENDER_CRIT_RATIO = 0.9f
        private const val RENDER_MAX_RATIO = 1.2f

        /** Thresholds for the reported canvas rate; 60 fps only when unknown or invalid. */
        fun forTargetFps(targetFps: Float?): FrameRateThresholds =
            FrameRateThresholds(
                targetFps?.takeIf { it.isFinite() && it > 0f }
                    ?: StatsThresholds.FALLBACK_TARGET_FPS
            )
    }
}

/** Shows the canvas target once OBS reports it, e.g. "Frame rate · target 29.97 fps". */
internal fun frameRateTitle(targetFps: Float?): String =
    if (targetFps == null) {
        "Frame rate"
    } else {
        "Frame rate · target %s fps".format(formatFps(targetFps))
    }

private fun formatFps(fps: Float): String =
    if (fps % 1f == 0f) "%.0f".format(fps) else "%.2f".format(fps)
