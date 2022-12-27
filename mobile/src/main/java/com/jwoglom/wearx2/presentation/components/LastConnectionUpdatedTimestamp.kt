package com.jwoglom.wearx2.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.shared.presentation.intervalOf
import com.jwoglom.wearx2.shared.util.shortTimeAgo
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

    Line(
        when {
            pumpConnected.value == false -> "Connecting: ${setupStage.value}\nLast connected: $pumpLastMessageTimestampRelative"
            pumpLastMessageTimestamp.value == null -> ""
            pumpLastMessageTimestampRelative == null -> ""
            else -> "Last updated: $pumpLastMessageTimestampRelative"

        }, bold = true
    )
}