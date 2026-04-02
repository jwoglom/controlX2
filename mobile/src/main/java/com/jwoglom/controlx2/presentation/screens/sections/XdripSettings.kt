@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.sync.xdrip.XdripBroadcastSender
import com.jwoglom.controlx2.sync.xdrip.XdripPayloadGroup
import com.jwoglom.controlx2.sync.xdrip.XdripSyncConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

@Composable
fun XdripSettings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val prefs = Prefs(context).prefs()
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(XdripSyncConfig.load(prefs)) }

    fun saveConfig(newConfig: XdripSyncConfig, showToastText: String? = null) {
        val oldConfig = config
        config = newConfig
        XdripSyncConfig.save(prefs, newConfig)

        if (newConfig.requiresReloadComparedTo(oldConfig)) {
            coroutineScope.launch {
                delay(250)
                sendMessage("/to-phone/force-reload", "".toByteArray())
                delay(250)
                sendMessage("/to-phone/app-reload", "".toByteArray())
            }
        }

        if (showToastText != null) {
            Toast.makeText(context, showToastText, Toast.LENGTH_SHORT).show()
        }
    }

    fun togglePayload(payloadGroup: XdripPayloadGroup) {
        val payloadEnabled = config.isPayloadEnabled(payloadGroup)
        saveConfig(config.withPayloadEnabled(payloadGroup, !payloadEnabled))
    }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("xDrip Settings")
                Divider()
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(if (config.enabled) "Disable xDrip Sync" else "Enable xDrip Sync")
                    },
                    supportingContent = {
                        Text(
                            if (config.enabled) {
                                "Stops sending pump and CGM updates to xDrip"
                            } else {
                                "Enables xDrip broadcasts for selected payload groups"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (config.enabled) Icons.Filled.Close else Icons.Filled.Check,
                            contentDescription = if (config.enabled) "Disable" else "Enable"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { saveConfig(config.copy(enabled = it)) }
                        )
                    },
                    modifier = Modifier.clickable {
                        val newEnabled = !config.enabled
                        saveConfig(
                            config.copy(enabled = newEnabled),
                            if (newEnabled) "xDrip sync enabled" else "xDrip sync disabled"
                        )
                    }
                )
                Divider()
            }

            item {
                XdripPayloadToggleItem(
                    title = "Send SGV",
                    subtitle = "Broadcast current glucose readings to xDrip",
                    enabled = config.sendCgmSgv,
                    onToggle = { togglePayload(XdripPayloadGroup.CGM) }
                )
                Divider()
            }

            item {
                XdripPayloadToggleItem(
                    title = "Send Device Status",
                    subtitle = "Broadcast battery, IOB, cartridge, and basal status",
                    enabled = config.sendPumpDeviceStatus,
                    onToggle = { togglePayload(XdripPayloadGroup.PUMP_DEVICE_STATUS) }
                )
                Divider()
            }

            item {
                XdripPayloadToggleItem(
                    title = "Send Treatments",
                    subtitle = "Broadcast bolus treatments to xDrip",
                    enabled = config.sendTreatments,
                    onToggle = { togglePayload(XdripPayloadGroup.TREATMENTS) }
                )
                Divider()
            }

            item {
                XdripPayloadToggleItem(
                    title = "Send Statusline",
                    subtitle = "Broadcast one-line pump status text",
                    enabled = config.sendStatusLine,
                    onToggle = { togglePayload(XdripPayloadGroup.STATUS_LINE) }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Send diagnostics test payload") },
                    supportingContent = {
                        Text("Sends one-shot test SGV and statusline broadcast intents")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.BugReport,
                            contentDescription = "Diagnostics icon"
                        )
                    },
                    modifier = Modifier.clickable {
                        sendDiagnosticsPayload(context)
                    }
                )
                Divider()
            }
        }
    )
}

@Composable
private fun XdripPayloadToggleItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() }
            )
        },
        modifier = Modifier.clickable { onToggle() }
    )
}

private fun sendDiagnosticsPayload(context: Context) {
    val now = Instant.now()
    val sender = XdripBroadcastSender(context)
    val sgvSent = sender.sendSgv(
        JSONArray().put(
            JSONObject().apply {
                put("mgdl", 123)
                put("mills", now.toEpochMilli())
                put("direction", "Flat")
            }
        ).toString()
    )
    val statusSent = sender.sendExternalStatusline("ControlX2 test statusline @ ${now}")

    val message = buildString {
        append("Diagnostics sent")
        append(if (sgvSent) " (SGV OK" else " (SGV skipped")
        append(if (statusSent) ", statusline OK)" else ", statusline skipped)")
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
