package com.jwoglom.wearx2.shared.util

import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID

class DebugTree(
    val prefix: String,
    val writeCharacteristicFailedCallback: () -> Unit = {},
) : timber.log.Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // HACK: a bug in timber does not properly notify for characteristic write failures,
        // which we need to detect when we fail to write to the authentication characteristic,
        // which is how the pump tells us that we are already authenticated
        if (tag == "BluetoothPeripheral" && message == "writeCharacteristic failed for characteristic: ${CharacteristicUUID.AUTHORIZATION_CHARACTERISTICS}") {
            writeCharacteristicFailedCallback()
        }
        super.log(priority, "WearX2:${prefix}:$tag", message, t)
    }
}