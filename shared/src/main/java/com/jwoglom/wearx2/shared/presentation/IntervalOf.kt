package com.jwoglom.wearx2.shared.presentation

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun intervalOf(seconds: Int): Int {
    var value by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())

        val runnable = {
            value += 1
        }

        handler.postDelayed(runnable, (seconds * 1000).toLong())

        onDispose {
            handler.removeCallbacks(runnable)
        }
    }

    return value
}