package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Commands are deliberate single writes; confirmed state comes from the existing shared poller. */
@Singleton
class RecordingToolsRepository
@Inject
constructor(
    private val client: ObsClient,
    private val stats: StatsRepository,
    private val active: ActiveConnection,
    private val clock: MonotonicClock,
    @ApplicationScope scope: CoroutineScope,
) {
    private val lock = Any()
    private var run = 0L
    private var boundaryAt: Long? = null
    private var command: Command? = null
    private val current = MutableStateFlow(RecordingToolsState())
    val state = current.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect(::onEvent)
        }
        scope.launch { stats.telemetry.collect { refresh() } }
        scope.launch { client.connectionState.collect { refresh() } }
        scope.launch { active.target.collect { refresh() } }
        scope.launch {
            while (isActive) {
                refresh()
                delay(1_000)
            }
        }
    }

    private fun onEvent(received: ObsSessionEvent) =
        synchronized(lock) {
            val ready = client.connectionState.value as? ConnectionState.Ready
            val event = received.event as? ObsEvent.RecordStateChanged
            if (ready?.token == received.token && event?.phase?.changesRun == true) {
                run++
                boundaryAt = clock.nowMs()
                refresh()
            }
        }

    private fun origin(): RecordingOrigin? {
        val ready = client.connectionState.value as? ConnectionState.Ready ?: return null
        val target = active.target.value
        return if (ready.collectionChanging || !target.confirmed) null
        else RecordingOrigin(target.generation, ready.token, run)
    }

    private fun refresh() =
        synchronized(lock) {
            val origin = origin()
            command?.takeIf { it.origin != origin }?.owner?.cancel()
            val telemetry = stats.telemetry.value
            val ready = client.connectionState.value as? ConnectionState.Ready
            val at = telemetry.receivedAt.record
            val fresh =
                at != null &&
                    clock.nowMs() - at in 0..MAX_AGE_MS &&
                    (boundaryAt == null || at > boundaryAt!!)
            val record = telemetry.record.takeIf { fresh && telemetry.session == origin?.session }
            val old = current.value
            current.value =
                RecordingToolsState(
                    origin = origin,
                    record = record,
                    capabilities = ready?.sessionInfo?.capabilities ?: ObsCapabilities(),
                    pending = command?.takeIf { it.origin == origin }?.action,
                    busy = command != null,
                    notice = old.notice.takeIf { origin == old.origin },
                )
        }

    /** Returns false if obsolete, unsupported, inactive or another action is already pending. */
    suspend fun perform(
        origin: RecordingOrigin,
        action: RecordingAction,
        chapterName: String = "",
    ): Boolean {
        val draft = chapterName.trim()
        val owner = currentCoroutineContext().job
        val accepted =
            synchronized(lock) {
                refresh()
                if (
                    draft.length > MAX_CHAPTER_LENGTH ||
                        current.value.origin != origin ||
                        !current.value.canRun(action)
                )
                    null
                else
                    Command(origin, action, draft, owner).also {
                        command = it
                        current.value =
                            current.value.copy(pending = action, busy = true, notice = null)
                    }
            } ?: return false
        var result = RecordingResult.UNKNOWN
        try {
            result = execute(accepted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RecordingChangedException) {
            result = RecordingResult.CHANGED
        } catch (_: ObsRequestFailedException) {
            result = RecordingResult.REJECTED
        } catch (_: Exception) {
            // Transport/parser/server diagnostics and recording paths never enter UI state.
            result = RecordingResult.UNKNOWN
        } finally {
            synchronized(lock) {
                command = null
                refresh()
                if (current.value.origin == origin)
                    current.value = current.value.copy(notice = RecordingNotice(action, result))
            }
        }
        return true
    }

    private suspend fun execute(command: Command): RecordingResult =
        client.withSession(command.origin.session) {
            requireCurrent(command)
            val before = client.getRecordStatus()
            requireCurrent(command)
            val expectedPaused = command.action == RecordingAction.RESUME
            if (!before.active || before.paused != expectedPaused) throw RecordingChangedException()
            when (command.action) {
                RecordingAction.PAUSE -> client.pauseRecord()
                RecordingAction.RESUME -> client.resumeRecord()
                RecordingAction.SPLIT -> client.splitRecordFile()
                RecordingAction.CHAPTER ->
                    client.createRecordChapter(command.chapterName.ifBlank { null })
            }
            requireCurrent(command)
            if (
                command.action == RecordingAction.SPLIT || command.action == RecordingAction.CHAPTER
            )
                RecordingResult.ACCEPTED
            else confirmPause(command)
        }

    private suspend fun confirmPause(command: Command): RecordingResult {
        val after = clock.nowMs()
        val confirmed =
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
                stats.telemetry.first { sample ->
                    requireCurrent(command)
                    val at = sample.recordReadStartedAtMs
                    sample.session == command.origin.session &&
                        at != null &&
                        at > after &&
                        sample.record?.active == true &&
                        sample.record.paused == (command.action == RecordingAction.PAUSE)
                }
            }
        requireCurrent(command)
        return if (confirmed != null) RecordingResult.CONFIRMED else RecordingResult.UNKNOWN
    }

    private fun requireCurrent(command: Command) =
        synchronized(lock) {
            if (origin() != command.origin) throw RecordingChangedException()
        }

    private data class Command(
        val origin: RecordingOrigin,
        val action: RecordingAction,
        val chapterName: String,
        val owner: Job,
    )

    private class RecordingChangedException : Exception()

    companion object {
        const val MAX_CHAPTER_LENGTH = 120
        private const val MAX_AGE_MS = 3_000L
        private const val CONFIRM_TIMEOUT_MS = 3_500L
    }
}
