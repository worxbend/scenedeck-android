package com.scenedeck.android.core.data

import androidx.room.withTransaction
import com.scenedeck.android.core.database.PreflightConfigEntity
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PreflightRepository
@Inject
constructor(
    private val database: SceneDeckDatabase,
    val workspaces: WorkspaceContext,
) {
    private val dao = database.preflightConfigDao()
    private val retryRevision = MutableStateFlow(0L)
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<PreflightSetupState> =
        combine(workspaces.state, retryRevision) { workspace, _ -> workspace }
            .flatMapLatest { workspace ->
                val origin = (workspace as? WorkspaceState.Ready)?.session
                flow<PreflightSetupState> {
                        if (origin == null || !workspaces.isCurrent(origin)) {
                            emit(PreflightSetupState.Unavailable(workspace))
                        } else {
                            emit(PreflightSetupState.Loading(origin))
                            dao.observe(origin.key.profileId, origin.key.collectionName).collect {
                                workspaces.requireCurrent(origin)
                                emit(PreflightSetupState.Ready(origin, decode(origin, it)))
                            }
                        }
                    }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(
                            if (origin != null && workspaces.isCurrent(origin))
                                PreflightSetupState.Failed(origin)
                            else PreflightSetupState.Unavailable(workspaces.state.value)
                        )
                    }
            }

    fun retry() {
        workspaces.retry()
        retryRevision.update { it + 1 }
    }

    suspend fun snapshot(origin: WorkspaceSession): PreflightConfig {
        workspaces.requireCurrent(origin)
        val result = decode(origin, dao.get(origin.key.profileId, origin.key.collectionName))
        workspaces.requireCurrent(origin)
        return result
    }

    /**
     * Compare the full original setup inside the transaction; stale editors cannot overwrite it.
     */
    suspend fun save(origin: WorkspaceSession, original: PreflightConfig, draft: PreflightConfig) {
        val checked = draft.checked(original)
        workspaces.requireCurrent(origin)
        database.withTransaction {
            workspaces.requireCurrent(origin)
            if (snapshot(origin) != original) throw PreflightEditException()
            dao.save(
                PreflightConfigEntity(
                    origin.key.profileId,
                    origin.key.collectionName,
                    json.encodeToString(checked),
                )
            )
            currentCoroutineContext().ensureActive()
            workspaces.requireCurrent(origin)
        }
    }

    private fun decode(origin: WorkspaceSession, entity: PreflightConfigEntity?): PreflightConfig {
        if (entity == null) return PreflightConfig(origin.key.collectionName)
        if (entity.payload.length > 1_048_576) throw PreflightEditException()
        return json.decodeFromString<PreflightConfig>(entity.payload).checked()
    }
}
