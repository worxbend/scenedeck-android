package com.scenedeck.android.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class HistoryTestFixture {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName
        get() = "${javaClass.simpleName}.db"

    protected lateinit var database: SceneDeckDatabase
    protected lateinit var repository: HistoryRepository

    @Before
    fun setupHistory() {
        context.deleteDatabase(databaseName)
        openHistory()
    }

    @After
    fun closeHistory() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    protected fun openHistory() {
        database =
            Room.databaseBuilder(context, SceneDeckDatabase::class.java, databaseName).build()
        repository = HistoryRepository(database)
    }

    protected fun write(
        ordinal: Long,
        id: String = SEED.id,
        payload: HistoryPayload = HistoryPayload.Program("Camera", false),
    ) = HistoryAppend(id, SEED.ownerId, ordinal, 10_000 - ordinal, ordinal * 100, payload)

    protected companion object {
        val SEED = HistorySeed("observation", "process-a", 0, WorkspaceKey(7, "Studio"), 10_000)
    }
}
