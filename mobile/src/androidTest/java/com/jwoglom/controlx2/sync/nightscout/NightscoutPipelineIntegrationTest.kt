package com.jwoglom.controlx2.sync.nightscout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncStateDatabase
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutClient
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DailyBasalHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Full pipeline integration tests for the Nightscout uploader.
 *
 * These tests exercise the complete flow:
 *   Room in-memory DB (with real PumpX2 history log cargo)
 *   → NightscoutSyncCoordinator
 *   → Processors (parse cargo → Nightscout models)
 *   → NightscoutClient (real HTTP)
 *   → In-process NanoHTTPD server
 *
 * The test server captures every HTTP request so we can assert on
 * endpoints, headers, and JSON bodies.
 */
@RunWith(AndroidJUnit4::class)
class NightscoutPipelineIntegrationTest {

    companion object {
        private const val PUMP_SID = 123
        private const val API_SECRET = "integration-test-secret"
        private const val SERVER_PORT = 0 // auto-assign
    }

    // --- Captured request model -----------------------------------------------

    data class CapturedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    )

    // --- Test HTTP server (NanoHTTPD) -----------------------------------------

    class TestNightscoutServer : NanoHTTPD("127.0.0.1", SERVER_PORT) {
        val capturedRequests = CopyOnWriteArrayList<CapturedRequest>()

        override fun serve(session: IHTTPSession): Response {
            val bodySize = (session.headers["content-length"] ?: "0").toIntOrNull() ?: 0
            val bodyMap = HashMap<String, String>()
            if (bodySize > 0) {
                session.parseBody(bodyMap)
            }
            val body = bodyMap["postData"] ?: ""

            capturedRequests.add(
                CapturedRequest(
                    method = session.method.name,
                    path = session.uri + (session.queryParameterString?.let { "?$it" } ?: ""),
                    headers = session.headers,
                    body = body
                )
            )

            return newFixedLengthResponse(Response.Status.OK, "application/json", "[]")
        }

        fun requestsForPath(pathPrefix: String) =
            capturedRequests.filter { it.path.startsWith(pathPrefix) }

        fun reset() = capturedRequests.clear()
    }

    // --- Fields ---------------------------------------------------------------

    private lateinit var historyLogDb: HistoryLogDatabase
    private lateinit var syncStateDb: NightscoutSyncStateDatabase
    private lateinit var historyLogRepo: HistoryLogRepo
    private lateinit var server: TestNightscoutServer
    private lateinit var context: Context
    private val gson = Gson()

    // --- Lifecycle -------------------------------------------------------------

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        historyLogDb = Room.inMemoryDatabaseBuilder(
            context, HistoryLogDatabase::class.java
        ).allowMainThreadQueries().build()

        syncStateDb = Room.inMemoryDatabaseBuilder(
            context, NightscoutSyncStateDatabase::class.java
        ).allowMainThreadQueries().build()

        historyLogRepo = HistoryLogRepo(historyLogDb.historyLogDao())

        server = TestNightscoutServer()
        server.start()
    }

    @After
    fun cleanup() {
        historyLogDb.close()
        syncStateDb.close()
        server.stop()
    }

    // --- Helpers ---------------------------------------------------------------

    private fun serverBaseUrl() = "http://127.0.0.1:${server.listeningPort}"

    private fun buildConfig(
        enabledProcessors: Set<ProcessorType> = ProcessorType.all(),
        lookbackHours: Int = 720 // 30 days – wide enough to include all test data
    ) = NightscoutSyncConfig(
        enabled = true,
        nightscoutUrl = serverBaseUrl(),
        apiSecret = API_SECRET,
        enabledProcessors = enabledProcessors,
        initialLookbackHours = lookbackHours
    )

    private fun buildCoordinator(config: NightscoutSyncConfig): NightscoutSyncCoordinator {
        val client = NightscoutClient(
            baseUrl = config.getSanitizedUrl(),
            apiSecret = config.apiSecret
        )
        return NightscoutSyncCoordinator(
            historyLogRepo,
            client,
            syncStateDb.nightscoutSyncStateDao(),
            config,
            pumpSid = PUMP_SID
        )
    }

    private fun toPumpTimeSec(ldt: LocalDateTime): Long {
        val epochSec = ldt.atZone(ZoneId.systemDefault()).toInstant().epochSecond
        return epochSec - Dates.JANUARY_1_2008_UNIX_EPOCH
    }

    private fun buildHistoryLogItem(
        seqId: Long,
        pumpTime: LocalDateTime,
        cargo: ByteArray
    ): HistoryLogItem {
        val parsed = HistoryLogParser.parse(cargo)
        return HistoryLogItem(
            seqId = seqId,
            pumpSid = PUMP_SID,
            typeId = parsed.typeId(),
            cargo = cargo,
            pumpTime = pumpTime,
            addedTime = LocalDateTime.now()
        )
    }

    // --- CGM cargo builder ----------------------------------------------------

    private fun buildCgmCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        glucoseValue: Int
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return DexcomG6CGMHistoryLog.buildCargo(
            pts, seqId,
            /* glucoseValueStatusRaw */ 0,
            /* cgmDataTypeRaw */ 0,
            /* rate */ 0,
            /* algorithmState */ 6,
            /* rssi */ -70,
            /* currentGlucoseDisplayValue */ glucoseValue,
            /* timeStampSeconds */ pts,
            /* egvInfoBitmaskRaw */ 0,
            /* interval */ 5
        )
    }

    // --- Bolus cargo builder --------------------------------------------------

    private fun buildBolusCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        deliveredTotalMilliunits: Int
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return BolusDeliveryHistoryLog.buildCargo(
            pts, seqId,
            /* bolusID */ 1,
            /* bolusDeliveryStatusId */ 2,
            /* bolusTypeBitmask */ 1,
            /* bolusSource */ 0,
            /* reserved */ 0,
            /* requestedNow */ deliveredTotalMilliunits,
            /* requestedLater */ 0,
            /* correction */ 0,
            /* extendedDurationRequested */ 0,
            /* deliveredTotal */ deliveredTotalMilliunits
        )
    }

    // --- Basal cargo builder --------------------------------------------------

    private fun buildBasalCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        commandedRateMilliunits: Int
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return BasalDeliveryHistoryLog.buildCargo(
            pts, seqId,
            /* commandedRate */ commandedRateMilliunits,
            /* profileBasalRate */ commandedRateMilliunits,
            /* algorithmRate */ 0,
            /* tempRate */ 0,
            /* basalDeliveryStatus */ 0
        )
    }

    // --- Suspension cargo builder ---------------------------------------------

    private fun buildSuspensionCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        insulinAmountMilliunits: Int
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return PumpingSuspendedHistoryLog.buildCargo(
            pts, seqId,
            /* insulinAmount */ insulinAmountMilliunits,
            /* reasonId */ 1
        )
    }

    // --- Resume cargo builder -------------------------------------------------

    private fun buildResumeCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        insulinAmountMilliunits: Int
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return PumpingResumedHistoryLog.buildCargo(
            pts, seqId,
            /* insulinAmount */ insulinAmountMilliunits
        )
    }

    // --- Cartridge cargo builders ---------------------------------------------

    private fun buildCartridgeFilledCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        insulinUnits: Float
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return CartridgeFilledHistoryLog.buildCargo(
            pts, seqId,
            /* insulinActual */ insulinUnits.toLong(),
            /* insulinDisplay */ insulinUnits
        )
    }

    private fun buildTubingFilledCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        primeSize: Float
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return TubingFilledHistoryLog.buildCargo(pts, seqId, primeSize)
    }

    private fun buildCannulaFilledCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        primeSize: Float
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return CannulaFilledHistoryLog.buildCargo(pts, seqId, primeSize)
    }

    // --- Device status cargo builder ------------------------------------------

    private fun buildDailyBasalCargo(
        pumpTime: LocalDateTime,
        seqId: Long,
        batteryChargeRaw: Int,
        iob: Float
    ): ByteArray {
        val pts = toPumpTimeSec(pumpTime)
        return DailyBasalHistoryLog.buildCargo(
            pts, seqId,
            /* dailyTotalBasal */ 12.0f,
            /* lastBasalRate */ 0.8f,
            /* iob */ iob,
            /* finalEventForDay */ false,
            /* batteryChargeRaw */ batteryChargeRaw,
            /* lipoMv */ 3800
        )
    }

    // =========================================================================
    // TESTS
    // =========================================================================

    @Test
    fun fullPipeline_cgmReadings_uploadedAsEntries() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(1001, now.minusMinutes(15), buildCgmCargo(now.minusMinutes(15), 1001, 120)),
            buildHistoryLogItem(1002, now.minusMinutes(10), buildCgmCargo(now.minusMinutes(10), 1002, 135)),
            buildHistoryLogItem(1003, now.minusMinutes(5),  buildCgmCargo(now.minusMinutes(5),  1003, 142))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.CGM_READING))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(3, success.processedCount)
        assertEquals(3, success.uploadedCount)

        val entryRequests = server.requestsForPath("/api/v1/entries")
        assertEquals(1, entryRequests.size)
        assertEquals("POST", entryRequests[0].method)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val entries: List<Map<String, Any>> = gson.fromJson(entryRequests[0].body, type)
        assertEquals(3, entries.size)

        assertEquals("sgv", entries[0]["type"])
        assertEquals(120.0, entries[0]["sgv"])
        assertEquals("1001", entries[0]["identifier"])

        assertEquals(135.0, entries[1]["sgv"])
        assertEquals(142.0, entries[2]["sgv"])
    }

    @Test
    fun fullPipeline_bolusDelivery_uploadedAsTreatment() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(2001, now.minusMinutes(30),
                buildBolusCargo(now.minusMinutes(30), 2001, 2500)),
            buildHistoryLogItem(2002, now.minusMinutes(10),
                buildBolusCargo(now.minusMinutes(10), 2002, 750))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.BOLUS))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).uploadedCount)

        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        assertEquals(1, treatmentRequests.size)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val treatments: List<Map<String, Any>> = gson.fromJson(treatmentRequests[0].body, type)
        assertEquals(2, treatments.size)

        assertEquals("Bolus", treatments[0]["eventType"])
        assertEquals(2.5, treatments[0]["insulin"])
        assertEquals("2001", treatments[0]["pumpId"])
        assertEquals("ControlX2", treatments[0]["enteredBy"])

        assertEquals("Bolus", treatments[1]["eventType"])
        assertEquals(0.75, treatments[1]["insulin"])
        assertEquals("2002", treatments[1]["pumpId"])
    }

    @Test
    fun fullPipeline_basalDelivery_uploadedAsTempBasal() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(3001, now.minusMinutes(60),
                buildBasalCargo(now.minusMinutes(60), 3001, 800))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.BASAL))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).uploadedCount)

        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val treatments: List<Map<String, Any>> = gson.fromJson(treatmentRequests[0].body, type)

        assertEquals("Temp Basal", treatments[0]["eventType"])
        assertEquals(0.8, treatments[0]["rate"])
        assertEquals(0.8, treatments[0]["absolute"])
    }

    @Test
    fun fullPipeline_suspension_uploadedAsZeroRateBasal() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(4001, now.minusMinutes(20),
                buildSuspensionCargo(now.minusMinutes(20), 4001, 5000))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.BASAL_SUSPENSION))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)

        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val treatments: List<Map<String, Any>> = gson.fromJson(treatmentRequests[0].body, type)

        assertEquals("Temp Basal", treatments[0]["eventType"])
        assertEquals(0.0, treatments[0]["rate"])
        assertEquals(0.0, treatments[0]["absolute"])
        assertTrue((treatments[0]["reason"] as String).contains("suspended"))
    }

    @Test
    fun fullPipeline_resume_uploadedAsNote() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(5001, now.minusMinutes(10),
                buildResumeCargo(now.minusMinutes(10), 5001, 4500))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.BASAL_RESUME))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)

        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val treatments: List<Map<String, Any>> = gson.fromJson(treatmentRequests[0].body, type)

        assertEquals("Note", treatments[0]["eventType"])
        assertTrue((treatments[0]["reason"] as String).contains("resumed"))
    }

    @Test
    fun fullPipeline_cartridgeAndSiteChange_uploadedAsTreatments() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(6001, now.minusMinutes(60),
                buildCartridgeFilledCargo(now.minusMinutes(60), 6001, 200f)),
            buildHistoryLogItem(6002, now.minusMinutes(50),
                buildTubingFilledCargo(now.minusMinutes(50), 6002, 15f)),
            buildHistoryLogItem(6003, now.minusMinutes(45),
                buildCannulaFilledCargo(now.minusMinutes(45), 6003, 0.3f))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.CARTRIDGE))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        assertEquals(3, (result as SyncResult.Success).uploadedCount)

        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val treatments: List<Map<String, Any>> = gson.fromJson(treatmentRequests[0].body, type)

        assertEquals("Insulin Change", treatments[0]["eventType"])
        assertEquals("Site Change", treatments[1]["eventType"])
        assertEquals("Site Change", treatments[2]["eventType"])
    }

    @Test
    fun fullPipeline_deviceStatus_uploadedToDeviceStatusEndpoint() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(7001, now.minusMinutes(5),
                buildDailyBasalCargo(now.minusMinutes(5), 7001, 85, 3.5f))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.DEVICE_STATUS))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).uploadedCount)

        val statusRequests = server.requestsForPath("/api/v1/devicestatus")
        assertEquals(1, statusRequests.size)
        assertEquals("POST", statusRequests[0].method)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val statuses: List<Map<String, Any>> = gson.fromJson(statusRequests[0].body, type)
        assertEquals(1, statuses.size)
        assertEquals("ControlX2", statuses[0]["device"])

        @Suppress("UNCHECKED_CAST")
        val pump = statuses[0]["pump"] as? Map<String, Any>
        assertNotNull(pump)
    }

    @Test
    fun fullPipeline_mixedTypes_routedToCorrectEndpoints() = runBlocking {
        val now = LocalDateTime.now()
        val items = listOf(
            buildHistoryLogItem(8001, now.minusMinutes(20),
                buildCgmCargo(now.minusMinutes(20), 8001, 110)),
            buildHistoryLogItem(8002, now.minusMinutes(15),
                buildCgmCargo(now.minusMinutes(15), 8002, 115)),
            buildHistoryLogItem(8003, now.minusMinutes(10),
                buildBolusCargo(now.minusMinutes(10), 8003, 3000)),
            buildHistoryLogItem(8004, now.minusMinutes(5),
                buildBasalCargo(now.minusMinutes(5), 8004, 1200)),
            buildHistoryLogItem(8005, now.minusMinutes(3),
                buildDailyBasalCargo(now.minusMinutes(3), 8005, 90, 2.0f))
        )
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig()
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(5, success.processedCount)

        val entryRequests = server.requestsForPath("/api/v1/entries")
        assertTrue("Expected at least 1 entry request", entryRequests.isNotEmpty())

        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        assertTrue("Expected at least 1 treatment request", treatmentRequests.isNotEmpty())

        val statusRequests = server.requestsForPath("/api/v1/devicestatus")
        assertTrue("Expected at least 1 device status request", statusRequests.isNotEmpty())

        val entryType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val entries: List<Map<String, Any>> = gson.fromJson(entryRequests[0].body, entryType)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it["type"] == "sgv" })
    }

    @Test
    fun fullPipeline_incrementalSync_onlyProcessesNewData() = runBlocking {
        val now = LocalDateTime.now()

        // First batch
        listOf(
            buildHistoryLogItem(9001, now.minusMinutes(30),
                buildCgmCargo(now.minusMinutes(30), 9001, 100)),
            buildHistoryLogItem(9002, now.minusMinutes(25),
                buildCgmCargo(now.minusMinutes(25), 9002, 105))
        ).forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.CGM_READING))
        val coordinator = buildCoordinator(config)

        val result1 = coordinator.syncAll()
        assertTrue(result1 is SyncResult.Success)
        assertEquals(2, (result1 as SyncResult.Success).processedCount)

        server.reset()

        // Add new data
        listOf(
            buildHistoryLogItem(9003, now.minusMinutes(20),
                buildCgmCargo(now.minusMinutes(20), 9003, 112)),
            buildHistoryLogItem(9004, now.minusMinutes(15),
                buildCgmCargo(now.minusMinutes(15), 9004, 118))
        ).forEach { historyLogRepo.insert(it) }

        // Second sync should only pick up new items
        val result2 = coordinator.syncAll()
        assertTrue(result2 is SyncResult.Success)
        val success2 = result2 as SyncResult.Success
        assertEquals(2, success2.processedCount)

        val entryRequests = server.requestsForPath("/api/v1/entries")
        assertEquals(1, entryRequests.size)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val entries: List<Map<String, Any>> = gson.fromJson(entryRequests[0].body, type)
        assertEquals(2, entries.size)
        assertEquals(112.0, entries[0]["sgv"])
        assertEquals(118.0, entries[1]["sgv"])
    }

    @Test
    fun fullPipeline_disabledProcessor_skipsUpload() = runBlocking {
        val now = LocalDateTime.now()

        listOf(
            buildHistoryLogItem(10001, now.minusMinutes(10),
                buildCgmCargo(now.minusMinutes(10), 10001, 130)),
            buildHistoryLogItem(10002, now.minusMinutes(5),
                buildBolusCargo(now.minusMinutes(5), 10002, 1500))
        ).forEach { historyLogRepo.insert(it) }

        // Only enable CGM, disable bolus
        val config = buildConfig(enabledProcessors = setOf(ProcessorType.CGM_READING))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue(result is SyncResult.Success)

        val entryRequests = server.requestsForPath("/api/v1/entries")
        assertEquals(1, entryRequests.size)

        // No bolus-related treatment requests should have been made
        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        assertEquals(0, treatmentRequests.size)
    }

    @Test
    fun fullPipeline_apiSecretHeader_presentOnAllRequests() = runBlocking {
        val now = LocalDateTime.now()

        listOf(
            buildHistoryLogItem(11001, now.minusMinutes(10),
                buildCgmCargo(now.minusMinutes(10), 11001, 120)),
            buildHistoryLogItem(11002, now.minusMinutes(5),
                buildBolusCargo(now.minusMinutes(5), 11002, 1000)),
            buildHistoryLogItem(11003, now.minusMinutes(3),
                buildDailyBasalCargo(now.minusMinutes(3), 11003, 80, 1.5f))
        ).forEach { historyLogRepo.insert(it) }

        val config = buildConfig()
        val coordinator = buildCoordinator(config)
        coordinator.syncAll()

        val expectedHash = java.security.MessageDigest.getInstance("SHA-1")
            .digest(API_SECRET.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertTrue(server.capturedRequests.isNotEmpty())
        server.capturedRequests.forEach { request ->
            assertEquals(
                "api-secret header should be SHA-1 hash on ${request.path}",
                expectedHash,
                request.headers["api-secret"]
            )
        }
    }

    @Test
    fun fullPipeline_syncStateUpdated_afterSuccessfulSync() = runBlocking {
        val now = LocalDateTime.now()

        listOf(
            buildHistoryLogItem(12001, now.minusMinutes(10),
                buildCgmCargo(now.minusMinutes(10), 12001, 100)),
            buildHistoryLogItem(12005, now.minusMinutes(5),
                buildCgmCargo(now.minusMinutes(5), 12005, 110))
        ).forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.CGM_READING))
        val coordinator = buildCoordinator(config)
        coordinator.syncAll()

        val state = syncStateDb.nightscoutSyncStateDao().getState()
        assertNotNull(state)
        assertEquals(12005L, state!!.lastProcessedSeqId)
        assertNotNull(state.lastSyncTime)
    }

    @Test
    fun fullPipeline_emptyDb_returnsNoData() = runBlocking {
        val config = buildConfig()
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue(result is SyncResult.NoData)
        assertTrue(server.capturedRequests.isEmpty())
    }

    @Test
    fun fullPipeline_invalidConfig_returnsInvalidConfig() = runBlocking {
        val now = LocalDateTime.now()
        listOf(
            buildHistoryLogItem(13001, now.minusMinutes(5),
                buildCgmCargo(now.minusMinutes(5), 13001, 100))
        ).forEach { historyLogRepo.insert(it) }

        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "",
            apiSecret = ""
        )
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue(result is SyncResult.InvalidConfig)
        assertTrue(server.capturedRequests.isEmpty())
    }

    @Test
    fun fullPipeline_disabledSync_returnsDisabled() = runBlocking {
        val now = LocalDateTime.now()
        listOf(
            buildHistoryLogItem(14001, now.minusMinutes(5),
                buildCgmCargo(now.minusMinutes(5), 14001, 100))
        ).forEach { historyLogRepo.insert(it) }

        val config = NightscoutSyncConfig(
            enabled = false,
            nightscoutUrl = serverBaseUrl(),
            apiSecret = API_SECRET
        )
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue(result is SyncResult.Disabled)
        assertTrue(server.capturedRequests.isEmpty())
    }

    @Test
    fun fullPipeline_suspendAndResume_correctEventSequence() = runBlocking {
        val now = LocalDateTime.now()

        listOf(
            buildHistoryLogItem(15001, now.minusMinutes(30),
                buildSuspensionCargo(now.minusMinutes(30), 15001, 5000)),
            buildHistoryLogItem(15002, now.minusMinutes(10),
                buildResumeCargo(now.minusMinutes(10), 15002, 4800))
        ).forEach { historyLogRepo.insert(it) }

        val config = buildConfig(
            enabledProcessors = setOf(ProcessorType.BASAL_SUSPENSION, ProcessorType.BASAL_RESUME)
        )
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).uploadedCount)

        // Both suspension and resume should produce treatment requests
        val treatmentRequests = server.requestsForPath("/api/v1/treatments")
        // Could be 1 or 2 requests (batched or separate) depending on processor ordering
        assertTrue(treatmentRequests.isNotEmpty())

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val allTreatments = treatmentRequests.flatMap { req ->
            gson.fromJson<List<Map<String, Any>>>(req.body, type)
        }
        assertEquals(2, allTreatments.size)

        val suspension = allTreatments.first { it["eventType"] == "Temp Basal" }
        assertEquals(0.0, suspension["rate"])

        val resume = allTreatments.first { it["eventType"] == "Note" }
        assertTrue((resume["reason"] as String).contains("resumed"))
    }

    @Test
    fun fullPipeline_largeBatch_allItemsProcessed() = runBlocking {
        val now = LocalDateTime.now()

        val items = (1..50).map { i ->
            buildHistoryLogItem(
                20000L + i,
                now.minusMinutes((50 - i).toLong()),
                buildCgmCargo(now.minusMinutes((50 - i).toLong()), 20000L + i, 80 + i)
            )
        }
        items.forEach { historyLogRepo.insert(it) }

        val config = buildConfig(enabledProcessors = setOf(ProcessorType.CGM_READING))
        val coordinator = buildCoordinator(config)
        val result = coordinator.syncAll()

        assertTrue("Expected Success, got $result", result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(50, success.processedCount)
        assertEquals(50, success.uploadedCount)

        val entryRequests = server.requestsForPath("/api/v1/entries")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val totalEntries = entryRequests.sumOf { req ->
            gson.fromJson<List<Map<String, Any>>>(req.body, type).size
        }
        assertEquals(50, totalEntries)
    }
}
