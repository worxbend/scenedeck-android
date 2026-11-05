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

/** Configuration only: editing, enabling or observing a binding never invokes its action. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutRepository
@Inject
constructor(private val database: SceneDeckDatabase, val workspaces: WorkspaceContext) {
    private val dao = database.shortcutBindingDao()
    private val retryRevision = MutableStateFlow(0L)

    val state: Flow<ShortcutState> =
        combine(workspaces.state, retryRevision) { workspace, _ -> workspace }
            .flatMapLatest { workspace -> observe(workspace) }

    private fun observe(workspace: WorkspaceState): Flow<ShortcutState> {
        val origin = (workspace as? WorkspaceState.Ready)?.session
        return flow<ShortcutState> {
                if (origin == null || !workspaces.isCurrent(origin)) {
                    emit(ShortcutState.Unavailable(workspace))
                } else {
                    emit(ShortcutState.Loading(origin))
                    dao.observe(
                            origin.key.profileId,
                            origin.key.collectionName,
                            MAX_SHORTCUT_BINDINGS + 1,
                        )
                        .collect { rows ->
                            workspaces.requireCurrent(origin)
                            emit(ShortcutState.Ready(origin, rows.decodeShortcuts()))
                        }
                }
            }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(
                    if (origin != null && workspaces.isCurrent(origin)) ShortcutState.Failed(origin)
                    else ShortcutState.Unavailable(workspaces.state.value)
                )
            }
    }

    fun retry() {
        workspaces.retry()
        retryRevision.update { it + 1 }
    }

    suspend fun snapshot(origin: WorkspaceSession): List<ShortcutBinding> {
        workspaces.requireCurrent(origin)
        val bindings =
            dao.list(origin.key.profileId, origin.key.collectionName, MAX_SHORTCUT_BINDINGS + 1)
                .decodeShortcuts()
        workspaces.requireCurrent(origin)
        return bindings
    }

    suspend fun save(
        origin: WorkspaceSession,
        original: ShortcutBinding?,
        draft: ShortcutDraft,
    ): ShortcutBinding {
        val checked = draft.checked()
        requireShortcut(
            original == null || (original.workspace == origin.key && original.id == draft.id)
        )
        return mutate(origin) {
            val all = snapshot(origin)
            val current = all.find { it.id == checked.id }
            val saved = replacement(origin.key, original, current, checked)
            requireShortcut(
                current != null || all.size < MAX_SHORTCUT_BINDINGS,
                ShortcutEditFailure.LIMIT,
            )
            requireShortcut(
                all.none { it.id != saved.id && it.chord == saved.chord },
                ShortcutEditFailure.CHORD_IN_USE,
            )
            dao.save(saved.encodeShortcut())
            saved
        }
    }

    suspend fun delete(origin: WorkspaceSession, target: ShortcutBinding) {
        requireShortcut(target.workspace == origin.key)
        mutate(origin) {
            requireShortcut(
                snapshot(origin).find { it.id == target.id } == target,
                ShortcutEditFailure.CHANGED,
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
    original: ShortcutBinding?,
    current: ShortcutBinding?,
    draft: ShortcutDraft,
): ShortcutBinding {
    if (original == null && current != null) {
        requireShortcut(
            current.revision == 0L && current.draft() == draft,
            ShortcutEditFailure.CHANGED,
        )
        return current
    }
    requireShortcut(current == original, ShortcutEditFailure.CHANGED)
    val revision =
        when {
            current == null -> 0L
            current.draft() == draft -> current.revision
            else -> Math.addExact(current.revision, 1)
        }
    return ShortcutBinding(
        workspace,
        draft.id,
        revision,
        requireNotNull(draft.chord),
        requireNotNull(draft.action),
        draft.enabled,
    )
}
