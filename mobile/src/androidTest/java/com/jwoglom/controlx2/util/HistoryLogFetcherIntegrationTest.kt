package com.jwoglom.controlx2.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BGHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.util.Collections

/**
 * Integration tests for HistoryLogFetcher.
 *
 * These test the entire flow: status response triggers fetch requests,
 * simulated pump responses insert into the in-memory Room DB, and the
 * fetcher polls until the DB has the expected data.
 */
@RunWith(AndroidJUnit4::class)
class HistoryLogFetcherIntegrationTest {

    private lateinit var db: HistoryLogDatabase
    private lateinit var repo: HistoryLogRepo
    private lateinit var context: Context

    private val sentCommands: MutableList<Pair<Long, Int>> =
        Collections.synchronizedList(mutableListOf())
    private val broadcastedItems: MutableList<HistoryLogItem> =
        Collections.synchronizedList(mutableListOf())

    companion object {
        private const val TEST_PUMP_SID = 123
        private const val FAST_REQUEST_DELAY_MS = 20L
        private const val FAST_POLL_INTERVAL_MS = 20L
        private const val TEST_TIMEOUT_MS = 5000L
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HistoryLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = HistoryLogRepo(db.historyLogDao())
        sentCommands.clear()
        broadcastedItems.clear()
    }

