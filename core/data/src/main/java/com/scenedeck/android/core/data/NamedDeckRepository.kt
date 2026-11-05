package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.NamedDeckDao
import com.scenedeck.android.core.database.NamedDeckEntity
import com.scenedeck.android.core.database.NamedDeckMemberEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.WorkspaceDeckSelectionEntity
import java.util.UUID
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

/** Room transactions serialize named-deck edits, including selection and complete member order. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class NamedDeckRepository
@Inject
constructor(
    private val database: SceneDeckDatabase,
    val workspaces: WorkspaceContext,
) {
    private val dao = database.namedDeckDao()
    private val revision = MutableStateFlow(0L)

    val state: Flow<NamedDeckState> =
        combine(workspaces.state, revision) { workspace, _ -> workspace }
            .flatMapLatest { workspace ->
                val origin = (workspace as? WorkspaceState.Ready)?.session
                flow<NamedDeckState> {
                        if (origin == null || !workspaces.isCurrent(origin)) {
                            emit(NamedDeckState.Unavailable(workspaces.state.value))
                        } else {
                            emit(NamedDeckState.Loading(origin))
                            dao.observeWorkspace(origin.key.profileId, origin.key.collectionName)
                                .collect { rows ->
                                    workspaces.requireCurrent(origin)
                                    emit(NamedDeckState.Ready(origin, rows.toDeckSnapshot()))
                                }
                        }
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(
                            if (origin != null && workspaces.isCurrent(origin))
                                NamedDeckState.Failed(origin)
                            else NamedDeckState.Unavailable(workspaces.state.value)
                        )
                    }
            }

    fun retry() {
        workspaces.retry()
        revision.update { it + 1 }
    }

    suspend fun snapshot(origin: WorkspaceSession): NamedDeckSnapshot {
        workspaces.requireCurrent(origin)
        val result = dao.snapshot(origin.key.profileId, origin.key.collectionName).toDeckSnapshot()
        workspaces.requireCurrent(origin)
        return result
    }

    suspend fun create(
        origin: WorkspaceSession,
        rawName: String,
        sceneNames: List<String> = emptyList(),
    ): String {
        val name = checkedDeckName(rawName)
        val members = checkedDeckMembers(sceneNames)
        return mutate(origin) { dao ->
            val key = origin.key
            if (
                dao.snapshot(key.profileId, key.collectionName).toDeckSnapshot().decks.size >=
                    MAX_NAMED_DECKS
            )
                throw DeckEditException(DeckEditFailure.TOO_MANY_DECKS)
            requireUniqueDeckName(dao, key, name)
            val id = "deck:${UUID.randomUUID()}"
            dao.insert(
                NamedDeckEntity(key.profileId, key.collectionName, id, name, name.deckNameKey())
            )
            dao.insertMembers(members.toDeckMembers(key, id))
            dao.select(WorkspaceDeckSelectionEntity(key.profileId, key.collectionName, id))
            id
        }
    }

    suspend fun rename(origin: WorkspaceSession, target: NamedDeck, rawName: String) {
        val name = checkedDeckName(rawName)
        mutate(origin) { dao ->
            val key = origin.key
            val id = target.id
            requireUnchangedDeck(dao, key, target)
            requireUniqueDeckName(dao, key, name, id)
            dao.rename(key.profileId, key.collectionName, id, name, name.deckNameKey())
        }
    }

    suspend fun delete(origin: WorkspaceSession, target: NamedDeck) =
        mutate(origin) { dao ->
            val key = origin.key
            val id = target.id
            requireUnchangedDeck(dao, key, target)
            dao.clearSelection(key.profileId, key.collectionName, id)
            dao.delete(key.profileId, key.collectionName, id)
        }

    /**
     * Selection and reordering pass the complete list; first duplicate wins, positions are dense.
     */
    suspend fun replaceMembers(
        origin: WorkspaceSession,
        target: NamedDeck,
        sceneNames: List<String>,
    ) {
        val members = checkedDeckMembers(sceneNames)
        mutate(origin) { dao ->
            val key = origin.key
            val id = target.id
            requireUnchangedDeck(dao, key, target)
            dao.clearMembers(key.profileId, key.collectionName, id)
            dao.insertMembers(members.toDeckMembers(key, id))
        }
    }

    suspend fun select(origin: WorkspaceSession, selection: DeckSelection) =
        mutate(origin) { dao ->
            val key = origin.key
            if (selection is DeckSelection.Named) requireDeck(dao, key, selection.id)
            dao.select(
                WorkspaceDeckSelectionEntity(key.profileId, key.collectionName, selection.storageId)
            )
        }

    internal suspend fun <T> mutate(
        origin: WorkspaceSession,
        action: suspend (NamedDeckDao) -> T,
    ): T {
        workspaces.requireCurrent(origin)
        val result = database.withTransaction {
            workspaces.requireCurrent(origin)
            val result = action(dao)
            currentCoroutineContext().ensureActive()
            workspaces.requireCurrent(origin)
            result
        }
        workspaces.requireCurrent(origin)
        return result
    }
}

internal suspend fun requireDeck(
    dao: NamedDeckDao,
    key: WorkspaceKey,
    id: String,
): NamedDeckEntity =
    dao.byId(key.profileId, key.collectionName, id)
        ?: throw DeckEditException(DeckEditFailure.DECK_MISSING)

internal suspend fun requireUniqueDeckName(
    dao: NamedDeckDao,
    key: WorkspaceKey,
    name: String,
    exceptId: String? = null,
) {
    val other = dao.byName(key.profileId, key.collectionName, name.deckNameKey())
    if (other != null && other.deckId != exceptId)
        throw DeckEditException(DeckEditFailure.DUPLICATE_NAME)
}

internal fun List<String>.toDeckMembers(key: WorkspaceKey, id: String) = mapIndexed { index, name ->
    NamedDeckMemberEntity(key.profileId, key.collectionName, id, name, index)
}

/** Reject a stale editor/reorder after another edit or an OBS scene rename. */
internal suspend fun requireUnchangedDeck(dao: NamedDeckDao, key: WorkspaceKey, target: NamedDeck) {
    val current =
        dao.snapshot(key.profileId, key.collectionName).toDeckSnapshot().decks.find {
            it.id == target.id
        } ?: throw DeckEditException(DeckEditFailure.DECK_MISSING)
    if (current != target) throw DeckEditException(DeckEditFailure.DECK_CHANGED)
}
