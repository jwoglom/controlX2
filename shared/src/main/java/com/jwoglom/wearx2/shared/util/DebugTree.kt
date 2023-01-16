package com.jwoglom.wearx2.shared.util

import android.content.Context
import android.util.Log
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID
import java.io.File
import java.time.Instant

const val failedForCharacteristicMsg = "writeCharacteristic failed for characteristic: "
class DebugTree(
    val prefix: String,
    val context: Context,
    val logToFile: Boolean,
    val shouldLog: (Int, String) -> Boolean,
    val writeCharacteristicFailedCallback: (String) -> Unit = {},
) : timber.log.Timber.DebugTree() {
    private var logFile: File? = null

    /**
     * To view these logs in `adb shell`:
     * $ run-as com.jwoglom.wearx2
     * $ tail -f /data/user/0/com.jwoglom.wearx2/files/debugLog-MUA.txt
     */
    init {
        if (logToFile) {
            logFile = File("${context.filesDir}/debugLog-$prefix.txt")
            logFile?.createNewFile()
            log(Log.INFO, "DebugTree", "Writing to debugLog: ${logFile?.absolutePath}", null)

        }
    }
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // HACK: a bug in timber / Android 13 does not properly notify for characteristic write failures
        if (tag == "BluetoothPeripheral") {
            if (message.startsWith(failedForCharacteristicMsg)) {
                writeCharacteristicFailedCallback(message.removePrefix(failedForCharacteristicMsg).trim())
            }
        }
        super.log(priority, "WearX2:${prefix}:$tag", message, t)
        tag?.let {
            if (shouldLog(priority, it)) {
                logFile?.appendText("${Instant.now()},$prefix,$tag,$priority,$message,$t\n")
            }
        }
    }
}