package com.jwoglom.controlx2

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Locks in the manifest <uses-permission> set declared by the mobile app at
 * each supported SDK level. Robolectric runs each test method once per SDK
 * configured below, and the framework parser itself honors
 * android:minSdkVersion / android:maxSdkVersion on each <uses-permission>.
 *
 * If a permission is added, removed, or its SDK gating changes — including
 * via library manifest merging — this test fails and the golden below must
 * be updated deliberately.
 *
 * Known gap surfaced by field bug reports on Android 11 (API 30) hardware:
 * BLE startScan() throws SecurityException because ACCESS_FINE_LOCATION is
 * not declared. On API <= 30, fine location is required for BLE scan;
 * BLUETOOTH_SCAN does not exist yet and ACCESS_COARSE_LOCATION is not
 * sufficient. The current declared set is captured below as-is.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30, 31, 32, 33, 34, 35, 36])
class ManifestPermissionsTest {

    @Test
    fun `manifest permissions match golden for current sdk`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val info = ctx.packageManager.getPackageInfo(
            ctx.packageName, PackageManager.GET_PERMISSIONS,
        )
        val actual = info.requestedPermissions?.toSet().orEmpty()
        val sdk = Build.VERSION.SDK_INT
        assertEquals(
            "manifest requestedPermissions for sdk=$sdk",
            EXPECTED.getValue(sdk),
            actual,
        )
    }

    companion object {
        private val ALWAYS = setOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.INTERNET",
        )

        // Gated by android:minSdkVersion="34" in the manifest.
        private const val FSCD = "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"

        private val EXPECTED: Map<Int, Set<String>> = mapOf(
            30 to ALWAYS,
            31 to ALWAYS,
            32 to ALWAYS,
            33 to ALWAYS,
            34 to ALWAYS + FSCD,
            35 to ALWAYS + FSCD,
            36 to ALWAYS + FSCD,
        )
    }
}
