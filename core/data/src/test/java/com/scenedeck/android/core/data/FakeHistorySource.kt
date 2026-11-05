package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeHistorySource(
    var inputs: HistoryInputs = HistoryInputs(TEST_WORKSPACE, connected = true)
) : HistorySource {
    override val events = MutableSharedFlow<ObsSessionEvent>(extraBufferCapacity = 64)
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    override fun current() = inputs

    override fun isCurrent(origin: WorkspaceSession) = inputs.workspace == origin
}

internal fun HistoryCaptureQueue.drain(): List<HistoryCommand> = buildList {
    while (true) add(commands.tryReceive().getOrNull() ?: break)
}

internal fun historyObservation() =
    HistoryObservation(
        TEST_WORKSPACE,
        HistorySeed("history", "owner", 0, TEST_WORKSPACE.key, 10_000),
        1_000,
    )
