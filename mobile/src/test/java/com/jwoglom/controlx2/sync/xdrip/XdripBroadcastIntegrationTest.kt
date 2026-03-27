package com.jwoglom.controlx2.sync.xdrip

import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Integration tests that wire the real [XdripBroadcastSender] into the real
 * [XdripMessageDispatcher] and feed mock pump responses through the full
 * pipeline. Captured broadcasts are validated against the field names, types,
 * and constraints that xDrip's NSClientReceiver / ExternalStatusService
 * actually expect, so any schema mismatch is caught at test time rather than
 * silently dropped at runtime.
 */
class XdripBroadcastIntegrationTest {

    /** Every broadcast that reaches the "send to Android" boundary. */
    private data class CapturedBroadcast(val action: String, val extraKey: String, val payload: String)

    private val broadcasts = mutableListOf<CapturedBroadcast>()
    private var nowMillis = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()

    private val VALID_XDRIP_DIRECTIONS = setOf(
        "DoubleDown", "SingleDown", "FortyFiveDown", "Flat",
        "FortyFiveUp", "SingleUp", "DoubleUp"
    )

    private lateinit var sender: XdripBroadcastSender
    private lateinit var dispatcher: XdripMessageDispatcher

    private var config = XdripSyncConfig(
        enabled = true,
        sendCgmSgv = true,
        sendPumpDeviceStatus = true,
        sendTreatments = true,
        sendStatusLine = true
    )

