package com.jwoglom.controlx2

import android.Manifest
import android.os.Build

object BluetoothPermissions {
    fun required(sdkInt: Int, targetSdkVersion: Int): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            // Pre-S: BLUETOOTH_SCAN doesn't exist; BLE scan requires fine
            // location at runtime (coarse alone fails on API <= 30 with
            // SecurityException from BluetoothLeScanner.startScan).
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
}
