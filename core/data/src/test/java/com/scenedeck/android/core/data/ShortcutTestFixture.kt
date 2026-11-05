package com.scenedeck.android.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.database.SceneDeckDatabase
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class ShortcutTestFixture {
    private val androidContext = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName
        get() = "${javaClass.simpleName}.db"

    protected val context = MutableWorkspaceContext()
    protected val origin = TEST_WORKSPACE
    protected lateinit var database: SceneDeckDatabase
    protected lateinit var repository: ShortcutRepository
    protected var duringWrite: (() -> Unit)? = null

    @Before
    fun setupShortcuts() {
        androidContext.deleteDatabase(databaseName)
        reopen()
    }

    @After
    fun closeShortcuts() {
        database.close()
        androidContext.deleteDatabase(databaseName)
    }

    protected fun reopen() {
        database =
            Room.databaseBuilder(androidContext, SceneDeckDatabase::class.java, databaseName)
                .setQueryCallback(
                    { sql, _ ->
                        if (sql.startsWith("INSERT") && sql.contains("shortcut_bindings")) {
                            val callback = duringWrite
                            duringWrite = null
                            callback?.invoke()
                        }
                    },
                    Executor { it.run() },
                )
                .build()
        repository = ShortcutRepository(database, context)
    }

    protected fun draft(key: String = "F8", modifiers: Int = 0) =
        ShortcutDraft(
            chord = ShortcutChord(key, modifiers),
            action = ShortcutAction.Scene(" Camera "),
        )
}
