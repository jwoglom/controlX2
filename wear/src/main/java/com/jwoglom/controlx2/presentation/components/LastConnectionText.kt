package com.jwoglom.controlx2.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo

/**
 * Watch port of [com.jwoglom.controlx2.presentation.components.LastConnectionUpdatedTimestamp]
 * from `:mobile`. Reads `pumpConnected`, `pumpLastConnectionTimestamp`, and
 * `pumpLastMessageTimestamp` from the Activity-scoped [com.jwoglom.controlx2.presentation.DataStore]
 * and renders a single compact line suitable for Wear OS screens.
 */
@Composable
fun LastConnectionText(
    modifier: Modifier = Modifier,
) {
    val ds = LocalDataStore.current
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()

    // intervalOf itself triggers a recomposition every 10s via an internal
    // mutable state it reads, so the relative timestamp refreshes without any
    // writes to the observed LiveData fields.
    @Suppress("UNUSED_VARIABLE")
    val tick = intervalOf(10)

    val line: String? = when {
        pumpConnected.value == false -> {
            // When disconnected, prefer the connection timestamp (set on the
            // last successful connect) over the last-message timestamp since
            // the latter is typically older and more confusing than useful.
            val instant = pumpLastConnectionTimestamp.value ?: pumpLastMessageTimestamp.value
            instant?.let { "Last seen ${shortTimeAgo(it, nowThresholdSeconds = 1)}" }
        }
        pumpLastMessageTimestamp.value != null ->
            "Last updated ${shortTimeAgo(pumpLastMessageTimestamp.value!!, nowThresholdSeconds = 1)}"
        else -> null
    }

    if (line != null) {
        Text(
            text = line,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    }
}
