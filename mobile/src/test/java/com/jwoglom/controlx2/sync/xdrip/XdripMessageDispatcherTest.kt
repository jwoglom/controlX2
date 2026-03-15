package com.jwoglom.controlx2.sync.xdrip

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XdripMessageDispatcherTest {
    private class FakeBroadcaster : XdripBroadcaster {
        val sgvPayloads = mutableListOf<String>()
        val deviceStatusPayloads = mutableListOf<String>()
        val treatmentPayloads = mutableListOf<String>()
        val statuslinePayloads = mutableListOf<String>()

        override fun sendSgv(sgvsJsonArrayString: String, minimumIntervalSeconds: Int?) =
            sgvPayloads.add(sgvsJsonArrayString)

        override fun sendDeviceStatus(deviceStatusJsonString: String, minimumIntervalSeconds: Int?) =
            deviceStatusPayloads.add(deviceStatusJsonString)

        override fun sendTreatments(
            treatmentsJsonString: String,
            minimumIntervalSeconds: Int?,
            alsoSendNewFood: Boolean
        ) = treatmentPayloads.add(treatmentsJsonString)

        override fun sendExternalStatusline(statusline: String, minimumIntervalSeconds: Int?) =
            statuslinePayloads.add(statusline)
    }

    @Test
    fun onEvent_pumpStatusIncludesPerFieldReceivedTimestamps() {
        val broadcaster = FakeBroadcaster()
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = { XdripSyncConfig(enabled = true) },
            nowProvider = { now }
        )

        dispatcher.onEvent(DispatchEvent.PumpBattery(50))
        now = Instant.parse("2026-01-01T00:01:00Z")
        dispatcher.onEvent(DispatchEvent.CgmSgv(120))

        val payload = JSONObject(broadcaster.deviceStatusPayloads.last())
        val receivedAt = payload.getJSONObject("controlx2ReceivedAt")
        assertEquals("2026-01-01T00:00:00Z", receivedAt.getString("battery"))
        assertEquals("2026-01-01T00:01:00Z", receivedAt.getString("sgv"))
    }

    @Test
    fun onEvent_disabledConfigSkipsAllBroadcasts() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = { XdripSyncConfig(enabled = false) }
        )

        dispatcher.onEvent(DispatchEvent.CgmSgv(120))
        dispatcher.onEvent(DispatchEvent.PumpBattery(55))

        assertTrue(broadcaster.sgvPayloads.isEmpty())
        assertTrue(broadcaster.deviceStatusPayloads.isEmpty())
        assertTrue(broadcaster.statuslinePayloads.isEmpty())
    }

    @Test
    fun onEvent_treatmentEventProducesTreatmentPayload() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = { XdripSyncConfig(enabled = true) },
            nowProvider = { Instant.parse("2026-01-02T03:04:05Z") }
        )

        dispatcher.onEvent(DispatchEvent.TreatmentInitiated(bolusId = 77, status = "SUCCESS"))

        val treatmentJson = JSONObject(broadcaster.treatmentPayloads.single().removePrefix("[").removeSuffix("]"))
        assertEquals("Bolus", treatmentJson.getString("eventType"))
        assertEquals(true, treatmentJson.getString("notes").contains("bolusId=77"))
    }
}
