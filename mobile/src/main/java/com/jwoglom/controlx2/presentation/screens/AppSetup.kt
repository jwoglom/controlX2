@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.components.DialogScreen
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppSetup(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    preview: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ds = LocalDataStore.current

    var connectionSharingEnabled by remember { mutableStateOf(Prefs(context).connectionSharingEnabled()) }
    var insulinDeliveryActions by remember { mutableStateOf(Prefs(context).insulinDeliveryActions()) }
    var bolusConfirmationInsulinThreshold by remember { mutableStateOf(Prefs(context).bolusConfirmationInsulinThreshold()) }
    var checkForUpdates by remember { mutableStateOf(Prefs(context).checkForUpdates()) }
    var autoFetchHistoryLogs by remember { mutableStateOf(Prefs(context).autoFetchHistoryLogs()) }
    var glucoseUnit by remember { mutableStateOf(Prefs(context).glucoseUnit()) }

    var showGlucoseUnitDialog by remember { mutableStateOf(false) }
    var showInsulinWarningDialog by remember { mutableStateOf(false) }
    var showBolusThresholdDialog by remember { mutableStateOf(false) }
    var showUpdatesWarningDialog by remember { mutableStateOf(false) }

    DialogScreen(
        "App Setup",
        buttonContent = {
            Button(
                onClick = {
                    if (navController?.popBackStack() == false) {
                        navController.navigate(Screen.PumpSetup.route)
                    }
                    Prefs(context).setAppSetupComplete(false)
                }
            ) {
                Text("Back")
            }
            Button(
                onClick = {
                    Prefs(context).setAppSetupComplete(true)
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(250)
                        }
                        sendMessage(
                            "/to-phone/app-reload",
                            "".toByteArray()
                        )
                    }
                    navController?.navigate(Screen.Landing.route)
                }
            ) {
                Text("Continue")
            }
        }
    ) {
        item {
            ServiceDisabledMessage(sendMessage = sendMessage)
        }
        item {
            ListItem(
                headlineContent = {
                    Text("Connection Sharing")
                },
                supportingContent = {
                    Text("Enable t:connect app connection sharing. Enables workarounds to run ControlX2 and the t:connect app at the same time.")
                },
                trailingContent = {
                    Switch(
                        checked = connectionSharingEnabled,
                        onCheckedChange = {
                            connectionSharingEnabled = it
                            Prefs(context).setConnectionSharingEnabled(it)
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    Thread.sleep(250)
                                }
                                sendMessage(
                                    "/to-phone/app-reload",
                                    "".toByteArray()
                                )
                            }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    connectionSharingEnabled = !connectionSharingEnabled
                    Prefs(context).setConnectionSharingEnabled(connectionSharingEnabled)
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(250)
                        }
                        sendMessage(
                            "/to-phone/app-reload",
                            "".toByteArray()
                        )
                    }
                }
            )
            Divider()
        }
        item {
            ListItem(
                headlineContent = {
                    Text("Insulin Delivery Actions")
                },
                supportingContent = {
                    Text("Allow remote boluses from your phone or watch.")
                },
                trailingContent = {
                    Switch(
                        checked = insulinDeliveryActions,
                        onCheckedChange = {
                            if (!insulinDeliveryActions) {
                                showInsulinWarningDialog = true
                            } else {
                                insulinDeliveryActions = false
                                Prefs(context).setInsulinDeliveryActions(false)
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        Thread.sleep(250)
                                    }
                                    sendMessage(
                                        "/to-phone/app-reload",
                                        "".toByteArray()
                                    )
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    if (!insulinDeliveryActions) {
                        showInsulinWarningDialog = true
                    } else {
                        insulinDeliveryActions = false
                        Prefs(context).setInsulinDeliveryActions(false)
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                Thread.sleep(250)
                            }
                            sendMessage(
                                "/to-phone/app-reload",
                                "".toByteArray()
                            )
                        }
                    }
                }
            )
            Divider()
        }
        item {
            if (insulinDeliveryActions || preview) {
                ListItem(
                    headlineContent = {
                        Text("Bolus Confirmation Threshold")
                    },
                    supportingContent = {
                        Text(
                            bolusConfirmationInsulinThreshold.let {
                                if (it == 0.0) "Require confirmation for all boluses"
                                else "Require confirmation for boluses above ${twoDecimalPlaces(it)}u"
                            }
                        )
                    },
                    trailingContent = {
                        Text(
                            text = bolusConfirmationInsulinThreshold.let { if (it == 0.0) "always" else "${twoDecimalPlaces(it)}u" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        showBolusThresholdDialog = true
                    }
                )
                Divider()
            }
        }
        item {
            ListItem(
                headlineContent = {
                    Text("Check for Updates")
                },
                supportingContent = {
                    Text("Automatically check for ControlX2 updates")
                },
                trailingContent = {
                    Switch(
                        checked = checkForUpdates,
                        onCheckedChange = {
                            if (checkForUpdates) {
                                showUpdatesWarningDialog = true
                            } else {
                                checkForUpdates = true
                                Prefs(context).setCheckForUpdates(true)
                            }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    if (checkForUpdates) {
                        showUpdatesWarningDialog = true
                    } else {
                        checkForUpdates = true
                        Prefs(context).setCheckForUpdates(true)
                    }
                }
            )
            Divider()
        }
        item {
            ListItem(
                headlineContent = {
                    Text("Auto Fetch History Logs")
                },
                supportingContent = {
                    Text("Fetching history logs allows for rendering a CGM graph on the dashboard screen.")
                },
                trailingContent = {
                    Switch(
                        checked = autoFetchHistoryLogs,
                        onCheckedChange = {
                            autoFetchHistoryLogs = it
                            Prefs(context).setAutoFetchHistoryLogs(it)
                        }
                    )
                },
                modifier = Modifier.clickable {
                    autoFetchHistoryLogs = !autoFetchHistoryLogs
                    Prefs(context).setAutoFetchHistoryLogs(autoFetchHistoryLogs)
                }
            )
            Divider()
        }
        item {
            ListItem(
                headlineContent = {
                    Text("Glucose Unit")
                },
                supportingContent = {
                    Text("Choose between mg/dL or mmol/L for glucose values")
                },
                trailingContent = {
                    Text(
                        text = glucoseUnit?.abbreviation ?: "Not Set",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable {
                    showGlucoseUnitDialog = true
                }
            )
            Divider()
        }
    }

    // Glucose Unit Selection Dialog
    if (showGlucoseUnitDialog) {
        AlertDialog(
            onDismissRequest = { showGlucoseUnitDialog = false },
            title = { Text("Select Glucose Unit") },
            text = {
                Column {
                    GlucoseUnit.values().forEach { unit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    glucoseUnit = unit
                                    Prefs(context).setGlucoseUnit(unit)
                                    showGlucoseUnitDialog = false
                                    // Trigger app reload
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            Thread.sleep(250)
                                        }
                                        sendMessage("/to-phone/app-reload", "".toByteArray())
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = glucoseUnit == unit,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = unit.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGlucoseUnitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Insulin Delivery Warning Dialog
    if (showInsulinWarningDialog) {
        AlertDialog(
            onDismissRequest = { showInsulinWarningDialog = false },
            title = { Text("Warning") },
            text = {
                Text("WARNING: THIS SOFTWARE IS UNOFFICIAL AND EXPERIMENTAL. ENABLING INSULIN DELIVERY ACTIONS WILL ALLOW YOUR PHONE OR WATCH TO REMOTELY SEND BOLUSES TO YOUR PUMP. BE AWARE OF THE SECURITY AND SAFETY IMPLICATIONS OF ENABLING THIS SETTING. FOR SAFETY, VERIFY BOLUS OPERATIONS ON YOUR PUMP. THE PUMP WILL BEEP WHEN A BOLUS COMMAND IS SENT.")
            },
            confirmButton = {
                Button(onClick = {
                    insulinDeliveryActions = true
                    Prefs(context).setInsulinDeliveryActions(true)
                    showInsulinWarningDialog = false
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(250)
                        }
                        sendMessage("/to-phone/app-reload", "".toByteArray())
                    }
                }) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsulinWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Updates Warning Dialog
    if (showUpdatesWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUpdatesWarningDialog = false },
            title = { Text("Disable Update Checks") },
            text = {
                Text("Please regularly check the ControlX2 GitHub page and subscribe to release notifications to ensure you stay up to date. Warning: by disabling this option, you will not be alerted to any new feature, security, or safety updates.")
            },
            confirmButton = {
                Button(onClick = {
                    checkForUpdates = false
                    Prefs(context).setCheckForUpdates(false)
                    showUpdatesWarningDialog = false
                }) {
                    Text("Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatesWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bolus Threshold Dialog
    if (showBolusThresholdDialog) {
        var thresholdInput by remember { mutableStateOf(bolusConfirmationInsulinThreshold.let { if (it == 0.0) "" else "$it" }) }
        AlertDialog(
            onDismissRequest = { showBolusThresholdDialog = false },
            title = { Text("Bolus Confirmation Threshold") },
            text = {
                OutlinedTextField(
                    value = thresholdInput,
                    onValueChange = { thresholdInput = it },
                    label = { Text("Threshold (units)") },
                    supportingText = { Text("Enter 0 to always require confirmation") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newValue = thresholdInput.toDoubleOrNull() ?: 0.0
                    bolusConfirmationInsulinThreshold = newValue
                    Prefs(context).setBolusConfirmationInsulinThreshold(newValue)
                    showBolusThresholdDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBolusThresholdDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Preview(showBackground = true)
@Composable
internal fun AppSetupDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            AppSetup(
                sendMessage = {_, _ -> },
                preview = true,
            )
        }
    }
}