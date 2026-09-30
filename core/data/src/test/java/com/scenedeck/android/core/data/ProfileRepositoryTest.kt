package com.scenedeck.android.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {

    private lateinit var database: SceneDeckDatabase
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SceneDeckDatabase::class.java,
                )
                .build()
        repository = ProfileRepository(database.connectionProfileDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addAndObserve(): Unit = runBlocking {
        repository.add("Home PC", "192.168.1.20", 4455)
        repository.add("Studio rig", "10.0.0.5", 4456)

        repository.profiles.test {
            val profiles = awaitItem()
            assertEquals(2, profiles.size)
            assertEquals(setOf("Home PC", "Studio rig"), profiles.map { it.name }.toSet())
            val home = profiles.first { it.name == "Home PC" }
            assertEquals("192.168.1.20", home.host)
            assertEquals(4455, home.port)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun markUsedDrivesLastUsedAndOrdering(): Unit = runBlocking {
        val first = repository.add("A", "10.0.0.1", 4455)
        val second = repository.add("B", "10.0.0.2", 4455)

        repository.markUsed(first)
        assertEquals(first, repository.lastUsed()?.id)

        repository.markUsed(second)
        assertEquals(second, repository.lastUsed()?.id)

        repository.profiles.test {
            // Most-recently-used first.
            assertEquals(listOf("B", "A"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateChangesFields(): Unit = runBlocking {
        val id = repository.add("Old", "10.0.0.1", 4455)
        val existing = repository.byId(id)!!

        repository.update(existing.copy(name = "New", host = "studio.local", port = 4456))

        val updated = repository.byId(id)!!
        assertEquals("New", updated.name)
        assertEquals("studio.local", updated.host)
        assertEquals(4456, updated.port)
    }

    @Test
    fun deleteRemovesProfile(): Unit = runBlocking {
        val id = repository.add("Doomed", "10.0.0.1", 4455)
        repository.delete(id)
        assertNull(repository.byId(id))
        repository.profiles.test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
