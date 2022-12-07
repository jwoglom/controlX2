package com.jwoglom.wearx2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.jwoglom.wearx2.presentation.navigation.Screen

class BolusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("route", Screen.Bolus.route))
        finish()
    }
}