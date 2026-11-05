package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class HistoryRecorder
@Inject
constructor(
    private val repository: HistoryRepository,
    private val source: HistorySource,
    clock: MonotonicClock,
    wallClock: WallClock,
    @ApplicationScope private val scope: CoroutineScope,
) : HistoryBookmarkOrigin {
    private val started = AtomicBoolean()
    private val queue = HistoryCaptureQueue()
    private val engine = HistoryCaptureEngine(source, clock, wallClock, queue)
    val state = engine.state
    private var sourcesJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        observeSources()
        scope.launch { repository.state.collect(engine::storage) }
        scope.launch { consume() }
        scope.launch {
            queue.commands.send(
                HistoryCommand.Initialize(engine.owner, HistoryEndReason.PROCESS_ENDED)
            )
        }
        scope.launch {
            while (isActive) {
                engine.refresh()
                delay(1_000)
            }
        }
    }

    fun retry() {
        val initialize = engine.restart() ?: return
        observeSources()
        repository.retry()
        scope.launch { queue.commands.send(initialize) }
    }

    override fun capture(origin: WorkspaceSession): HistoryBookmarkLink? = engine.link(origin)

    private fun observeSources() {
        sourcesJob?.cancel()
        val epoch = engine.owner
        sourcesJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    requestResult { source.events.collect { engine.refresh(it, epoch) } }
                    engine.fail(epoch, HistoryCaptureFailure.SOURCE_ENDED)
                }
                launch {
                    requestResult { source.changes.collect { engine.refresh(epoch = epoch) } }
                    engine.fail(epoch, HistoryCaptureFailure.SOURCE_ENDED)
                }
            }
    }

    private suspend fun consume() {
        for (command in queue.commands) {
            if (!engine.accepts(command.owner)) continue
            requestResult { execute(command) }
                .onFailure { engine.fail(command.owner, HistoryCaptureFailure.STORAGE) }
        }
    }

    private suspend fun execute(command: HistoryCommand) {
        val available =
            when (command) {
                is HistoryCommand.Initialize -> {
                    repository.recover(command.owner, command.reason)
                    engine.activate(command.owner)
                    true
                }
                is HistoryCommand.Start -> repository.start(command.seed)
                is HistoryCommand.Append ->
                    repository.append(command.write) != HistoryAppendResult.UNAVAILABLE
                is HistoryCommand.Touch -> repository.touch(command.position)
                is HistoryCommand.Finish -> repository.finish(command.position, command.reason)
            }
        engine.completed(command, available)
    }
}
