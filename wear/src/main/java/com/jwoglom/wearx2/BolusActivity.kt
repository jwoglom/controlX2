package com.jwoglom.wearx2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jwoglom.wearx2.presentation.navigation.Screen

/**
 * Launches MainActivity with the bolus default route (post-loading)
 */
class BolusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(R.style.MainTheme) // clean up from splash screen icon

        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("route", Screen.Bolus.route))
        finish()
    }
}