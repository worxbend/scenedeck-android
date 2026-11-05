package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.SceneDeckDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/** Serialized, scoped local edits. It never makes an OBS request or runs a stored definition. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PresetRepository
@Inject
constructor(private val database: SceneDeckDatabase, val workspaces: WorkspaceContext) {
    private val dao = database.actionPresetDao()
    private val retryRevision = MutableStateFlow(0L)

    val state: Flow<PresetState> =
        combine(workspaces.state, retryRevision) { workspace, _ -> workspace }
            .flatMapLatest { workspace -> observe(workspace) }

    private fun observe(workspace: WorkspaceState): Flow<PresetState> {
        val origin = (workspace as? WorkspaceState.Ready)?.session
        return flow<PresetState> {
                if (origin == null || !workspaces.isCurrent(origin)) {
                    emit(PresetState.Unavailable(workspace))
                } else {
                    emit(PresetState.Loading(origin))
                    dao.observe(
                            origin.key.profileId,
                            origin.key.collectionName,
                            MAX_ACTION_PRESETS + 1,
                        )
                        .collect { rows ->
                            workspaces.requireCurrent(origin)
                            emit(PresetState.Ready(origin, rows.decodePresets()))
                        }
                }
            }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(
                    if (origin != null && workspaces.isCurrent(origin)) PresetState.Failed(origin)
                    else PresetState.Unavailable(workspaces.state.value)
                )
            }
    }

    fun retry() {
        workspaces.retry()
        retryRevision.update { it + 1 }
    }

    suspend fun snapshot(origin: WorkspaceSession): List<ActionPreset> {
        workspaces.requireCurrent(origin)
        val presets =
            dao.list(origin.key.profileId, origin.key.collectionName, MAX_ACTION_PRESETS + 1)
                .decodePresets()
        workspaces.requireCurrent(origin)
        return presets
    }

    /** Full comparison rejects stale editors, even if another edit restored the old text. */
    suspend fun save(
        origin: WorkspaceSession,
        original: ActionPreset?,
        draft: PresetDraft,
    ): ActionPreset {
        val checked = draft.checked()
        requirePreset(
            original == null || (original.workspace == origin.key && original.id == draft.id)
        )
        return mutate(origin) {
            val all = snapshot(origin)
            val current = all.find { it.id == checked.id }
            val saved = replacement(origin.key, original, current, checked)
            requirePreset(current != null || all.size < MAX_ACTION_PRESETS, PresetEditFailure.LIMIT)
            requirePreset(
                all.none {
                    it.id != saved.id && it.name.presetNameKey() == saved.name.presetNameKey()
                },
                PresetEditFailure.DUPLICATE_NAME,
            )
            dao.save(saved.encodePreset())
            saved
        }
    }

    suspend fun delete(origin: WorkspaceSession, target: ActionPreset) {
        requirePreset(target.workspace == origin.key)
        mutate(origin) {
            requirePreset(
                snapshot(origin).find { it.id == target.id } == target,
                PresetEditFailure.CHANGED,
            )
            dao.delete(origin.key.profileId, origin.key.collectionName, target.id)
        }
    }

    private suspend fun <T> mutate(origin: WorkspaceSession, action: suspend () -> T): T {
        workspaces.requireCurrent(origin)
        val result = database.withTransaction {
            workspaces.requireCurrent(origin)
            val result = action()
            currentCoroutineContext().ensureActive()
            workspaces.requireCurrent(origin)
            result
        }
        workspaces.requireCurrent(origin)
        return result
    }
}

private fun replacement(
    workspace: WorkspaceKey,
    original: ActionPreset?,
    current: ActionPreset?,
    draft: PresetDraft,
): ActionPreset {
    if (original == null && current != null) {
        requirePreset(current.revision == 0L && current.draft() == draft, PresetEditFailure.CHANGED)
        return current
    }
    requirePreset(current == original, PresetEditFailure.CHANGED)
    val revision =
        when {
            current == null -> 0L
            current.draft() == draft -> current.revision
            else -> Math.addExact(current.revision, 1)
        }
    return ActionPreset(workspace, draft.id, draft.name, revision, draft.steps)
}
