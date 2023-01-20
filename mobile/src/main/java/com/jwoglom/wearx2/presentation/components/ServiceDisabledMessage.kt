package com.jwoglom.wearx2.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.text.FormattableUtils.append

@Composable
fun ServiceDisabledMessage(
    sendMessage: (String, ByteArray) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ds = LocalDataStore.current

    var enabled by remember { mutableStateOf(Prefs(context).serviceEnabled()) }
    var onlySnoopEnabled by remember { mutableStateOf(Prefs(context).onlySnoopBluetoothEnabled()) }

    val pumpConnected = ds.pumpConnected.observeAsState()
    LaunchedEffect (pumpConnected.value) {
        enabled = Prefs(context).serviceEnabled()
        onlySnoopEnabled = Prefs(context).onlySnoopBluetoothEnabled()
    }

    if (!enabled) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(8.dp).clickable {
                    Prefs(context).setServiceEnabled(true)
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(250)
                        }
                        // reload service, if running
                        sendMessage("/to-phone/force-reload", "".toByteArray())
                        withContext(Dispatchers.IO) {
                            Thread.sleep(250)
                            // reload main activity as fallback
                            sendMessage("/to-phone/app-reload", "".toByteArray())
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).padding(8.dp)
                )
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("The background service is disabled, so WearX2 cannot connect to the pump. ")
                    }
                    append("Press here to re-enable the service.")
                })
            }
        }
    } else if (onlySnoopEnabled) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).padding(8.dp)
                )
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("OnlySnoopBluetooth debug option is enabled, so limited app functionality is available. ")
                    }
                    append("Select 'Debug > Disable Only Snoop Bluetooth' to disable.")
                })
            }
        }
    }
}