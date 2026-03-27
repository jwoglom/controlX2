package com.jwoglom.controlx2.sync.xdrip

import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusSnapshot
import com.jwoglom.controlx2.sync.xdrip.models.XdripSgvPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripTreatmentPayload
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XdripPayloadModelsTest {
    @Test
    fun sgvPayload_usesXdripFieldNames_andIncludesDirection() {
        val receivedAt = Instant.parse("2026-01-01T00:00:00Z")
        val payload = XdripSgvPayload.fromValue(123, 0, receivedAt)
        val obj = JSONArray(payload.toJsonArrayString()).getJSONObject(0)

        assertEquals("sgvs", XdripSgvPayload.EXTRA_KEY)
        assertEquals(123, obj.getInt("mgdl"))
        assertEquals(receivedAt.toEpochMilli(), obj.getLong("mills"))
        assertEquals("Flat", obj.getString("direction"))
    }

    @Test
    fun sgvPayload_directionFromTrendRate_mapsCorrectly() {
        assertEquals("DoubleDown", XdripSgvPayload.directionFromTrendRate(-4))
        assertEquals("DoubleDown", XdripSgvPayload.directionFromTrendRate(-3))
        assertEquals("SingleDown", XdripSgvPayload.directionFromTrendRate(-2))
        assertEquals("FortyFiveDown", XdripSgvPayload.directionFromTrendRate(-1))
        assertEquals("Flat", XdripSgvPayload.directionFromTrendRate(0))
        assertEquals("FortyFiveUp", XdripSgvPayload.directionFromTrendRate(1))
        assertEquals("SingleUp", XdripSgvPayload.directionFromTrendRate(2))
        assertEquals("DoubleUp", XdripSgvPayload.directionFromTrendRate(3))
        assertEquals("DoubleUp", XdripSgvPayload.directionFromTrendRate(5))
    }

    @Test
    fun deviceStatusSnapshot_mapsPumpResponses_withExpectedConversions() {
        val snapshot = XdripDeviceStatusSnapshot()
        val batteryAt = Instant.parse("2026-01-01T00:00:00Z")
        val iobAt = Instant.parse("2026-01-01T00:01:00Z")
        val reservoirAt = Instant.parse("2026-01-01T00:02:00Z")
        val basalAt = Instant.parse("2026-01-01T00:03:00Z")
        val sgvAt = Instant.parse("2026-01-01T00:04:00Z")

        snapshot.applyBatteryResponse(CurrentBatteryV2Response(0, 66, 0, 0, 0, 0, 0), batteryAt)
        snapshot.applyIobResponse(ControlIQIOBResponse(1450, 0, 0, 0, 0), iobAt)
        snapshot.applyReservoirResponse(InsulinStatusResponse(120, 0, 20), reservoirAt)
        snapshot.applyBasalResponse(CurrentBasalStatusResponse(0, 850, 0), basalAt)
        snapshot.applySgvValue(111, sgvAt)

        val json = JSONObject(snapshot.toPayload(createdAt = Instant.parse("2026-01-01T00:05:00Z")).toJsonString())

        assertEquals("devicestatus", XdripDeviceStatusPayload.EXTRA_KEY)
        assertEquals(66, json.getJSONObject("pump").getJSONObject("battery").getInt("percent"))
        assertEquals(InsulinUnit.from1000To1(1450), json.getJSONObject("pump").getJSONObject("iob").getDouble("bolusiob"), 0.0001)
        assertEquals(120, json.getJSONObject("pump").getInt("reservoir"))
        assertEquals(InsulinUnit.from1000To1(850), json.getJSONObject("pump").getDouble("basal"), 0.0001)
        assertEquals(111, json.getJSONObject("cgm").getInt("sgv"))
        assertEquals(batteryAt.toString(), json.getJSONObject("controlx2ReceivedAt").getString("battery"))
        assertEquals(iobAt.toString(), json.getJSONObject("controlx2ReceivedAt").getString("iob"))
        assertEquals(reservoirAt.toString(), json.getJSONObject("controlx2ReceivedAt").getString("reservoir"))
        assertEquals(basalAt.toString(), json.getJSONObject("controlx2ReceivedAt").getString("basal"))
        assertEquals(sgvAt.toString(), json.getJSONObject("controlx2ReceivedAt").getString("sgv"))
    }

    @Test
    fun treatmentPayload_includesMillsTimestamp() {
        val receivedAt = Instant.parse("2026-01-01T00:00:00Z")
        val initiated = XdripTreatmentPayload
            .fromInitiateBolusResponse(
                InitiateBolusResponse(0, 42, 0),
                receivedAt
            )
            .toJsonArrayString()
        val initiatedObj = JSONArray(initiated).getJSONObject(0)

        assertEquals("treatments", XdripTreatmentPayload.EXTRA_KEY)
        assertEquals("Bolus", initiatedObj.getString("eventType"))
        assertEquals(receivedAt.toEpochMilli(), initiatedObj.getLong("mills"))
        assertEquals(receivedAt.toString(), initiatedObj.getString("created_at"))
        assertTrue(initiatedObj.getString("notes").contains("bolusId=42"))

        val currentStatus = XdripTreatmentPayload
            .fromCurrentBolusStatusResponse(
                CurrentBolusStatusResponse(0, 77, 1710001234, 2300, 0, 0)
            )
            .toJsonArrayString()
        val statusObj = JSONArray(currentStatus).getJSONObject(0)
        assertEquals(InsulinUnit.from1000To1(2300), statusObj.getDouble("insulin"), 0.0001)
        assertTrue(statusObj.getLong("mills") > 0)
        assertTrue(statusObj.getString("notes").contains("bolusId=77"))
    }
}
