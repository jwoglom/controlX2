package com.jwoglom.controlx2

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Catches the regression class behind the original Android 11 field bug:
 * the runtime permission request includes a permission that isn't declared
 * in the manifest at the current SDK level (or vice versa, declared but
 * never asked for). Runs at every supported device SDK; targetSdk is read
 * from the merged manifest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36])
class RuntimeMatchesManifestTest {

    @Test
    fun `runtime permission requests are declared in the manifest`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val targetSdk = ctx.applicationInfo.targetSdkVersion
        val sdk = Build.VERSION.SDK_INT

        val declared = ctx.packageManager
            .getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()

        val requested = BluetoothPermissions.required(sdk, targetSdk).toSet()
        val undeclared = requested - declared

        assertTrue(
            "Runtime requests permissions not declared in the manifest at sdk=$sdk " +
                "targetSdk=$targetSdk: $undeclared",
            undeclared.isEmpty(),
        )
    }
}
