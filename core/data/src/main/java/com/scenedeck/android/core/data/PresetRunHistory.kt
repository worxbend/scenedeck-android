package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.HistoryPreferencesEntity
import com.scenedeck.android.core.database.HistoryPresetEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

const val MAX_HISTORY_PRESET_RUNS = 50

@Serializable data class HistoryPresetRun(val elapsedMs: Long, val run: PresetRun)

interface PresetRunHistory {
    fun capture(origin: WorkspaceSession): HistoryBookmarkLink?

    suspend fun start(
        workspace: WorkspaceKey,
        link: HistoryBookmarkLink?,
        run: PresetRun,
    ): PresetHistoryStatus

    suspend fun save(link: HistoryBookmarkLink, run: PresetRun): PresetHistoryStatus
}

/** Updates never insert. Closed parents accept existing outcomes; deleted parents stay deleted. */
@Singleton
class StoredPresetRunHistory
@Inject
constructor(
    private val database: SceneDeckDatabase,
    private val process: LocalProcessIdentity,
    private val origins: HistoryBookmarkOrigin,
) : PresetRunHistory {
    private val runs = database.historyPresetDao()

    override fun capture(origin: WorkspaceSession) = origins.capture(origin)

    override suspend fun start(
        workspace: WorkspaceKey,
        link: HistoryBookmarkLink?,
        run: PresetRun,
    ): PresetHistoryStatus {
        if (link == null) return PresetHistoryStatus.NOT_RECORDED
        return database.withTransaction { startCurrent(workspace, link, run) }
    }

    private suspend fun startCurrent(
        workspace: WorkspaceKey,
        link: HistoryBookmarkLink,
        run: PresetRun,
    ): PresetHistoryStatus {
        val payload = run.encodeRun()
        checkHistory(
            run.revision == 0L &&
                run.outcome == PresetRunOutcome.RUNNING &&
                run.steps.all { it.result == PresetStepResult.PENDING }
        )
        checkHistory(link.elapsedMs >= 0)
        val row =
            HistoryPresetEntity(
                run.id,
                link.sessionId,
                process.id,
                link.elapsedMs,
                run.revision,
                true,
                payload,
            )
        val existing = runs.get(run.id)
        if (existing != null) {
            checkHistory(existing == row)
            return PresetHistoryStatus.SAVED
        }
        val status = insertionStatus(workspace, link)
        if (status == PresetHistoryStatus.SAVED) runs.insert(row)
        return status
    }

    private suspend fun insertionStatus(
        workspace: WorkspaceKey,
        link: HistoryBookmarkLink,
    ): PresetHistoryStatus {
        val parent =
            database.historySessionDao().get(link.sessionId) ?: return PresetHistoryStatus.REMOVED
        val session = parent.decodeHistory()
        checkHistory(session.workspace == workspace)
        val prefs =
            (database.historyPreferencesDao().get() ?: HistoryPreferencesEntity()).decodeHistory()
        checkHistory(session.endedAtMs == null || link.elapsedMs <= session.elapsedMs)
        val count = runs.list(link.sessionId, MAX_HISTORY_PRESET_RUNS + 1).size
        checkHistory(count <= MAX_HISTORY_PRESET_RUNS)
        return when {
            !prefs.enabled || session.endReason !in allowedStartReasons ->
                PresetHistoryStatus.NOT_RECORDED
            count == MAX_HISTORY_PRESET_RUNS -> PresetHistoryStatus.LIMIT_REACHED
            else -> PresetHistoryStatus.SAVED
        }
    }

    override suspend fun save(link: HistoryBookmarkLink, run: PresetRun): PresetHistoryStatus =
        database.withTransaction {
            val payload = run.encodeRun()
            val row = runs.get(run.id) ?: return@withTransaction PresetHistoryStatus.REMOVED
            checkHistory(
                row.sessionId == link.sessionId &&
                    row.elapsedMs == link.elapsedMs &&
                    row.processId == process.id
            )
            val previous = row.decodeRun().run
            if (previous == run) return@withTransaction PresetHistoryStatus.SAVED
            run.requireSuccessor(previous)
            runs.update(
                row.copy(
                    payload = payload,
                    revision = run.revision,
                    running = run.outcome == PresetRunOutcome.RUNNING,
                )
            )
            PresetHistoryStatus.SAVED
        }
}

private val allowedStartReasons =
    setOf(null, HistoryEndReason.DISCONNECTED, HistoryEndReason.WORKSPACE_CHANGED)

/** Called in History recovery's transaction, using process identity rather than capture owner. */
internal suspend fun SceneDeckDatabase.recoverPresetRuns(processId: String) {
    val limit = MAX_HISTORY_SESSIONS * MAX_HISTORY_PRESET_RUNS
    val dao = historyPresetDao()
    val rows = dao.interrupted(processId, limit + 1)
    checkHistory(rows.size <= limit)
    rows.forEach { row ->
        val interrupted =
            row.decodeRun()
                .run
                .finish(PresetRunOutcome.INTERRUPTED, PresetStopReason.PROCESS_ENDED, null)
        dao.update(
            row.copy(
                payload = interrupted.encodeRun(),
                revision = interrupted.revision,
                running = false,
            )
        )
    }
}

internal fun HistoryDetail.withPresetRuns(rows: List<HistoryPresetEntity>): HistoryDetail {
    checkHistory(rows.size <= MAX_HISTORY_PRESET_RUNS)
    val runs = rows.map { it.decodeRun() }
    runs.forEach { checkHistory(session.endedAtMs == null || it.elapsedMs <= session.elapsedMs) }
    val latest = runs.maxByOrNull { it.elapsedMs }
    val observed =
        if (latest != null && latest.elapsedMs > session.elapsedMs)
            session.copy(elapsedMs = latest.elapsedMs, lastObservedAtMs = latest.run.startedAtMs)
        else session
    return copy(session = observed, presetRuns = runs)
}
