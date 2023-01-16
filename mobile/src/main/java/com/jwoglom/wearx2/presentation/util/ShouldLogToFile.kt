package com.jwoglom.wearx2.presentation.util

import android.content.Context
import android.util.Log
import com.jwoglom.wearx2.Prefs

fun ShouldLogToFile(context: Context): (Int, String) -> Boolean {
    return {level, tag ->
        if (Prefs(context).verboseFileLoggingEnabled()) {
            true
        } else if (Prefs(context).onlySnoopBluetoothEnabled()) {
            level >= Log.INFO && (
                tag.startsWith("L:BTResponseParser") ||
                tag.startsWith("L:Messages") ||
                tag.startsWith("TandemBluetooth") ||
                tag.startsWith("TandemPump")
            )
        } else {
            level >= Log.WARN ||
            level >= Log.INFO && (
                tag.startsWith("L:BTResponseParser") ||
                tag.startsWith("L:Messages") ||
                tag.startsWith("TandemBluetooth") ||
                tag.startsWith("TandemPump") ||
                tag.startsWith("CommService")
            )
        }
    }
}