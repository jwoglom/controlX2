package com.jwoglom.controlx2.sync.xdrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripBroadcastSenderTest {
    @Test
    fun sendSgv_suppressesDuplicatePayloads() {
        val sent = mutableListOf<Triple<String, String, String>>()
        var now = 1000L
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload -> sent.add(Triple(action, extra, payload)) },
            nowMillisFn = { now }
        )

        assertTrue(sender.sendSgv("[{\"sgv\":100}]"))
        assertFalse(sender.sendSgv("[{\"sgv\":100}]"))

        assertEquals(1, sent.size)
        assertEquals(XdripBroadcastSender.ACTION_NEW_SGV, sent[0].first)
    }

    @Test
    fun sendDeviceStatus_obeysMinimumInterval() {
        val sent = mutableListOf<Triple<String, String, String>>()
        var now = 1000L
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload -> sent.add(Triple(action, extra, payload)) },
            nowMillisFn = { now }
        )

        assertTrue(sender.sendDeviceStatus("{\"a\":1}", minimumIntervalSeconds = 5))
        now = 3000L
        assertFalse(sender.sendDeviceStatus("{\"a\":2}", minimumIntervalSeconds = 5))
        now = 7000L
        assertTrue(sender.sendDeviceStatus("{\"a\":2}", minimumIntervalSeconds = 5))

        assertEquals(2, sent.size)
    }

    @Test
    fun sendTreatments_sendsNewFoodWhenEnabled() {
        val sent = mutableListOf<Triple<String, String, String>>()
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload -> sent.add(Triple(action, extra, payload)) }
        )

        assertTrue(sender.sendTreatments("[{\"eventType\":\"Bolus\"}]", alsoSendNewFood = true))

        assertEquals(2, sent.size)
        assertEquals(XdripBroadcastSender.ACTION_NEW_TREATMENT, sent[0].first)
        assertEquals(XdripBroadcastSender.ACTION_NEW_FOOD, sent[1].first)
    }
}
