package com.jwoglom.controlx2.db.nightscout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDateTime

/**
 * Instrumented tests for NightscoutSyncState database operations
 */
@RunWith(AndroidJUnit4::class)
class NightscoutSyncStateDatabaseTest {
    private lateinit var syncStateDao: NightscoutSyncStateDao
    private lateinit var db: NightscoutSyncStateDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, NightscoutSyncStateDatabase::class.java
        ).build()
        syncStateDao = db.nightscoutSyncStateDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieve() = runBlocking {
        val state = NightscoutSyncState(
            id = 1,
            lastProcessedSeqId = 12345L,
            lastSyncTime = LocalDateTime.of(2024, 12, 12, 10, 30),
            firstEnabledTime = LocalDateTime.of(2024, 12, 11, 8, 0),
            lookbackHours = 48
        )

        syncStateDao.upsert(state)
        val retrieved = syncStateDao.getState()

        assertNotNull(retrieved)
        assertEquals(12345L, retrieved?.lastProcessedSeqId)
        assertEquals(48, retrieved?.lookbackHours)
    }

    @Test
    fun testUpdate() = runBlocking {
        // Insert initial state
        val state = NightscoutSyncState(
            id = 1,
            lastProcessedSeqId = 100L,
            lookbackHours = 24
        )
        syncStateDao.upsert(state)

        // Update
        val newTime = LocalDateTime.now()
        syncStateDao.updateLastProcessed(200L, newTime)

        val retrieved = syncStateDao.getState()
        assertEquals(200L, retrieved?.lastProcessedSeqId)
        assertEquals(newTime.withNano(0), retrieved?.lastSyncTime?.withNano(0))
    }

    @Test
    fun testUpdateLookbackHours() = runBlocking {
        val state = NightscoutSyncState(id = 1, lookbackHours = 24)
        syncStateDao.upsert(state)

        syncStateDao.updateLookbackHours(72)

        val retrieved = syncStateDao.getState()
        assertEquals(72, retrieved?.lookbackHours)
    }

    @Test
    fun testSetRetroactiveRange() = runBlocking {
        val state = NightscoutSyncState(id = 1)
        syncStateDao.upsert(state)

        val startTime = LocalDateTime.of(2024, 12, 1, 0, 0)
        val endTime = LocalDateTime.of(2024, 12, 7, 23, 59)
        syncStateDao.setRetroactiveRange(startTime, endTime)

        val retrieved = syncStateDao.getState()
        assertEquals(startTime, retrieved?.retroactiveStartTime)
        assertEquals(endTime, retrieved?.retroactiveEndTime)
    }

    @Test
    fun testClearRetroactiveRange() = runBlocking {
        val state = NightscoutSyncState(
            id = 1,
            retroactiveStartTime = LocalDateTime.now().minusDays(7),
            retroactiveEndTime = LocalDateTime.now()
        )
        syncStateDao.upsert(state)

        syncStateDao.setRetroactiveRange(null, null)

        val retrieved = syncStateDao.getState()
        assertNull(retrieved?.retroactiveStartTime)
        assertNull(retrieved?.retroactiveEndTime)
    }

    @Test
    fun testDeleteAll() = runBlocking {
        val state = NightscoutSyncState(id = 1, lastProcessedSeqId = 999L)
        syncStateDao.upsert(state)

        syncStateDao.deleteAll()

        val retrieved = syncStateDao.getState()
        assertNull(retrieved)
    }

    @Test
    fun testUpsertReplacesExisting() = runBlocking {
        val state1 = NightscoutSyncState(id = 1, lastProcessedSeqId = 100L)
        syncStateDao.upsert(state1)

        val state2 = NightscoutSyncState(id = 1, lastProcessedSeqId = 200L)
        syncStateDao.upsert(state2)

        val retrieved = syncStateDao.getState()
        assertEquals(200L, retrieved?.lastProcessedSeqId)
    }

    @Test
    fun testGetStateWhenEmpty() = runBlocking {
        val retrieved = syncStateDao.getState()
        assertNull(retrieved)
    }
}
