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
 * map. The targetSdk is read from the merged manifest at runtime; if it
 * changes, GOLDEN must be updated to add entries for the new pair.
 *
 * Known gap surfaced by field bug reports on Android 11 (API 30) hardware:
 * BLE startScan() throws SecurityException because the API-30 branch only
 * requests ACCESS_COARSE_LOCATION. On API <= 30, fine location is required
 * for BLE scan. The current behavior is captured below as-is.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30, 31, 32, 33, 34, 35, 36])
class BluetoothPermissionsTest {

    @Test
    fun `required permissions match golden for current sdk`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val targetSdk = ctx.applicationInfo.targetSdkVersion
        val sdk = Build.VERSION.SDK_INT
        val key = sdk to targetSdk
        val expected = GOLDEN[key]
            ?: error("No golden entry for (deviceSdk=$sdk, targetSdk=$targetSdk). " +
                    "Update BluetoothPermissionsTest.GOLDEN to cover this pair.")
        val actual = BluetoothPermissions.required(sdk, targetSdk).toSet()
        assertEquals("deviceSdk=$sdk targetSdk=$targetSdk", expected, actual)
    }

    @Test
    fun `legacy targetSdk forces the pre-S permission model on a new device`() {
        // App targeting < 12 keeps the legacy permission model regardless of
        // the device API level — Android contract.
        val key = 33 to 30
        assertEquals(
            "deviceSdk=33 targetSdk=30",
            GOLDEN.getValue(key),
            BluetoothPermissions.required(sdkInt = 33, targetSdkVersion = 30).toSet(),
        )
    }

    companion object {
        private const val BT_SCAN = Manifest.permission.BLUETOOTH_SCAN
        private const val BT_CONNECT = Manifest.permission.BLUETOOTH_CONNECT
        private const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION

        // Fully-explicit map of (deviceSdk, targetSdk) -> expected runtime
        // permission request set. Covers every device SDK in [30..36] paired
        // with the manifest's current targetSdk (35), plus the legacy-target
        // entry (33, 30) used by the contract test below.
        private val GOLDEN: Map<Pair<Int, Int>, Set<String>> = mapOf(
            (30 to 35) to setOf(COARSE),
            (31 to 35) to setOf(BT_SCAN, BT_CONNECT, COARSE),
            (32 to 35) to setOf(BT_SCAN, BT_CONNECT, COARSE),
            (33 to 35) to setOf(BT_SCAN, BT_CONNECT, COARSE),
            (34 to 35) to setOf(BT_SCAN, BT_CONNECT, COARSE),
            (35 to 35) to setOf(BT_SCAN, BT_CONNECT, COARSE),
            (36 to 35) to setOf(BT_SCAN, BT_CONNECT, COARSE),
            (33 to 30) to setOf(COARSE),
        )
    }
}
