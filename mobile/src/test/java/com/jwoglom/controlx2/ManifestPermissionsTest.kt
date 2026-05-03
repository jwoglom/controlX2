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
 * Locks in the merged-manifest <uses-permission> set declared by the mobile
 * app at every supported SDK level. Robolectric runs each test method once
 * per SDK configured below, and the framework parser honors
 * android:maxSdkVersion on each <uses-permission> (also auto-strips perms
 * the platform considers obsolete at the target SDK, e.g. BLUETOOTH and
 * BLUETOOTH_ADMIN past API 30).
 *
 * If a permission is added, removed, or its SDK gating changes — including
 * via library manifest merging — this test fails and the GOLDEN below must
 * be updated deliberately.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36])
class ManifestPermissionsTest {

    @Test
    fun `manifest permissions match golden for current sdk`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val info = ctx.packageManager.getPackageInfo(
            ctx.packageName, PackageManager.GET_PERMISSIONS,
        )
        val actual = info.requestedPermissions?.toSet().orEmpty()
        val sdk = Build.VERSION.SDK_INT
        val expected = GOLDEN.firstOrNull { it.deviceSdk == sdk }?.expected
            ?: error("No GOLDEN entry for deviceSdk=$sdk. " +
                    "Add a Case to ManifestPermissionsTest.GOLDEN.")
        assertEquals("manifest requestedPermissions for sdk=$sdk", expected, actual)
    }

    companion object {
        // Always present at every supported SDK.
        private val ALWAYS = setOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.INTERNET",
            // Merged in by libraries at every SDK
            "android.permission.BLUETOOTH_ADVERTISE", // blessed-android
            "com.jwoglom.controlx2.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", // androidx.core
        )

        // Only present on API <= 30. BLUETOOTH and BLUETOOTH_ADMIN are
        // auto-stripped by the platform past API 30; ACCESS_FINE_LOCATION
        // is gated via android:maxSdkVersion="30" in our manifest.
        private val LEGACY_ONLY = setOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_FINE_LOCATION",
        )

        private data class Case(val deviceSdk: Int, val expected: Set<String>)

        private val GOLDEN: List<Case> = listOf(
            Case(deviceSdk = 26, expected = ALWAYS + LEGACY_ONLY),
            Case(deviceSdk = 27, expected = ALWAYS + LEGACY_ONLY),
            Case(deviceSdk = 28, expected = ALWAYS + LEGACY_ONLY),
            Case(deviceSdk = 29, expected = ALWAYS + LEGACY_ONLY),
            Case(deviceSdk = 30, expected = ALWAYS + LEGACY_ONLY),
            Case(deviceSdk = 31, expected = ALWAYS),
            Case(deviceSdk = 32, expected = ALWAYS),
            Case(deviceSdk = 33, expected = ALWAYS),
            Case(deviceSdk = 34, expected = ALWAYS),
            Case(deviceSdk = 35, expected = ALWAYS),
            Case(deviceSdk = 36, expected = ALWAYS),
        )
    }
}
