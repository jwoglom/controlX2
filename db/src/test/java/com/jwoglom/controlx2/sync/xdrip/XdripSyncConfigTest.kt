package com.jwoglom.controlx2.sync.xdrip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripSyncConfigTest {
    @Test
    fun requiresReloadComparedTo_payloadAndIntervalUpdatesDoNotRequireReload() {
        val oldConfig = XdripSyncConfig(enabled = true)
        val newConfig = oldConfig.copy(
            sendCgmSgv = false,
            sendPumpDeviceStatus = false,
            sendTreatments = false,
            sendStatusLine = false,
            cgmSgvMinimumIntervalSeconds = 30,
            pumpDeviceStatusMinimumIntervalSeconds = 45,
            treatmentsMinimumIntervalSeconds = 60,
            statusLineMinimumIntervalSeconds = 120
        )

        assertFalse(newConfig.requiresReloadComparedTo(oldConfig))
    }

    @Test
    fun requiresReloadComparedTo_enabledToggleRequiresReload() {
        val disabled = XdripSyncConfig(enabled = false)
        val enabled = disabled.copy(enabled = true)

        assertTrue(enabled.requiresReloadComparedTo(disabled))
        assertTrue(disabled.requiresReloadComparedTo(enabled))
    }
}
