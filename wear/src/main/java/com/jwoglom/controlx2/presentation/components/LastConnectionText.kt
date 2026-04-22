package com.jwoglom.controlx2.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var relative: String? by remember { mutableStateOf(null) }
    fun update() {
        relative = pumpLastMessageTimestamp.value?.let {
            shortTimeAgo(it, nowThresholdSeconds = 1)
        }
    }

    LaunchedEffect(pumpLastMessageTimestamp.value) { update() }
    LaunchedEffect(intervalOf(10)) { update() }

    val line: String? = when {
        pumpConnected.value == false -> when {
            relative != null -> "Last seen $relative"
            pumpLastConnectionTimestamp.value != null -> "Last seen ${pumpLastConnectionTimestamp.value}"
            else -> null
        }
        relative != null -> "Last updated $relative"
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
