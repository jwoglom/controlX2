package com.jwoglom.controlx2.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import timber.log.Timber

@Composable
fun LastConnectionUpdatedTimestamp() {
    val ds = LocalDataStore.current

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()

    var pumpLastMessageTimestampRelative: String? by remember { mutableStateOf(null) }
    fun updatePumpLastMessageTsRelative() {
        pumpLastMessageTimestampRelative = pumpLastMessageTimestamp.value?.let {
            shortTimeAgo(it, nowThresholdSeconds = 1)
        }
        Timber.d("set pumpLastMessageTimestampRelative=%s", pumpLastMessageTimestampRelative)
    }

    LaunchedEffect (pumpConnected.value, pumpLastMessageTimestamp.value) {
        updatePumpLastMessageTsRelative()
    }

    LaunchedEffect (intervalOf(10)) {
        updatePumpLastMessageTsRelative()
    }

    when {
        pumpConnected.value == false -> when {
            pumpLastMessageTimestampRelative != null -> Line("Last connected: $pumpLastMessageTimestampRelative", bold = true)
            pumpLastConnectionTimestamp.value != null -> Line("Device last seen: ${pumpLastConnectionTimestamp.value}", bold = true)
            else -> {}
        }
        pumpLastMessageTimestamp.value == null -> {}
        pumpLastMessageTimestampRelative == null -> {}
        else -> {
            Line("Last updated: $pumpLastMessageTimestampRelative", bold = true)
        }

    }
}