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
 * Locks in the merged-manifest <uses-permission> set declared by the wear
 * app at every supported SDK level. Robolectric runs each test method once
 * per SDK configured below, and the framework parser honors
 * android:maxSdkVersion on each <uses-permission> (also auto-strips perms
 * the platform considers obsolete at the target SDK, e.g. BLUETOOTH and
 * BLUETOOTH_ADMIN past API 30).
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
        val expected = GOLDEN.firstOrNull { it.deviceSdk == sdk }?.expected
            ?: error("No GOLDEN entry for deviceSdk=$sdk. " +
                    "Add a Case to ManifestPermissionsTest.GOLDEN.")
        assertEquals("manifest requestedPermissions for sdk=$sdk", expected, actual)
    }

    companion object {
        private val ALWAYS = setOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "com.google.android.c2dm.permission.RECEIVE",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_COARSE_LOCATION",
            // Merged in by libraries at every SDK
            "android.permission.BLUETOOTH_ADVERTISE", // blessed-android
            "android.permission.ACCESS_FINE_LOCATION", // blessed-android (no SDK cap)
            "com.google.android.wearable.permission.BIND_WATCH_FACE_CONTROL", // wear watchface
            "com.jwoglom.controlx2.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", // androidx.core
        )

        // Auto-stripped by the platform past API 30.
        private val LEGACY_ONLY = setOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
        )

        private data class Case(val deviceSdk: Int, val expected: Set<String>)

        private val GOLDEN: List<Case> = listOf(
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
