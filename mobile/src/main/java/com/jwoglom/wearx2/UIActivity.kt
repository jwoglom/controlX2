package com.jwoglom.wearx2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.MobileApp
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.theme.WearX2Theme

var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class UIActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileApp(
                startDestination = determineStartDestination()
            )
        }
    }
}

fun determineStartDestination(): String {
    return Screen.FirstLaunch.route
}