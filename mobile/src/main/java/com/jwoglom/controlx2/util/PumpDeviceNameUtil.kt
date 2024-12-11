package com.jwoglom.controlx2.util

import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel

// the pump sid is the short pump serial number -- the last 3 numeric characters --
// which is contained in the bluetooth device name like:
// - "tslim X2 ***123" (t:slim X2)
// - "Tandem Mobi 123" (Mobi)
fun extractPumpSid(pumpDeviceName: String): Int? {
    return when (determinePumpModel(pumpDeviceName)) {
        KnownDeviceModel.TSLIM_X2 ->  pumpDeviceName.substringAfterLast("*").toIntOrNull()
        KnownDeviceModel.MOBI -> pumpDeviceName.substringAfterLast(" ").toIntOrNull()
        else -> null
    }
}

fun determinePumpModel(pumpDeviceName: String): KnownDeviceModel? {
    if (pumpDeviceName.startsWith("tslim X2 ***")) {
        return KnownDeviceModel.TSLIM_X2
    } else if (pumpDeviceName.startsWith("Tandem Mobi")) {
        return KnownDeviceModel.MOBI
    } else {
        return null
    }
}