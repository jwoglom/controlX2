package com.jwoglom.controlx2

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Locks in the runtime permission set requested by the mobile app per
 * (deviceSdk, targetSdk) pair. Drives BluetoothPermissions.required() at
 * every supported device SDK and asserts against a fully-explicit golden
 * table. The targetSdk is read from the merged manifest at runtime; if it
 * changes, GOLDEN must grow new entries for the new pair.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36])
class BluetoothPermissionsTest {

    @Test
    fun `required permissions match golden for current sdk`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val targetSdk = ctx.applicationInfo.targetSdkVersion
        val sdk = Build.VERSION.SDK_INT
        val expected = lookup(sdk, targetSdk)
            ?: error("No GOLDEN entry for deviceSdk=$sdk targetSdk=$targetSdk. " +
                    "Add a Case to BluetoothPermissionsTest.GOLDEN.")
        val actual = BluetoothPermissions.required(sdk, targetSdk).toSet()
        assertEquals("deviceSdk=$sdk targetSdk=$targetSdk", expected, actual)
    }

    @Test
    fun `legacy targetSdk forces the pre-S permission model on a new device`() {
        // App targeting < S keeps the legacy permission model regardless of
        // the device API level — Android contract.
        val expected = lookup(deviceSdk = 33, targetSdk = 30)!!
        val actual = BluetoothPermissions.required(sdkInt = 33, targetSdkVersion = 30).toSet()
        assertEquals("deviceSdk=33 targetSdk=30", expected, actual)
    }

    private fun lookup(deviceSdk: Int, targetSdk: Int): Set<String>? =
        GOLDEN.firstOrNull { it.deviceSdk == deviceSdk && it.targetSdk == targetSdk }?.expected

    companion object {
        private const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
        private const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val BT_SCAN = Manifest.permission.BLUETOOTH_SCAN
        private const val BT_CONNECT = Manifest.permission.BLUETOOTH_CONNECT

        private data class Case(
            val deviceSdk: Int,
            val targetSdk: Int,
            val expected: Set<String>,
        )

        // Fully-explicit table. One entry per (deviceSdk, targetSdk) pair
        // that any test exercises. Pre-S devices (or legacy-target apps on
        // any device): need fine + coarse location for BLE scan. S+ device
        // with S+ target: BLUETOOTH_SCAN/CONNECT + coarse.
        private val GOLDEN: List<Case> = listOf(
            Case(deviceSdk = 26, targetSdk = 35, expected = setOf(FINE, COARSE)),
            Case(deviceSdk = 27, targetSdk = 35, expected = setOf(FINE, COARSE)),
            Case(deviceSdk = 28, targetSdk = 35, expected = setOf(FINE, COARSE)),
            Case(deviceSdk = 29, targetSdk = 35, expected = setOf(FINE, COARSE)),
            Case(deviceSdk = 30, targetSdk = 35, expected = setOf(FINE, COARSE)),
            Case(deviceSdk = 31, targetSdk = 35, expected = setOf(BT_SCAN, BT_CONNECT, COARSE)),
            Case(deviceSdk = 32, targetSdk = 35, expected = setOf(BT_SCAN, BT_CONNECT, COARSE)),
            Case(deviceSdk = 33, targetSdk = 35, expected = setOf(BT_SCAN, BT_CONNECT, COARSE)),
            Case(deviceSdk = 34, targetSdk = 35, expected = setOf(BT_SCAN, BT_CONNECT, COARSE)),
            Case(deviceSdk = 35, targetSdk = 35, expected = setOf(BT_SCAN, BT_CONNECT, COARSE)),
            Case(deviceSdk = 36, targetSdk = 35, expected = setOf(BT_SCAN, BT_CONNECT, COARSE)),
            // Legacy-target contract test below.
            Case(deviceSdk = 33, targetSdk = 30, expected = setOf(FINE, COARSE)),
        )
    }
}