    @Before
    fun setUp() {
        broadcasts.clear()
        nowMillis = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        config = XdripSyncConfig(
            enabled = true,
            sendCgmSgv = true,
            sendPumpDeviceStatus = true,
            sendTreatments = true,
            sendStatusLine = true
        )
        sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload ->
                broadcasts.add(CapturedBroadcast(action, extra, payload))
            },
            nowMillisFn = { nowMillis }
        )
        dispatcher = XdripMessageDispatcher(
            broadcaster = sender,
            configProvider = { config },
            nowProvider = { Instant.ofEpochMilli(nowMillis) }
        )
    }

    // ---------------------------------------------------------------
    // Helpers that replicate xDrip's JSON field-access patterns
    // ---------------------------------------------------------------

    /** Mirrors xDrip JoH.JsonStringtoMap — JSONObject keys into a HashMap. */
    private fun jsonToMap(json: String): Map<String, Any?> {
        val obj = JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.opt(it) }
    }

    private fun broadcastsWithAction(action: String) =
        broadcasts.filter { it.action == action }

    // ---------------------------------------------------------------
    // 1. SGV broadcast — xDrip NSClientReceiver.toBgReadingJSON()
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_sgvBroadcast_matchesXdripSchema() {
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 145, 1, 2))

        val sgvBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_SGV)
        assertEquals("exactly one SGV broadcast", 1, sgvBroadcasts.size)

        val bc = sgvBroadcasts.single()
        assertEquals(XdripBroadcastSender.ACTION_NEW_SGV, bc.action)
        assertEquals("sgvs", bc.extraKey)

        // xDrip parses: JSONArray(extras.getString("sgvs")).getJSONObject(i)
        val arr = JSONArray(bc.payload)
        assertTrue("payload must be a non-empty array", arr.length() > 0)
        val sgvMap = jsonToMap(arr.getJSONObject(0).toString())

        // xDrip does sgv_map.get("mills").toString() → Long — null causes NPE
        assertNotNull("xDrip reads 'mills' for timestamp — null crashes receiver", sgvMap["mills"])
        assertTrue("mills must be positive epoch millis", (sgvMap["mills"] as Number).toLong() > 0)

        // xDrip does sgv_map.get("mgdl").toString() → Integer
        assertNotNull("xDrip reads 'mgdl' for calculated_value — null gives bad reading", sgvMap["mgdl"])
        assertEquals(145, (sgvMap["mgdl"] as Number).toInt())

        // xDrip does sgv_map.get("direction").toString() — null causes NPE at line 263
        assertNotNull("xDrip reads 'direction'.toString() — null causes NPE", sgvMap["direction"])
        assertTrue(
            "direction must be a valid xDrip direction name, got: ${sgvMap["direction"]}",
            sgvMap["direction"].toString() in VALID_XDRIP_DIRECTIONS
        )
    }

    @Test
    fun fullPipeline_sgvBroadcast_allTrendRatesProduceValidDirections() {
        // Test the full range of trend rates through the entire pipeline
        for (trendRate in -5..5) {
            broadcasts.clear()
            // Reset sender cache by creating fresh instances
            val localSender = XdripBroadcastSender(
                sendBroadcastFn = { action, extra, payload ->
                    broadcasts.add(CapturedBroadcast(action, extra, payload))
                },
                nowMillisFn = { nowMillis }
            )
            val localDispatcher = XdripMessageDispatcher(
                broadcaster = localSender,
                configProvider = { config.copy(sendPumpDeviceStatus = false, sendStatusLine = false) },
                nowProvider = { Instant.ofEpochMilli(nowMillis) }
            )

            localDispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 100 + trendRate, 1, trendRate))

            val sgvBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_SGV)
            assertEquals("trendRate=$trendRate should produce one SGV broadcast", 1, sgvBroadcasts.size)

            val sgvObj = JSONArray(sgvBroadcasts.single().payload).getJSONObject(0)
            val direction = sgvObj.getString("direction")
            assertTrue(
                "trendRate=$trendRate produced invalid direction '$direction'",
                direction in VALID_XDRIP_DIRECTIONS
            )
        }
    }

    // ---------------------------------------------------------------
    // 2. Treatment broadcast — xDrip NSClientReceiver.toTreatmentJSON()
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_treatmentInitiatedBroadcast_matchesXdripSchema() {
        config = config.copy(sendCgmSgv = false, sendPumpDeviceStatus = false, sendStatusLine = false)
        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 42, 0))

        // xDrip receives both NEW_TREATMENT and NEW_FOOD
        val treatmentBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_TREATMENT)
        val foodBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_FOOD)
        assertEquals("one treatment broadcast", 1, treatmentBroadcasts.size)
        assertEquals("one food broadcast", 1, foodBroadcasts.size)

        val bc = treatmentBroadcasts.single()
        assertEquals("treatments", bc.extraKey)

        // xDrip parses: JSONArray(extras.getString("treatments")).getJSONObject(i)
        val arr = JSONArray(bc.payload)
        assertTrue("payload must be non-empty array", arr.length() > 0)
        val trtMap = jsonToMap(arr.getJSONObject(0).toString())

        // xDrip reads mills or date: if neither is present, timestamp=0 → treatment rejected
        val mills = trtMap["mills"] ?: trtMap["date"]
        assertNotNull("xDrip reads 'mills'/'date' for timestamp — null causes rejection", mills)
        assertTrue("timestamp must be > 0 or treatment rejected", (mills as Number).toLong() > 0)

        // xDrip reads eventType
        assertNotNull("eventType required", trtMap["eventType"])
        assertEquals("Bolus", trtMap["eventType"].toString())

        // Food broadcast has same extra key
        assertEquals("treatments", foodBroadcasts.single().extraKey)
    }

    @Test
    fun fullPipeline_treatmentStatusBroadcast_matchesXdripSchema() {
        config = config.copy(sendCgmSgv = false, sendPumpDeviceStatus = false, sendStatusLine = false)

        // Send a status update (bolus in progress with insulin amount)
        nowMillis += 1000 // advance time to avoid dedup
        dispatcher.onReceiveMessage(CurrentBolusStatusResponse(0, 77, 1710001234, 2300, 0, 0))

        val treatmentBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_TREATMENT)
        assertEquals("one treatment broadcast", 1, treatmentBroadcasts.size)

        val arr = JSONArray(treatmentBroadcasts.single().payload)
        val trtMap = jsonToMap(arr.getJSONObject(0).toString())

        // mills must exist and be positive
        val mills = trtMap["mills"] ?: trtMap["date"]
        assertNotNull("mills/date required for xDrip treatment timestamp", mills)
        assertTrue("timestamp > 0", (mills as Number).toLong() > 0)

        // insulin field present and positive
        assertNotNull("insulin field expected for bolus status", trtMap["insulin"])
        assertTrue("insulin > 0", (trtMap["insulin"] as Number).toDouble() > 0)
        assertEquals(InsulinUnit.from1000To1(2300), (trtMap["insulin"] as Number).toDouble(), 0.0001)

        // created_at should be an ISO string
        assertNotNull("created_at expected", trtMap["created_at"])
    }

    // ---------------------------------------------------------------
    // 3. Device status broadcast — xDrip AAPSStatusHandler / NSDeviceStatus
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_deviceStatusBroadcast_matchesXdripSchema() {
        config = config.copy(sendCgmSgv = false, sendTreatments = false, sendStatusLine = false)

        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(ControlIQIOBResponse(1234, 0, 0, 0, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(InsulinStatusResponse(80, 0, 20))
        nowMillis += 1000
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 900, 0))

        val dsBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS)
        assertTrue("at least one device status broadcast", dsBroadcasts.isNotEmpty())

        // Validate the last (most complete) device status broadcast
        val bc = dsBroadcasts.last()
        assertEquals("devicestatus", bc.extraKey)

        // xDrip parses via Gson into NSDeviceStatus which has:
        //   pump.battery.percent, pump.reservoir, pump.iob.bolusiob, pump.basal
        val obj = JSONObject(bc.payload)
        assertTrue("must have 'pump' object", obj.has("pump"))
        assertTrue("must have 'created_at'", obj.has("created_at"))

        val pump = obj.getJSONObject("pump")

        // xDrip reads pump.battery.percent (int)
        assertTrue("pump must have 'battery'", pump.has("battery"))
        val batteryPercent = pump.getJSONObject("battery").getInt("percent")
        assertEquals(55, batteryPercent)

        // xDrip reads pump.reservoir (double)
        assertTrue("pump must have 'reservoir'", pump.has("reservoir"))
        assertEquals(80, pump.getInt("reservoir"))

        // pump.iob.bolusiob — currently ignored by xDrip but structurally correct
        assertTrue("pump must have 'iob'", pump.has("iob"))
        assertEquals(
            InsulinUnit.from1000To1(1234),
            pump.getJSONObject("iob").getDouble("bolusiob"),
            0.0001
        )

        // pump.basal
        assertTrue("pump must have 'basal'", pump.has("basal"))
        assertEquals(InsulinUnit.from1000To1(900), pump.getDouble("basal"), 0.0001)
    }

    // ---------------------------------------------------------------
    // 4. Status line — xDrip ExternalStatusService regex parsing
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_statusLine_matchesXdripRegex() {
        config = config.copy(sendCgmSgv = false, sendTreatments = false, sendPumpDeviceStatus = false)

        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 900, 0))

        val slBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_EXTERNAL_STATUSLINE)
        assertTrue("at least one statusline broadcast", slBroadcasts.isNotEmpty())

        val bc = slBroadcasts.last()
        assertEquals("com.eveningoutpost.dexdrip.ExternalStatusline", bc.action)
        assertEquals("com.eveningoutpost.dexdrip.Extras.Statusline", bc.extraKey)

        val statusline = bc.payload

        // xDrip getAbsoluteBRDouble() uses regex: ([0-9.,]+U/h) — must match
        val absoluteBrPattern = Regex("""([0-9.,]+U/h)""")
        assertTrue(
            "statusline '$statusline' must match xDrip absolute basal regex ([0-9.,]+U/h)",
            absoluteBrPattern.containsMatchIn(statusline)
        )

        // xDrip getTBRInt() uses regex: ([0-9]+%) — must NOT match
        // (battery was previously formatted as "Batt:55%" which falsely matched TBR)
        val tbrPattern = Regex("""([0-9]+%)""")
        assertTrue(
            "statusline '$statusline' must NOT match xDrip TBR regex ([0-9]+%) — " +
                "battery percent would be misinterpreted as temp basal rate",
            !tbrPattern.containsMatchIn(statusline)
        )
    }

    // ---------------------------------------------------------------
    // 5. Full realistic session — all broadcast types sent
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_realisticSession_sendsAllBroadcastTypes() {
        // Simulate a realistic pump data session with all response types
        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 85, 0, 0, 0, 0, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(ControlIQIOBResponse(2500, 0, 0, 0, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(InsulinStatusResponse(150, 0, 20))
        nowMillis += 1000
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 750, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 120, 1, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 99, 0))

        // Verify all expected broadcast actions were sent
        val actions = broadcasts.map { it.action }.toSet()
        assertTrue("SGV broadcast sent", XdripBroadcastSender.ACTION_NEW_SGV in actions)
        assertTrue("Device status broadcast sent", XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS in actions)
        assertTrue("Treatment broadcast sent", XdripBroadcastSender.ACTION_NEW_TREATMENT in actions)
        assertTrue("Food broadcast sent", XdripBroadcastSender.ACTION_NEW_FOOD in actions)
        assertTrue("Statusline broadcast sent", XdripBroadcastSender.ACTION_EXTERNAL_STATUSLINE in actions)

        // Verify correct extra keys for each action
        broadcasts.filter { it.action == XdripBroadcastSender.ACTION_NEW_SGV }
            .forEach { assertEquals("sgvs", it.extraKey) }
        broadcasts.filter { it.action == XdripBroadcastSender.ACTION_NEW_TREATMENT }
            .forEach { assertEquals("treatments", it.extraKey) }
        broadcasts.filter { it.action == XdripBroadcastSender.ACTION_NEW_FOOD }
            .forEach { assertEquals("treatments", it.extraKey) }
        broadcasts.filter { it.action == XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS }
            .forEach { assertEquals("devicestatus", it.extraKey) }
        broadcasts.filter { it.action == XdripBroadcastSender.ACTION_EXTERNAL_STATUSLINE }
            .forEach { assertEquals("com.eveningoutpost.dexdrip.Extras.Statusline", it.extraKey) }
    }

    // ---------------------------------------------------------------
    // 6. Disabled config — no broadcasts at all
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_disabledConfig_sendsNoBroadcasts() {
        config = XdripSyncConfig(enabled = false)

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 145, 1, 2))
        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 42, 0))

        assertTrue("no broadcasts when disabled", broadcasts.isEmpty())
    }

    // ---------------------------------------------------------------
    // 7. SGV also triggers device status with CGM field
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_sgvAlsoUpdatesDeviceStatusCgm() {
        config = config.copy(sendTreatments = false, sendStatusLine = false)

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 180, 1, -1))

        val dsBroadcasts = broadcastsWithAction(XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS)
        assertTrue("SGV event also sends device status", dsBroadcasts.isNotEmpty())

        val obj = JSONObject(dsBroadcasts.last().payload)
        assertTrue("device status should include cgm", obj.has("cgm"))
        assertEquals(180, obj.getJSONObject("cgm").getInt("sgv"))
    }

    // ---------------------------------------------------------------
    // 8. Selective config — only enabled payload types broadcast
    // ---------------------------------------------------------------

    @Test
    fun fullPipeline_selectiveConfig_onlySendsEnabledPayloads() {
        config = XdripSyncConfig(
            enabled = true,
            sendCgmSgv = true,
            sendPumpDeviceStatus = false,
            sendTreatments = false,
            sendStatusLine = false
        )

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 145, 1, 2))
        nowMillis += 1000
        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        nowMillis += 1000
        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 42, 0))

        val actions = broadcasts.map { it.action }.toSet()
        assertTrue("SGV enabled → sent", XdripBroadcastSender.ACTION_NEW_SGV in actions)
        assertTrue("Device status disabled → not sent", XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS !in actions)
        assertTrue("Treatment disabled → not sent", XdripBroadcastSender.ACTION_NEW_TREATMENT !in actions)
        assertTrue("Statusline disabled → not sent", XdripBroadcastSender.ACTION_EXTERNAL_STATUSLINE !in actions)
    }
}
