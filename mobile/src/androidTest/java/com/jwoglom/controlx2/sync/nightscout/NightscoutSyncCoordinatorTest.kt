package com.jwoglom.controlx2.sync.nightscout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncStateDatabase
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Integration tests for NightscoutSyncCoordinator
 */
@RunWith(AndroidJUnit4::class)
class NightscoutSyncCoordinatorTest {
    private lateinit var historyLogDb: HistoryLogDatabase
    private lateinit var syncStateDb: NightscoutSyncStateDatabase
    private lateinit var historyLogRepo: HistoryLogRepo
    private lateinit var mockApi: MockNightscoutApi
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory databases
        historyLogDb = Room.inMemoryDatabaseBuilder(
            context, HistoryLogDatabase::class.java
        ).build()

        syncStateDb = Room.inMemoryDatabaseBuilder(
            context, NightscoutSyncStateDatabase::class.java
        ).build()

        historyLogRepo = HistoryLogRepo(historyLogDb.historyLogDao())
        mockApi = MockNightscoutApi()
    }

    @After
    fun cleanup() {
        historyLogDb.close()
        syncStateDb.close()
    }

    @Test
    fun testSyncWithNoData() = runBlocking {
        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "https://test.com",
            apiSecret = "secret"
        )

        val coordinator = NightscoutSyncCoordinator(
            historyLogRepo,
            mockApi,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = 123
        )

        val result = coordinator.syncAll()
        assertTrue(result is SyncResult.NoData)
    }

    @Test
    fun testSyncWhenDisabled() = runBlocking {
        val config = NightscoutSyncConfig(
            enabled = false,
            nightscoutUrl = "https://test.com",
            apiSecret = "secret"
        )

        val coordinator = NightscoutSyncCoordinator(
            historyLogRepo,
            mockApi,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = 123
        )

        val result = coordinator.syncAll()
        assertTrue(result is SyncResult.Disabled)
    }

    @Test
    fun testSyncWithInvalidConfig() = runBlocking {
        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "",
            apiSecret = ""
        )

        val coordinator = NightscoutSyncCoordinator(
            historyLogRepo,
            mockApi,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = 123
        )

        val result = coordinator.syncAll()
        assertTrue(result is SyncResult.InvalidConfig)
    }

    @Test
    fun testFirstSyncWithLookback() = runBlocking {
        // Add some history logs
        val now = LocalDateTime.now()
        val logs = listOf(
            createHistoryLogItem(100L, now.minusHours(48)),
            createHistoryLogItem(101L, now.minusHours(36)),
            createHistoryLogItem(102L, now.minusHours(12)),
            createHistoryLogItem(103L, now.minusHours(1))
        )

        logs.forEach { historyLogRepo.insert(it) }

        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "https://test.com",
            apiSecret = "secret",
            initialLookbackHours = 24
        )

        val coordinator = NightscoutSyncCoordinator(
            historyLogRepo,
            mockApi,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = 123
        )

        val result = coordinator.syncAll()

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(2, success.processedCount) // Only last 24h (2 logs)

        // Verify state was updated
        val state = syncStateDb.nightscoutSyncStateDao().getState()
        assertNotNull(state)
        assertEquals(103L, state?.lastProcessedSeqId)
    }

    @Test
    fun testIncrementalSync() = runBlocking {
        // Add initial logs
        val now = LocalDateTime.now()
        listOf(
            createHistoryLogItem(100L, now.minusHours(2)),
            createHistoryLogItem(101L, now.minusHours(1))
        ).forEach { historyLogRepo.insert(it) }

        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "https://test.com",
            apiSecret = "secret"
        )

        val coordinator = NightscoutSyncCoordinator(
            historyLogRepo,
            mockApi,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = 123
        )

        // First sync
        coordinator.syncAll()

        // Add more logs
        listOf(
            createHistoryLogItem(102L, now.minusMinutes(30)),
            createHistoryLogItem(103L, now.minusMinutes(15))
        ).forEach { historyLogRepo.insert(it) }

        // Second sync should only process new logs
        val result = coordinator.syncAll()

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(2, success.processedCount) // Only the 2 new logs
        assertEquals(102L to 103L, success.seqIdRange)
    }

    @Test
    fun testRetroactiveSync() = runBlocking {
        // Add historical logs
        val now = LocalDateTime.now()
        listOf(
            createHistoryLogItem(100L, now.minusDays(10)),
            createHistoryLogItem(101L, now.minusDays(9)),
            createHistoryLogItem(102L, now.minusDays(8)),
            createHistoryLogItem(103L, now.minusDays(1))
        ).forEach { historyLogRepo.insert(it) }

        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "https://test.com",
            apiSecret = "secret"
        )

        val coordinator = NightscoutSyncCoordinator(
            historyLogRepo,
            mockApi,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = 123
        )

        // Retroactive sync for specific range
        val result = coordinator.syncRetroactive(
            now.minusDays(10),
            now.minusDays(8)
        )

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(3, success.processedCount) // 3 logs in range

        // Verify retroactive range was cleared
        val state = syncStateDb.nightscoutSyncStateDao().getState()
        assertNull(state?.retroactiveStartTime)
        assertNull(state?.retroactiveEndTime)
    }

    private fun createHistoryLogItem(seqId: Long, pumpTime: LocalDateTime): HistoryLogItem {
        return HistoryLogItem(
            seqId = seqId,
            pumpSid = 123,
            typeId = 1, // Some test type
            cargo = ByteArray(10),
            pumpTime = pumpTime,
            addedTime = LocalDateTime.now()
        )
    }
}

/**
 * Mock implementation of NightscoutApi for testing
 */
class MockNightscoutApi : NightscoutApi {
    val uploadedEntries = mutableListOf<NightscoutEntry>()
    val uploadedTreatments = mutableListOf<NightscoutTreatment>()
    val uploadedDeviceStatus = mutableListOf<NightscoutDeviceStatus>()

    override suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int> {
        uploadedEntries.addAll(entries)
        return Result.success(entries.size)
    }

    override suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int> {
        uploadedTreatments.addAll(treatments)
        return Result.success(treatments.size)
    }

    override suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean> {
        uploadedDeviceStatus.add(status)
        return Result.success(true)
    }

    override suspend fun getLastEntries(count: Int): Result<List<NightscoutEntry>> {
        return Result.success(uploadedEntries.takeLast(count))
    }

    override suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?> {
        return Result.success(
            uploadedTreatments.lastOrNull { it.eventType == eventType }
        )
    }

    fun reset() {
        uploadedEntries.clear()
        uploadedTreatments.clear()
        uploadedDeviceStatus.clear()
    }
}
