package com.jwoglom.wearx2.shared.util

import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID

const val failedForCharacteristicMsg = "writeCharacteristic failed for characteristic: "
class DebugTree(
    val prefix: String,
    val writeCharacteristicFailedCallback: (String) -> Unit = {},
) : timber.log.Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // HACK: a bug in timber / Android 13 does not properly notify for characteristic write failures
        if (tag == "BluetoothPeripheral") {
            if (message.startsWith(failedForCharacteristicMsg)) {
                writeCharacteristicFailedCallback(message.removePrefix(failedForCharacteristicMsg).trim())
            }
        }
        super.log(priority, "WearX2:${prefix}:$tag", message, t)

    }
}