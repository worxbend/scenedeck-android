package com.scenedeck.android.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EphemeralPreferencesStoreTest {
    private val count = intPreferencesKey("count")

    @Test
    fun concurrentReadModifyWritesAreAtomicAndSnapshotsAreImmutable() = runTest {
        val store = EphemeralPreferencesStore()
        val draft = mutablePreferencesOf(count to 7)
        store.updateData { draft }
        val before = store.data.first()
        draft[count] = 99
        assertEquals(7, before[count])
        coroutineScope {
            repeat(100) {
                launch(Dispatchers.Default) { store.edit { it[count] = it[count]!! + 1 } }
            }
        }
        assertEquals(107, store.data.first()[count])
        assertEquals(7, before[count])
    }

    @Test
    fun cancelledOrFailedTransformsNeverCommitAndDoNotLockFutureWrites() = runTest {
        val store = EphemeralPreferencesStore()
        val job = launch {
            store.updateData {
                cancel()
                mutablePreferencesOf(count to 10)
            }
        }
        job.join()
        assertNull(store.data.first()[count])
        val failure = runCatching { store.updateData { throw CancellationException() } }
        assertEquals(CancellationException::class, failure.exceptionOrNull()!!::class)
        store.edit { it[count] = 1 }
        assertEquals(1, store.data.first()[count])
    }

    @Test
    fun everyNewSettingsGraphStartsFreshAndEditsStayInTheirOwnGraph() = runTest {
        val first = SceneDeckSettingsStore.ephemeral()
        val second = SceneDeckSettingsStore.ephemeral()
        first.setLastUsedProfileId(19)
        first.setOnboardingCompleted(true)
        first.setInputLocked("Microphone", true)
        assertEquals(19L, first.snapshot.first().lastUsedProfileId)
        assertEquals(setOf("Microphone"), first.snapshot.first().lockedInputs)
        assertNull(second.snapshot.first().lastUsedProfileId)
        assertFalse(second.snapshot.first().onboardingCompleted)
        assertEquals(emptySet<String>(), second.snapshot.first().lockedInputs)
        assertEquals(SettingsSnapshot(), SceneDeckSettingsStore.ephemeral().snapshot.first())
    }
}