    @After
    fun cleanup() {
        db.close()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeStatusResponse(
        numEntries: Long,
        firstSeqNum: Long,
        lastSeqNum: Long
    ): HistoryLogStatusResponse {
        return HistoryLogStatusResponse(numEntries, firstSeqNum, lastSeqNum)
    }

    private fun makeHistoryLog(seqId: Long, pumpTimeSec: Long = seqId * 300): HistoryLog {
        return BGHistoryLog(
            pumpTimeSec, seqId,
            /* bg */ 120, /* cgmCalibration */ 0, /* bgSourceId */ 0,
            /* iob */ 0.0f, /* targetBG */ 110, /* isf */ 50,
            /* selectedIOB */ 0, /* bgSourceType */ 0, /* spare */ 0
        )
    }

    /**
     * Creates a HistoryLogFetcher that automatically responds to commands by
     * feeding simulated HistoryLog entries back through onStreamResponse.
     *
     * [responseProvider] receives the (startSeqId, count) from each request
     * and returns the list of HistoryLogs to simulate as the pump's reply.
     * If null, a default provider generates BGHistoryLog entries for the
     * requested range.
     */
    private fun createAutoRespondingFetcher(
        autoFetchEnabled: Boolean = true,
        responseDelayMs: Long = 5,
        responseProvider: ((startSeqId: Long, count: Int) -> List<HistoryLog>)? = null,
        fetchGroupTimeoutMs: Long = TEST_TIMEOUT_MS
    ): HistoryLogFetcher {
        lateinit var fetcher: HistoryLogFetcher

        val provider = responseProvider ?: { startSeqId, count ->
            val startOfRange = startSeqId - count + 1
            (startOfRange..startSeqId).map { makeHistoryLog(it) }
        }

        fetcher = HistoryLogFetcher(
            historyLogRepo = repo,
            pumpSid = TEST_PUMP_SID,
            commandSender = { start, count ->
                sentCommands.add(start to count)
                Thread {
                    Thread.sleep(responseDelayMs)
                    val logs = provider(start, count)
                    runBlocking {
                        logs.forEach { fetcher.onStreamResponse(it) }
                    }
                }.start()
            },
            autoFetchEnabled = { autoFetchEnabled },
            broadcastCallback = { item -> broadcastedItems.add(item) },
            requestDelayMs = FAST_REQUEST_DELAY_MS,
            pollIntervalMs = FAST_POLL_INTERVAL_MS,
            fetchGroupTimeoutMs = fetchGroupTimeoutMs
        )
        return fetcher
    }

    /**
     * Creates a fetcher that records commands but does NOT auto-respond.
     * The caller must manually call onStreamResponse.
     */
    private fun createManualFetcher(
        autoFetchEnabled: Boolean = true,
        fetchGroupTimeoutMs: Long = TEST_TIMEOUT_MS
    ): HistoryLogFetcher {
        return HistoryLogFetcher(
            historyLogRepo = repo,
            pumpSid = TEST_PUMP_SID,
            commandSender = { start, count -> sentCommands.add(start to count) },
            autoFetchEnabled = { autoFetchEnabled },
            broadcastCallback = { item -> broadcastedItems.add(item) },
            requestDelayMs = FAST_REQUEST_DELAY_MS,
            pollIntervalMs = FAST_POLL_INTERVAL_MS,
            fetchGroupTimeoutMs = fetchGroupTimeoutMs
        )
    }

    private suspend fun insertExistingLogs(seqIds: LongRange) {
        seqIds.forEach { seqId ->
            repo.insert(
                HistoryLogItem(
                    seqId = seqId,
                    pumpSid = TEST_PUMP_SID,
                    typeId = 1,
                    cargo = ByteArray(26),
                    pumpTime = LocalDateTime.now(),
                    addedTime = LocalDateTime.now()
                )
            )
        }
    }

    /**
     * Wait for the fetch coroutine launched by onStatusResponse to complete.
     * The status response launches a child coroutine in the provided scope;
     * we wait for all children to finish.
     */
    private suspend fun waitForFetchCompletion(scope: CoroutineScope, timeoutMs: Long = 30_000L) {
        withTimeout(timeoutMs) {
            // The scope's coroutineContext[Job] has children launched by
            // onStatusResponse; wait for them.
            val job = scope.coroutineContext[Job]
            job?.children?.forEach { it.join() }
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun freshFetch_smallRange_allItemsInserted() = runBlocking {
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 50, firstSeqNum = 1, lastSeqNum = 50
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 50 items should be in DB", 50L, dbCount)
    }

    @Test
    fun freshFetch_verifyCorrectChunking() = runBlocking {
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 100, firstSeqNum = 1, lastSeqNum = 100
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        // Chunk size is 32, fetching 1..100 = 100 items
        // Chunks should be requested newest-first:
        //   100 (count=32) -> covers 69..100
        //   68  (count=32) -> covers 37..68
        //   36  (count=32) -> covers 5..36
        //   4   (count=4)  -> covers 1..4
        assertTrue("Should have sent at least 4 commands", sentCommands.size >= 4)

        val firstCmd = sentCommands[0]
        assertEquals("First request should start at seqId 100", 100L, firstCmd.first)
        assertEquals("First chunk should be size 32", 32, firstCmd.second)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 100 items should be in DB", 100L, dbCount)
    }

    @Test
    fun freshFetch_commandsSentNewestFirst() = runBlocking {
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 96, firstSeqNum = 1, lastSeqNum = 96
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        // 96 items / 32 chunk = exactly 3 chunks
        assertEquals("Should send exactly 3 commands", 3, sentCommands.size)
        assertEquals("First command at 96", 96L, sentCommands[0].first)
        assertEquals("Second command at 64", 64L, sentCommands[1].first)
        assertEquals("Third command at 32", 32L, sentCommands[2].first)

        sentCommands.forEach { (_, count) ->
            assertEquals("Each chunk should be 32", 32, count)
        }
    }

    @Test
    fun incrementalFetch_onlyMissingItemsRequested() = runBlocking {
        insertExistingLogs(1L..80L)

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 100, firstSeqNum = 1, lastSeqNum = 100
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 100 items should be in DB", 100L, dbCount)

        // Only seqIds 81..100 should have been requested (20 items)
        val requestedSeqIds = mutableSetOf<Long>()
        sentCommands.forEach { (start, count) ->
            val rangeStart = start - count + 1
            (rangeStart..start).forEach { requestedSeqIds.add(it) }
        }
        for (id in 81L..100L) {
            assertTrue("SeqId $id should have been requested", id in requestedSeqIds)
        }
    }

    @Test
    fun gapFilling_missingMiddleRangeRequested() = runBlocking {
        insertExistingLogs(1L..40L)
        insertExistingLogs(61L..100L)

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 100, firstSeqNum = 1, lastSeqNum = 100
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 100 items should be in DB", 100L, dbCount)

        // The gap is 41..60 (20 items). Verify these were requested.
        val requestedSeqIds = mutableSetOf<Long>()
        sentCommands.forEach { (start, count) ->
            val rangeStart = start - count + 1
            (rangeStart..start).forEach { requestedSeqIds.add(it) }
        }
        for (id in 41L..60L) {
            assertTrue("Gap seqId $id should have been requested", id in requestedSeqIds)
        }
    }

    @Test
    fun multipleGaps_allGapsFilled() = runBlocking {
        insertExistingLogs(1L..20L)
        // gap: 21..30
        insertExistingLogs(31L..50L)
        // gap: 51..60
        insertExistingLogs(61L..80L)

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 80, firstSeqNum = 1, lastSeqNum = 80
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 80 items should be in DB", 80L, dbCount)

        val allIds = repo.getAllIds(TEST_PUMP_SID, 1, 80)
        assertEquals("Should have 80 distinct IDs", 80, allIds.size)
        for (id in 1L..80L) {
            assertTrue("SeqId $id should be in DB", id in allIds)
        }
    }

    @Test
    fun autoFetchDisabled_noCommandsSent() = runBlocking {
        val fetcher = createAutoRespondingFetcher(autoFetchEnabled = false)
        val status = makeStatusResponse(
            numEntries = 100, firstSeqNum = 1, lastSeqNum = 100
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        delay(200)

        assertTrue("No commands should be sent when auto-fetch is disabled", sentCommands.isEmpty())
        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("DB should remain empty", 0L, dbCount)
    }

    @Test
    fun onStreamResponse_insertsItemAndBroadcasts() = runBlocking {
        val fetcher = createManualFetcher()
        val log = makeHistoryLog(42L, pumpTimeSec = 1700000000L)

        fetcher.onStreamResponse(log)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("One item should be in DB", 1L, dbCount)

        assertEquals("One item should be broadcast", 1, broadcastedItems.size)
        assertEquals("Broadcast item seqId", 42L, broadcastedItems[0].seqId)
        assertEquals("Broadcast item pumpSid", TEST_PUMP_SID, broadcastedItems[0].pumpSid)
    }

    @Test
    fun onStreamResponse_duplicateSeqIdIgnored() = runBlocking {
        val fetcher = createManualFetcher()
        val log1 = makeHistoryLog(42L)
        val log2 = makeHistoryLog(42L)

        fetcher.onStreamResponse(log1)
        fetcher.onStreamResponse(log2)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("Duplicate should be ignored (INSERT IGNORE)", 1L, dbCount)
    }

    @Test
    fun largeRange_respectsInitialHistoryLogCountThreshold() = runBlocking {
        // Pump has logs 1..10000 but InitialHistoryLogCount is 5000,
        // so the fetcher should only request the last 5000 (5001..10000).
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 10000, firstSeqNum = 1, lastSeqNum = 10000
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope, timeoutMs = 60_000L)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull() ?: 0
        assertTrue(
            "Should fetch approximately $InitialHistoryLogCount items (got $dbCount)",
            dbCount in (InitialHistoryLogCount - 10).toLong()..(InitialHistoryLogCount + 10).toLong()
        )

        // Verify no requests for seqIds below the threshold
        val lowestRequestedSeqId = sentCommands.minOf { (start, count) -> start - count + 1 }
        assertTrue(
            "Should not request seqIds much below threshold (lowest requested: $lowestRequestedSeqId)",
            lowestRequestedSeqId >= (10000 - InitialHistoryLogCount - 10)
        )
    }

    @Test
    fun timeoutBehavior_partialResponseStillStored() = runBlocking {
        // Respond to only half the requested items per chunk,
        // triggering timeouts for each chunk.
        val fetcher = createAutoRespondingFetcher(
            fetchGroupTimeoutMs = 300,
            responseProvider = { startSeqId, count ->
                val startOfRange = startSeqId - count + 1
                val halfCount = count / 2
                // Only return the first half of the range
                (startOfRange until startOfRange + halfCount).map { makeHistoryLog(it) }
            }
        )
        val status = makeStatusResponse(
            numEntries = 32, firstSeqNum = 1, lastSeqNum = 32
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull() ?: 0
        assertTrue("Partial items should still be in DB (got $dbCount)", dbCount > 0)
        assertTrue("Should not have all items due to timeout (got $dbCount)", dbCount < 32)
    }

    @Test
    fun noResponseAtAll_timesOutGracefully() = runBlocking {
        val fetcher = createAutoRespondingFetcher(
            fetchGroupTimeoutMs = 200,
            responseProvider = { _, _ -> emptyList() }
        )
        val status = makeStatusResponse(
            numEntries = 32, firstSeqNum = 1, lastSeqNum = 32
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull() ?: 0
        assertEquals("DB should be empty with no responses", 0L, dbCount)
        assertTrue("Commands should still have been sent", sentCommands.isNotEmpty())
    }

    @Test
    fun firstSequenceNumRespected_doesNotFetchBeforeAvailable() = runBlocking {
        // Pump has data only from 5000..10000 (old data was aged out)
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 5001, firstSeqNum = 5000, lastSeqNum = 10000
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope, timeoutMs = 60_000L)

        val lowestRequestedSeqId = sentCommands.minOf { (start, count) -> start - count + 1 }
        assertTrue(
            "Should not request seqIds before pump's firstSequenceNum (lowest: $lowestRequestedSeqId)",
            lowestRequestedSeqId >= 5000
        )
    }

    @Test
    fun correctBtCommands_requestArgFormat() = runBlocking {
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 64, firstSeqNum = 1, lastSeqNum = 64
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        // HistoryLogRequest(startSeqId, count):
        // the pump returns items backward from startSeqId.
        // So for range [33..64], the request is (64, 32).
        // For range [1..32], the request is (32, 32).
        assertEquals("Should send exactly 2 commands", 2, sentCommands.size)

        val cmd1 = sentCommands[0]
        assertEquals("First command startSeqId", 64L, cmd1.first)
        assertEquals("First command count", 32, cmd1.second)

        val cmd2 = sentCommands[1]
        assertEquals("Second command startSeqId", 32L, cmd2.first)
        assertEquals("Second command count", 32, cmd2.second)
    }

    @Test
    fun incrementalFetch_dbAlreadyCaughtUp_noCommands() = runBlocking {
        insertExistingLogs(1L..100L)

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 100, firstSeqNum = 1, lastSeqNum = 100
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        assertTrue("No commands should be sent when DB is up to date", sentCommands.isEmpty())
    }

    @Test
    fun broadcastCallback_calledForEachItem() = runBlocking {
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 10, firstSeqNum = 1, lastSeqNum = 10
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        assertEquals("Should broadcast 10 items", 10, broadcastedItems.size)
        val broadcastSeqIds = broadcastedItems.map { it.seqId }.toSet()
        for (id in 1L..10L) {
            assertTrue("SeqId $id should be broadcast", id in broadcastSeqIds)
        }
    }

    @Test
    fun historyLogItemFields_correctlyMapped() = runBlocking {
        val fetcher = createManualFetcher()
        val pumpTimeSec = 1700000000L
        val log = makeHistoryLog(seqId = 55, pumpTimeSec = pumpTimeSec)

        fetcher.onStreamResponse(log)

        val latest = repo.getLatest(TEST_PUMP_SID).firstOrNull()
        assertNotNull("Latest item should exist", latest)
        assertEquals("seqId", 55L, latest!!.seqId)
        assertEquals("pumpSid", TEST_PUMP_SID, latest.pumpSid)
        assertTrue("typeId should match BGHistoryLog", latest.typeId > 0)
        assertNotNull("cargo should not be null", latest.cargo)
        assertTrue("cargo should have data", latest.cargo.isNotEmpty())
    }

    @Test
    fun singleItem_fetchAndVerify() = runBlocking {
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(
            numEntries = 1, firstSeqNum = 500, lastSeqNum = 500
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("Single item should be in DB", 1L, dbCount)
        assertEquals("One command should be sent", 1, sentCommands.size)
        assertEquals("Command start", 500L, sentCommands[0].first)
        assertEquals("Command count", 1, sentCommands[0].second)
    }

    @Test
    fun staticResponsePackets_endToEndVerification() = runBlocking {
        // Define specific static response data to simulate realistic pump responses
        val staticLogs = mapOf(
            1L to BGHistoryLog(
                1000L, 1L, 150, 0, 0, 5.2f, 110, 50, 0, 0, 0
            ),
            2L to BGHistoryLog(
                1300L, 2L, 145, 0, 0, 4.8f, 110, 50, 0, 0, 0
            ),
            3L to BGHistoryLog(
                1600L, 3L, 130, 0, 0, 3.1f, 110, 50, 0, 0, 0
            ),
            4L to BGHistoryLog(
                1900L, 4L, 112, 0, 0, 2.0f, 110, 50, 0, 0, 0
            ),
            5L to BGHistoryLog(
                2200L, 5L, 98, 0, 0, 1.5f, 110, 50, 0, 0, 0
            )
        )

        val fetcher = createAutoRespondingFetcher(
            responseProvider = { startSeqId, count ->
                val rangeStart = startSeqId - count + 1
                (rangeStart..startSeqId).mapNotNull { staticLogs[it] }
            }
        )

        val status = makeStatusResponse(
            numEntries = 5, firstSeqNum = 1, lastSeqNum = 5
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 5 static items should be in DB", 5L, dbCount)

        val allIds = repo.getAllIds(TEST_PUMP_SID, 1, 5)
        assertEquals("All IDs present", listOf(1L, 2L, 3L, 4L, 5L), allIds)

        val item1 = repo.getLatest(TEST_PUMP_SID).firstOrNull()
        assertNotNull("Latest item exists", item1)
        assertEquals("Latest seqId should be 5", 5L, item1!!.seqId)
    }

    @Test
    fun manualRespond_simulateDelayedPumpBehavior() = runBlocking {
        val fetcher = createManualFetcher(fetchGroupTimeoutMs = 2000)

        val status = makeStatusResponse(
            numEntries = 5, firstSeqNum = 1, lastSeqNum = 5
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)

        // The fetcher sends commands asynchronously. Give it time to start.
        delay(100)
        assertTrue("Commands should have been sent", sentCommands.isNotEmpty())

        // Now simulate the pump responding, one at a time
        for (cmd in sentCommands.toList()) {
            val rangeStart = cmd.first - cmd.second + 1
            for (seqId in rangeStart..cmd.first) {
                fetcher.onStreamResponse(makeHistoryLog(seqId))
            }
        }

        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 5 items should be in DB", 5L, dbCount)
    }

    @Test
    fun concurrentStreamResponses_threadSafe() = runBlocking {
        val fetcher = createManualFetcher()

        // Fire many concurrent onStreamResponse calls
        val jobs = (1L..50L).map { seqId ->
            launch(Dispatchers.Default) {
                fetcher.onStreamResponse(makeHistoryLog(seqId))
            }
        }
        jobs.forEach { it.join() }

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull()
        assertEquals("All 50 items should be inserted safely", 50L, dbCount)
    }

    @Test
    fun harness_customResponseProvider_canDropSpecificSeqIds() = runBlocking {
        val droppedIds = setOf(3L, 7L, 15L)
        val fetcher = createAutoRespondingFetcher(
            fetchGroupTimeoutMs = 500,
            responseProvider = { startSeqId, count ->
                val rangeStart = startSeqId - count + 1
                (rangeStart..startSeqId)
                    .filter { it !in droppedIds }
                    .map { makeHistoryLog(it) }
            }
        )

        val status = makeStatusResponse(
            numEntries = 20, firstSeqNum = 1, lastSeqNum = 20
        )

        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope)

        val dbCount = repo.getCount(TEST_PUMP_SID).firstOrNull() ?: 0
        assertEquals("Should have 17 items (20 - 3 dropped)", 17L, dbCount)

        val allIds = repo.getAllIds(TEST_PUMP_SID, 1, 20)
        for (id in droppedIds) {
            assertFalse("Dropped seqId $id should NOT be in DB", id in allIds)
        }
    }
}
