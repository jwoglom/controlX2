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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XdripMessageDispatcherTest {
    private data class SentValue<T>(val payload: T, val minimumIntervalSeconds: Int?)

    private class FakeBroadcaster : XdripBroadcaster {
        val sgvPayloads = mutableListOf<SentValue<String>>()
        val deviceStatusPayloads = mutableListOf<SentValue<String>>()
        val treatmentPayloads = mutableListOf<SentValue<String>>()
        val treatmentFoodFlags = mutableListOf<Boolean>()
        val statuslinePayloads = mutableListOf<SentValue<String>>()

        override fun sendSgv(sgvsJsonArrayString: String, minimumIntervalSeconds: Int?): Boolean {
            sgvPayloads.add(SentValue(sgvsJsonArrayString, minimumIntervalSeconds))
            return true
        }

        override fun sendDeviceStatus(deviceStatusJsonString: String, minimumIntervalSeconds: Int?): Boolean {
            deviceStatusPayloads.add(SentValue(deviceStatusJsonString, minimumIntervalSeconds))
            return true
        }

        override fun sendTreatments(
            treatmentsJsonString: String,
            minimumIntervalSeconds: Int?,
            alsoSendNewFood: Boolean
        ): Boolean {
            treatmentPayloads.add(SentValue(treatmentsJsonString, minimumIntervalSeconds))
            treatmentFoodFlags.add(alsoSendNewFood)
            return true
        }

        override fun sendExternalStatusline(statusline: String, minimumIntervalSeconds: Int?): Boolean {
            statuslinePayloads.add(SentValue(statusline, minimumIntervalSeconds))
            return true
        }
    }

    @Test
    fun onReceiveMessage_mapsSgvWithXdripFieldNames() {
        val broadcaster = FakeBroadcaster()
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = {
                XdripSyncConfig(
                    enabled = true,
                    cgmSgvMinimumIntervalSeconds = 42,
                    sendPumpDeviceStatus = false,
                    sendTreatments = false,
                    sendStatusLine = false
                )
            },
            nowProvider = { now }
        )

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1710000000, 145, 1, 2))

        val sgvJson = JSONArray(broadcaster.sgvPayloads.single().payload).getJSONObject(0)
        assertEquals(42, broadcaster.sgvPayloads.single().minimumIntervalSeconds)
        assertEquals(145, sgvJson.getInt("mgdl"))
        assertEquals(now.toEpochMilli(), sgvJson.getLong("mills"))
        assertEquals("SingleUp", sgvJson.getString("direction"))
    }

    @Test
    fun onReceiveMessage_mapsPumpStatusResponsesIntoDeviceStatusAndStatusline() {
        val broadcaster = FakeBroadcaster()
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = {
                XdripSyncConfig(
                    enabled = true,
                    sendCgmSgv = false,
                    pumpDeviceStatusMinimumIntervalSeconds = 7,
                    statusLineMinimumIntervalSeconds = 3,
                    sendTreatments = false
                )
            },
            nowProvider = { now }
        )

        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        now = Instant.parse("2026-01-01T00:01:00Z")
        dispatcher.onReceiveMessage(ControlIQIOBResponse(1234, 0, 0, 0, 0))
        now = Instant.parse("2026-01-01T00:02:00Z")
        dispatcher.onReceiveMessage(InsulinStatusResponse(80, 0, 20))
        now = Instant.parse("2026-01-01T00:03:00Z")
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 900, 0))

        val latestStatus = JSONObject(broadcaster.deviceStatusPayloads.last().payload)
        assertEquals(7, broadcaster.deviceStatusPayloads.last().minimumIntervalSeconds)
        assertEquals(55, latestStatus.getJSONObject("pump").getJSONObject("battery").getInt("percent"))
        assertEquals(InsulinUnit.from1000To1(1234), latestStatus.getJSONObject("pump").getJSONObject("iob").getDouble("bolusiob"), 0.0001)
        assertEquals(80, latestStatus.getJSONObject("pump").getInt("reservoir"))
        assertEquals(InsulinUnit.from1000To1(900), latestStatus.getJSONObject("pump").getDouble("basal"), 0.0001)

        val latestStatusline = broadcaster.statuslinePayloads.last()
        assertEquals(3, latestStatusline.minimumIntervalSeconds)
        // Status line uses uppercase U/h for xDrip basal rate regex compatibility
        assertTrue(latestStatusline.payload.contains("U/h"))
        // Battery no longer has % suffix to avoid xDrip TBR misparse
        assertTrue(latestStatusline.payload.contains("Batt:55"))
        assertTrue(!latestStatusline.payload.contains("Batt:55%"))
        assertTrue(latestStatusline.payload.contains("Cart:80U"))
    }

    @Test
    fun onReceiveMessage_mapsTreatmentResponsesWithMillsTimestamp() {
        val broadcaster = FakeBroadcaster()
        val now = Instant.parse("2026-01-02T03:04:05Z")
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = {
                XdripSyncConfig(
                    enabled = true,
                    sendCgmSgv = false,
                    sendPumpDeviceStatus = false,
                    sendStatusLine = false,
                    treatmentsMinimumIntervalSeconds = 11
                )
            },
            nowProvider = { now }
        )

        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 77, 0))
        dispatcher.onReceiveMessage(CurrentBolusStatusResponse(0, 77, 1710001234, 2300, 0, 0))

        assertEquals(2, broadcaster.treatmentPayloads.size)
        assertTrue(broadcaster.treatmentFoodFlags.all { it })
        assertTrue(broadcaster.treatmentPayloads.all { it.minimumIntervalSeconds == 11 })

        val initiated = JSONArray(broadcaster.treatmentPayloads[0].payload).getJSONObject(0)
        assertEquals("Bolus", initiated.getString("eventType"))
        assertTrue(initiated.getString("notes").contains("bolusId=77"))
        assertEquals(now.toEpochMilli(), initiated.getLong("mills"))

        val status = JSONArray(broadcaster.treatmentPayloads[1].payload).getJSONObject(0)
        assertEquals(InsulinUnit.from1000To1(2300), status.getDouble("insulin"), 0.0001)
        assertTrue(status.getLong("mills") > 0)
        assertTrue(status.getString("notes").contains("status="))
    }

    @Test
    fun onEvent_disabledConfigSkipsAllBroadcasts() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = { XdripSyncConfig(enabled = false) }
        )

        dispatcher.onEvent(DispatchEvent.CgmSgv(120, 0))
        dispatcher.onEvent(DispatchEvent.PumpBattery(55))

        assertTrue(broadcaster.sgvPayloads.isEmpty())
        assertTrue(broadcaster.deviceStatusPayloads.isEmpty())
        assertTrue(broadcaster.statuslinePayloads.isEmpty())
        assertTrue(broadcaster.treatmentPayloads.isEmpty())
    }

    @Test
    fun onEvent_readsLatestConfigProviderValuesWithoutRestart() {
        val broadcaster = FakeBroadcaster()
        var config = XdripSyncConfig(enabled = true, sendCgmSgv = false)
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = { config },
            nowProvider = { Instant.parse("2026-01-01T00:00:00Z") }
        )

        dispatcher.onEvent(DispatchEvent.CgmSgv(120, 0))
        assertTrue(broadcaster.sgvPayloads.isEmpty())

        config = config.copy(sendCgmSgv = true, cgmSgvMinimumIntervalSeconds = 15)
        dispatcher.onEvent(DispatchEvent.CgmSgv(121, 1))

        assertEquals(1, broadcaster.sgvPayloads.size)
        assertEquals(15, broadcaster.sgvPayloads.single().minimumIntervalSeconds)
    }
}
