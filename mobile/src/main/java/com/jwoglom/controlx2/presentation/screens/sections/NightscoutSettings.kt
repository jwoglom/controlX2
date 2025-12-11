@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncWorker
import com.jwoglom.controlx2.presentation.components.HeaderLine
import kotlinx.coroutines.launch

@Composable
fun NightscoutSettings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    pumpSid: Int = 0
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("controlx2", android.content.Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(NightscoutSyncConfig.load(prefs)) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showApiSecretDialog by remember { mutableStateOf(false) }
    var showProcessorsDialog by remember { mutableStateOf(false) }
    var showLookbackDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Nightscout Settings")
                Divider()
            }

            // Enable/Disable Nightscout sync
            item {
                ListItem(
                    headlineContent = {
                        Text(if (config.enabled) "Disable Nightscout Sync" else "Enable Nightscout Sync")
                    },
                    supportingContent = {
                        Text(
                            if (config.enabled) {
                                "Stops automatic upload to Nightscout"
                            } else {
                                "Starts automatic upload of pump data to Nightscout"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (config.enabled) Icons.Filled.Close else Icons.Filled.Check,
                            contentDescription = if (config.enabled) "Disable" else "Enable"
                        )
                    },
                    modifier = Modifier.clickable {
                        val newConfig = config.copy(enabled = !config.enabled)
                        config = newConfig
                        NightscoutSyncConfig.save(prefs, newConfig)

                        // Start/stop worker
                        if (newConfig.enabled) {
                            NightscoutSyncWorker.startIfEnabled(context, prefs, pumpSid)
                            Toast.makeText(context, "Nightscout sync enabled", Toast.LENGTH_SHORT).show()
                        } else {
                            NightscoutSyncWorker.stopIfRunning()
                            Toast.makeText(context, "Nightscout sync disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Divider()
            }

            // Nightscout URL
            item {
                ListItem(
                    headlineContent = { Text("Nightscout URL") },
                    supportingContent = {
                        Text(config.nightscoutUrl.ifBlank { "Not configured" })
                    },
                    modifier = Modifier.clickable {
                        showUrlDialog = true
                    }
                )
                Divider()
            }

            // API Secret
            item {
                ListItem(
                    headlineContent = { Text("API Secret") },
                    supportingContent = {
                        Text(if (config.apiSecret.isNotBlank()) "••••••••" else "Not configured")
                    },
                    modifier = Modifier.clickable {
                        showApiSecretDialog = true
                    }
                )
                Divider()
            }

            // Enabled Processors
            item {
                ListItem(
                    headlineContent = { Text("Data Types") },
                    supportingContent = {
                        Text("${config.enabledProcessors.size} of ${ProcessorType.all().size} enabled")
                    },
                    modifier = Modifier.clickable {
                        showProcessorsDialog = true
                    }
                )
                Divider()
            }

            // Sync Interval
            item {
                ListItem(
                    headlineContent = { Text("Sync Interval") },
                    supportingContent = { Text("${config.syncIntervalMinutes} minutes") },
                    modifier = Modifier.clickable {
                        showIntervalDialog = true
                    }
                )
                Divider()
            }

            // Lookback Period
            item {
                ListItem(
                    headlineContent = { Text("Initial Lookback") },
                    supportingContent = { Text("${config.initialLookbackHours} hours on first sync") },
                    modifier = Modifier.clickable {
                        showLookbackDialog = true
                    }
                )
                Divider()
            }

            // Sync Now button
            item {
                ListItem(
                    headlineContent = { Text("Sync Now") },
                    supportingContent = { Text("Trigger immediate sync") },
                    leadingContent = {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync")
                    },
                    modifier = Modifier.clickable {
                        if (config.enabled && config.isValid()) {
                            coroutineScope.launch {
                                NightscoutSyncWorker.getInstance(context, prefs, pumpSid).syncNow()
                                Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Please enable and configure Nightscout first",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                Divider()
            }
        }
    )

    // URL Dialog
    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf(config.nightscoutUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Nightscout URL") },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://your-nightscout.herokuapp.com") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newConfig = config.copy(nightscoutUrl = urlInput.trim())
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showUrlDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // API Secret Dialog
    if (showApiSecretDialog) {
        var secretInput by remember { mutableStateOf(config.apiSecret) }
        AlertDialog(
            onDismissRequest = { showApiSecretDialog = false },
            title = { Text("API Secret") },
            text = {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = { Text("API Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newConfig = config.copy(apiSecret = secretInput.trim())
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showApiSecretDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiSecretDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Processors Dialog
    if (showProcessorsDialog) {
        var selectedProcessors by remember { mutableStateOf(config.enabledProcessors) }
        AlertDialog(
            onDismissRequest = { showProcessorsDialog = false },
            title = { Text("Select Data Types") },
            text = {
                LazyColumn {
                    items(ProcessorType.all().toList()) { processorType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProcessors = if (selectedProcessors.contains(processorType)) {
                                        selectedProcessors - processorType
                                    } else {
                                        selectedProcessors + processorType
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedProcessors.contains(processorType),
                                onCheckedChange = null
                            )
                            Text(
                                text = processorType.displayName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newConfig = config.copy(enabledProcessors = selectedProcessors)
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showProcessorsDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProcessorsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sync Interval Dialog
    if (showIntervalDialog) {
        var intervalInput by remember { mutableStateOf(config.syncIntervalMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Sync Interval") },
            text = {
                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { intervalInput = it },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = intervalInput.toIntOrNull() ?: 15
                    val newConfig = config.copy(syncIntervalMinutes = minutes.coerceIn(5, 1440))
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)

                    // Restart worker with new interval
                    if (config.enabled) {
                        NightscoutSyncWorker.stopIfRunning()
                        NightscoutSyncWorker.startIfEnabled(context, prefs, pumpSid)
                    }

                    showIntervalDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Lookback Dialog
    if (showLookbackDialog) {
        var lookbackInput by remember { mutableStateOf(config.initialLookbackHours.toString()) }
        AlertDialog(
            onDismissRequest = { showLookbackDialog = false },
            title = { Text("Initial Lookback Period") },
            text = {
                Column {
                    Text("How far back to sync when first enabled (hours)")
                    OutlinedTextField(
                        value = lookbackInput,
                        onValueChange = { lookbackInput = it },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hours = lookbackInput.toIntOrNull() ?: 24
                    val newConfig = config.copy(initialLookbackHours = hours.coerceIn(1, 720))
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showLookbackDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLookbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
