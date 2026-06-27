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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.Collections

/**
 * Robolectric (JVM, CI-executed via testDebugUnitTest) coverage for the HistoryLogFetcher
 * sync/catch-up behavior. The pre-existing instrumentation suite
 * (androidTest/HistoryLogFetcherIntegrationTest) is not run by CI because there is no
 * emulator step, so these regression guards live in src/test where `testDebugUnitTest`
 * exercises them.
 *
 * Focus: scenarios that the catch-up logic must handle so the sync progress bar can always
 * reach 100% — the class of bug behind "history sync stops at 99%".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HistoryLogFetcherSyncTest {

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

    // ----------------------------------------------------------------------- helpers

    private fun makeStatusResponse(numEntries: Long, firstSeqNum: Long, lastSeqNum: Long) =
        HistoryLogStatusResponse(numEntries, firstSeqNum, lastSeqNum)

    private fun makeHistoryLog(seqId: Long, pumpTimeSec: Long = seqId * 300): HistoryLog =
        BGHistoryLog(
            pumpTimeSec, seqId,
            /* bg */ 120, /* cgmCalibration */ 0, /* bgSourceId */ 0,
            /* iob */ 0.0f, /* targetBG */ 110, /* isf */ 50,
            /* selectedIOB */ 0, /* bgSourceType */ 0, /* spare */ 0
        )

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
                    runBlocking { logs.forEach { fetcher.onStreamResponse(it) } }
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

    private fun createManualFetcher(): HistoryLogFetcher =
        HistoryLogFetcher(
            historyLogRepo = repo,
            pumpSid = TEST_PUMP_SID,
            commandSender = { start, count -> sentCommands.add(start to count) },
            autoFetchEnabled = { true },
            broadcastCallback = { item -> broadcastedItems.add(item) },
            requestDelayMs = FAST_REQUEST_DELAY_MS,
            pollIntervalMs = FAST_POLL_INTERVAL_MS,
            fetchGroupTimeoutMs = TEST_TIMEOUT_MS
        )

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

    private suspend fun waitForFetchCompletion(scope: CoroutineScope, timeoutMs: Long = 30_000L) {
        withTimeout(timeoutMs) {
            scope.coroutineContext[Job]?.children?.forEach { it.join() }
        }
    }

    private fun requestedSeqIds(): Set<Long> {
        val out = mutableSetOf<Long>()
        sentCommands.forEach { (start, count) ->
            val rangeStart = start - count + 1
            (rangeStart..start).forEach { out.add(it) }
        }
        return out
    }

    // ----------------------------------------------------------------------- tests

    @Test
    fun orphanedHoleBelowLatestSeq_isHealedOnNextStatus() = runBlocking {
        // The core "stuck at 99%" regression: a small hole sits below the newest stored entry
        // while the DB is otherwise mostly full. The pre-fix heuristic (scan from dbLatestId
        // unless <50% present) would orphan it forever; the fetcher must re-scan the whole
        // retained window and re-request the hole.
        val pumpLastSeq = 8000L
        val windowStart = pumpLastSeq - InitialHistoryLogCount // 3000
        insertExistingLogs(windowStart..7000L)   // 3000..7000
        // hole: 7001..7010 missing
        insertExistingLogs(7011L..pumpLastSeq)   // 7011..8000

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(numEntries = pumpLastSeq, firstSeqNum = 1, lastSeqNum = pumpLastSeq)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope, timeoutMs = 60_000L)

        val requested = requestedSeqIds()
        for (id in 7001L..7010L) {
            assertTrue("hole seqId $id should be re-requested", id in requested)
        }
        val windowIds = repo.getAllIds(TEST_PUMP_SID, windowStart, pumpLastSeq)
        assertEquals(
            "whole retained window should be present after healing the hole",
            (pumpLastSeq - windowStart + 1).toInt(),
            windowIds.size
        )
    }

    @Test
    fun resync_fullySyncedWindow_repeatedStatusResponsesSendNoCommands() = runBlocking {
        // Guards the "always scan the full window" change against redundant traffic: once the
        // retained window is complete, repeated status responses must detect no gaps and send
        // zero history-log requests.
        insertExistingLogs(1L..100L)
        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(numEntries = 100, firstSeqNum = 1, lastSeqNum = 100)
        val scope = CoroutineScope(Dispatchers.Default + Job())

        repeat(3) {
            fetcher.onStatusResponse(status, scope)
            waitForFetchCompletion(scope)
        }

        assertTrue(
            "a fully synced window must not re-request anything (got ${sentCommands.size} commands)",
            sentCommands.isEmpty()
        )
        assertEquals(100L, repo.getCount(TEST_PUMP_SID).firstOrNull())
    }

    @Test
    fun holeBelowLatestPlusNewLogsAtTop_bothHealedInOnePass() = runBlocking {
        // Realistic post-disconnect state: DB mostly full, a small hole well below the newest
        // stored entry, AND the pump has generated new logs above it. One status response must
        // fill both the orphaned hole and the new tail.
        val pumpLastSeq = 8000L
        val windowStart = pumpLastSeq - InitialHistoryLogCount // 3000
        insertExistingLogs(windowStart..7000L)   // 3000..7000
        // hole: 7001..7010 missing
        insertExistingLogs(7011L..7990L)         // 7011..7990 (newest stored = 7990)
        // new logs 7991..8000 not yet fetched

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(numEntries = pumpLastSeq, firstSeqNum = 1, lastSeqNum = pumpLastSeq)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope, timeoutMs = 60_000L)

        val windowIds = repo.getAllIds(TEST_PUMP_SID, windowStart, pumpLastSeq).toSet()
        for (id in 7001L..7010L) {
            assertTrue("orphaned hole seqId $id should be healed", id in windowIds)
        }
        for (id in 7991L..8000L) {
            assertTrue("new tail seqId $id should be fetched", id in windowIds)
        }
        assertEquals(
            "whole retained window should be contiguous after one pass",
            (pumpLastSeq - windowStart + 1).toInt(),
            windowIds.size
        )
    }

    @Test
    fun growingLastSeq_incrementalNewLogsFetchedAcrossStatusResponses() = runBlocking {
        // Steady state: the pump keeps generating logs between syncs. Each status response
        // should fetch only the newly-added tail and leave the already-synced range alone.
        val fetcher = createAutoRespondingFetcher()
        val scope = CoroutineScope(Dispatchers.Default + Job())

        fetcher.onStatusResponse(makeStatusResponse(100, 1, 100), scope)
        waitForFetchCompletion(scope)
        assertEquals("first batch fully synced", 100L, repo.getCount(TEST_PUMP_SID).firstOrNull())

        sentCommands.clear()
        fetcher.onStatusResponse(makeStatusResponse(150, 1, 150), scope)
        waitForFetchCompletion(scope)

        assertEquals("new logs fetched incrementally", 150L, repo.getCount(TEST_PUMP_SID).firstOrNull())
        val lowestRequested = sentCommands.minOf { (start, count) -> start - count + 1 }
        assertTrue(
            "already-synced logs must not be re-requested (lowest requested $lowestRequested)",
            lowestRequested >= 101
        )
    }

    @Test
    fun retentionAdvancesFirstSeq_keepsAgedRowsAndDoesNotRefetch() = runBlocking {
        // Old data we already stored, now aged out on the pump (firstSequenceNum advanced past
        // it). The fetcher must not try to re-fetch below firstSequenceNum nor disturb the rows
        // we already hold below that boundary.
        insertExistingLogs(1L..50L)

        val fetcher = createAutoRespondingFetcher()
        val status = makeStatusResponse(numEntries = 101, firstSeqNum = 900, lastSeqNum = 1000)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        fetcher.onStatusResponse(status, scope)
        waitForFetchCompletion(scope, timeoutMs = 60_000L)

        val lowestRequested = sentCommands.minOf { (start, count) -> start - count + 1 }
        assertTrue("should not request aged-out seqIds (lowest requested $lowestRequested)", lowestRequested >= 900)
        assertEquals("retained window should be fully fetched", 101, repo.getAllIds(TEST_PUMP_SID, 900, 1000).size)
        assertEquals("aged rows below firstSequenceNum should not be deleted", 50, repo.getAllIds(TEST_PUMP_SID, 1, 50).size)
    }

    @Test
    fun duplicateDeliveryAfterCacheEviction_insertedOnce() = runBlocking {
        // recentSeqIds only remembers the last 256 stream items; the DB's INSERT-IGNORE is the
        // real dedup guarantee. Re-delivering a seqId after it has been evicted from that cache
        // (which happens constantly during a 5000-entry sync) must not create a duplicate row.
        val fetcher = createManualFetcher()

        fetcher.onStreamResponse(makeHistoryLog(1L))
        (2L..300L).forEach { fetcher.onStreamResponse(makeHistoryLog(it)) }
        fetcher.onStreamResponse(makeHistoryLog(1L))

        assertEquals(
            "redelivered seqId must not duplicate (DB-level INSERT IGNORE)",
            300L,
            repo.getCount(TEST_PUMP_SID).firstOrNull()
        )
        assertEquals("seqId 1 should be present exactly once", 1, repo.getAllIds(TEST_PUMP_SID, 1, 1).size)
    }
}
