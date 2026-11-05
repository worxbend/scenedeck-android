package com.scenedeck.android.feature.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.HistoryAppend
import com.scenedeck.android.core.data.HistoryInputs
import com.scenedeck.android.core.data.HistoryPayload
import com.scenedeck.android.core.data.HistoryRecorder
import com.scenedeck.android.core.data.HistoryRepository
import com.scenedeck.android.core.data.HistorySeed
import com.scenedeck.android.core.data.HistorySource
import com.scenedeck.android.core.data.HistoryState
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.database.BookmarkEntity
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.model.ObsSessionEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val seed = HistorySeed("selected", "owner", 0, WorkspaceKey(7, "Studio"), 1_000)

    @Test
    fun exportUsesThePreparedSelectionDespiteLaterWritesAndDeletion() = runTest {
        withHistory {
            repository.start(seed)
            repository.append(
                HistoryAppend(
                    seed.id,
                    seed.ownerId,
                    1,
                    1_100,
                    100,
                    HistoryPayload.Program("Original scene", true),
                )
            )
            model.export(seed.id)
            model.export(null)
            model.uiState.first { it.export.request != null }
            model.pickerOpened()
            repository.append(
                HistoryAppend(
                    seed.id,
                    seed.ownerId,
                    2,
                    1_200,
                    200,
                    HistoryPayload.Program("Later scene", false),
                )
            )
            repository.delete(seed.id)
            model.select(null)
            val file = File.createTempFile("history-export", ".json", context.cacheDir)
            try {
                val uri = Uri.fromFile(file)
                model.fileResult(uri, context.contentResolver)
                model.fileResult(uri, context.contentResolver)
                model.uiState.first {
                    !it.busy && it.operation.message == "History exported to the selected document."
                }
                val observation =
                    Json.parseToJsonElement(file.readText())
                        .jsonObject
                        .getValue("observations")
                        .jsonArray
                        .single()
                        .jsonObject
                assertEquals(seed.id, observation.getValue("id").jsonPrimitive.content)
                assertEquals(1, observation.getValue("events").jsonArray.size)
                assertTrue(file.readText().contains("Original scene"))
                assertFalse(file.readText().contains("Later scene"))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun canceledPickerAndExpiredResultDoNotChangeDataOrWriteAFile() = runTest {
        withHistory {
            repository.start(seed)
            model.export(null)
            model.uiState.first { it.export.request != null }
            model.fileResult(null, context.contentResolver)
            model.uiState.first { !it.busy }
            assertEquals(1, repository.exportSnapshot().observations.size)
            val file = File.createTempFile("history-expired", ".json", context.cacheDir)
            try {
                file.writeText("unchanged")
                model.fileResult(Uri.fromFile(file), context.contentResolver)
                model.uiState.first { it.operation.message?.contains("expired") == true }
                assertEquals("unchanged", file.readText())
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun documentWriteFailureIsSanitizedAndCanPrepareAnotherExport() = runTest {
        withHistory {
            model.export(null)
            model.uiState.first { it.export.request != null }
            model.fileResult(
                Uri.parse("content://missing.provider/private-file"),
                context.contentResolver,
            )
            val failed =
                model.uiState.first {
                    !it.busy && it.operation.message?.startsWith("Couldn't write") == true
                }
            assertFalse(requireNotNull(failed.operation.message).contains("private-file"))
            model.export(null)
            model.uiState.first { it.export.request != null }
            model.pickerFailed()
            model.uiState.first {
                !it.busy && it.operation.message?.startsWith("Couldn't open") == true
            }
        }
    }

    @Test
    fun settingsGateDuplicateWritesAndClearKeepsIndependentBookmarks() = runTest {
        withHistory {
            repository.start(seed)
            database
                .bookmarkDao()
                .insert(
                    BookmarkEntity(
                        bookmarkId = "mark",
                        connectionProfileId = 7,
                        sceneCollectionName = "Studio",
                        observationId = "legacy",
                        createdAtMs = 1_000,
                        label = "Keep",
                        times = "{}",
                    )
                )
            model.configure(false, 10)
            model.configure(true, 100)
            model.uiState.first {
                !it.busy && (it.saved as? HistoryState.Ready)?.preferences?.sessionLimit == 10
            }
            assertFalse(repository.exportSnapshot().preferences.enabled)
            model.select(seed.id)
            model.delete(null)
            model.uiState.first {
                !it.busy &&
                    it.selection.id == null &&
                    (it.saved as? HistoryState.Ready)?.sessions?.isEmpty() == true
            }
            assertEquals("Keep", repository.exportSnapshot().unlinkedBookmarks.single().label)
        }
    }

    @Test
    fun failedSettingWriteKeepsSavedValuesAndExcludesDatabaseDetails() = runTest {
        withHistory {
            repository.configure(true, 30)
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_setting BEFORE UPDATE ON history_preferences " +
                    "BEGIN SELECT RAISE(ABORT, 'private database detail'); END"
            )
            model.configure(false, 10)
            val failed =
                model.uiState.first {
                    !it.busy && it.operation.message?.startsWith("Couldn't update") == true
                }
            assertFalse(requireNotNull(failed.operation.message).contains("private database"))
            assertTrue(repository.exportSnapshot().preferences.enabled)
            assertEquals(30, repository.exportSnapshot().preferences.sessionLimit)
        }
    }

    private suspend fun TestScope.withHistory(block: suspend HistoryModelFixture.() -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, SceneDeckDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val repository = HistoryRepository(database)
            val source =
                object : HistorySource {
                    override val events = MutableSharedFlow<ObsSessionEvent>()
                    override val changes = MutableSharedFlow<Unit>()

                    override fun current() = HistoryInputs(null)

                    override fun isCurrent(origin: WorkspaceSession) = false
                }
            val recorder =
                HistoryRecorder(repository, source, { 1_000 }, { 2_000 }, backgroundScope)
            val model =
                HistoryViewModel(
                    SavedStateHandle(),
                    repository,
                    recorder,
                    ProfileRepository(database.connectionProfileDao()),
                    { 2_000 },
                )
            store.put("history", model)
            backgroundScope.launch { model.uiState.collect() }
            model.uiState.first { it.saved is HistoryState.Ready }
            block(HistoryModelFixture(model, repository, database, context))
        } finally {
            store.clear()
            runCurrent()
            database.close()
            Dispatchers.resetMain()
        }
    }
}

private data class HistoryModelFixture(
    val model: HistoryViewModel,
    val repository: HistoryRepository,
    val database: SceneDeckDatabase,
    val context: Context,
)
