package com.jwoglom.controlx2.util

// the pump sid is the short pump serial number -- the last 3 numeric characters --
// which is contained in the bluetooth device name like "tslim X2 ***123"
fun extractPumpSid(pumpDeviceName: String): Int? {
    return pumpDeviceName.substringAfterLast("*").toIntOrNull()
}