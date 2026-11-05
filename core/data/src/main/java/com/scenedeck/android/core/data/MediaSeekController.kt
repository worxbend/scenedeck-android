package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.withSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** Single deliberate seek per input; a write acknowledgement alone is not a confirmed position. */
internal class MediaSeekController(
    private val client: ObsClient,
    private val clock: MonotonicClock,
    private val store: MediaPlaybackStore,
    private val refresh: suspend (MediaTarget) -> MediaPlayback?,
) {
    private val commands = mutableMapOf<MediaTarget, Job>()

    fun invalidate(targets: Set<MediaTarget>) =
        synchronized(store.lock) {
            commands.filterKeys { it !in targets }.values.toList().forEach { it.cancel() }
        }

    suspend fun perform(
        target: MediaTarget,
        requestedMs: Long,
        expectedDurationMs: Long?,
    ): Boolean {
        val owner = currentCoroutineContext().job
        val before =
            synchronized(store.lock) {
                eligible(target, expectedDurationMs)?.also { commands[target] = owner }
            } ?: return false
        store.publishSeek(target, true, null)
        var result = MediaSeekResult.UNKNOWN
        try {
            result = execute(target, requestedMs, before.durationMs!!)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: WorkspaceChangedException) {
            result = MediaSeekResult.CHANGED
        } catch (_: ObsRequestFailedException) {
            result = MediaSeekResult.REJECTED
        } catch (_: Exception) {
            result = MediaSeekResult.UNKNOWN
        } finally {
            synchronized(store.lock) { commands.remove(target) }
            store.publishSeek(target, false, result)
        }
        return true
    }

    private fun eligible(target: MediaTarget, expectedDurationMs: Long?): MediaPlayback? {
        val sample = store.reading(target) ?: return null
        val matches = expectedDurationMs == null || sample.durationMs == expectedDurationMs
        if (!matches && !commands.containsKey(target))
            store.publishSeek(target, false, MediaSeekResult.CHANGED)
        val available =
            supported() && !commands.containsKey(target) && sample.canSeek(clock.nowMs())
        return sample.takeIf { matches && available }
    }

    private fun supported(): Boolean =
        (client.connectionState.value as? ConnectionState.Ready)
            ?.sessionInfo
            ?.capabilities
            ?.mediaSeek == true

    private fun requireCurrent(target: MediaTarget) {
        if (!store.isCurrent(target.workspace) || store.reading(target) == null || !supported())
            throw WorkspaceChangedException()
    }

    private suspend fun execute(
        target: MediaTarget,
        requestedMs: Long,
        durationMs: Long,
    ): MediaSeekResult =
        client.withSession(target.workspace.token) {
            requireCurrent(target)
            val before = refresh(target) ?: return@withSession MediaSeekResult.UNKNOWN
            requireCurrent(target)
            if (before.durationMs != durationMs || before.cursorMs == null)
                return@withSession MediaSeekResult.CHANGED
            val bounded = requestedMs.coerceIn(0, durationMs)
            client.setMediaInputCursor(target.inputName, bounded)
            requireCurrent(target)
            val acknowledgedAt = clock.nowMs()
            val after = refresh(target) ?: return@withSession MediaSeekResult.UNKNOWN
            requireCurrent(target)
            reconcile(after, durationMs, bounded, acknowledgedAt)
        }

    private fun reconcile(
        after: MediaPlayback,
        durationMs: Long,
        bounded: Long,
        acknowledgedAt: Long,
    ): MediaSeekResult {
        val actual = after.cursorMs ?: return MediaSeekResult.UNKNOWN
        return if (after.durationMs != durationMs) MediaSeekResult.CHANGED
        else {
            val elapsed = (clock.nowMs() - acknowledgedAt).coerceIn(0, 10_000)
            val advance = if (after.status?.state == MediaStateKind.PLAYING) elapsed else 0
            val difference = actual - bounded
            if (difference in -1_000L..(1_000 + advance)) MediaSeekResult.CONFIRMED
            else MediaSeekResult.DIFFERENT
        }
    }
}
