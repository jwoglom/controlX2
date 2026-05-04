@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataThresholding
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.screens.sections.components.VersionInfo
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.SupportBundleSummary
import com.jwoglom.controlx2.presentation.util.formatLogLineCount
import com.jwoglom.controlx2.presentation.util.getSupportBundleSummary
import com.jwoglom.controlx2.presentation.util.sendSupportBundleEmail
import com.jwoglom.controlx2.presentation.util.shareSupportBundle
import com.jwoglom.controlx2.shared.FeatureFlag
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.util.AppVersionCheck
import com.jwoglom.controlx2.util.AppVersionInfo
import com.jwoglom.controlx2.util.rescueResetThisDevice
import com.jwoglom.controlx2.util.switchDeviceRole
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeTimeDateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.PlaySoundRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun Settings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateToDebugOptions: () -> Unit = {},
    navigateToNightscoutSettings: () -> Unit = {},
    navigateToXdripSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showPumpSetupConfirmDialog by remember { mutableStateOf(false) }

    var showSyncTimeDialog by remember { mutableStateOf(false) }
    var showPlaySoundDialog by remember { mutableStateOf(false) }
    var showSupportBundleDialog by remember { mutableStateOf(false) }
    var supportBundleSummary by remember { mutableStateOf<SupportBundleSummary?>(null) }
    var showDeviceRoleDialog by remember { mutableStateOf(false) }
    var showRescueDialog by remember { mutableStateOf(false) }
    var currentDeviceRole by remember { mutableStateOf(Prefs(context).deviceRole()) }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Settings")
                Divider()
            }

            item {
                VersionInfo(context)
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Send Support Bundle") },
                    supportingContent = { Text("Generates a zip file with ControlX2 debug logs.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Help,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        val summary = getSupportBundleSummary(context)
                        if (summary == null) {
                            Toast.makeText(context, "No debug logs are available to share.", Toast.LENGTH_SHORT).show()
                        } else {
                            supportBundleSummary = summary
                            showSupportBundleDialog = true
                        }
                    }
                )
                Divider()
            }

            item {
                if (!Prefs(context).serviceEnabled()) {
                    ListItem(
                        headlineContent = { Text("Enable ControlX2 service") },
                        supportingContent = { Text("Starts the background service and enables it to start automatically when the app is opened.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Start icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            Prefs(context).setServiceEnabled(true)
                            coroutineScope.launch {
                                delay(250)
                                // reload service, if running
                                sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                                delay(250)
                                // reload main activity as fallback
                                sendMessage(MessagePaths.TO_SERVER_APP_RELOAD, "".toByteArray())
                            }
                        }
                    )
                    Divider()
                }
            }
//            item {
//                if (Prefs(context).connectionSharingEnabled()) {
//                    ListItem(
//                        headlineContent = { Text("Disable connection sharing") },
//                        supportingContent = { Text("Removes workarounds to run WearX2 and the t:connect app at the same time.") },
//                        leadingContent = {
//                            Icon(
//                                Icons.Filled.Close,
//                                contentDescription = "Stop icon",
//                            )
//                        },
//                        modifier = Modifier.clickable {
//                            Prefs(context).setConnectionSharingEnabled(false)
//                            coroutineScope.launch {
//                                withContext(Dispatchers.IO) {
//                                    delay(250)
//                                }
//                                sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
//                            }
//                        }
//                    )
//                } else {
//                    ListItem(
//                        headlineContent = { Text("Enable connection sharing") },
//                        supportingContent = { Text("Enables workarounds to run WearX2 and the t:connect app at the same time.") },
//                        leadingContent = {
//                            Icon(
//                                Icons.Filled.Check,
//                                contentDescription = "Start icon",
//                            )
//                        },
//                        modifier = Modifier.clickable {
//                            Prefs(context).setConnectionSharingEnabled(true)
//                            coroutineScope.launch {
//                                withContext(Dispatchers.IO) {
//                                    delay(250)
//                                }
//                                sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
//                            }
//                        }
//                    )
//                }
//                Divider()
//            }

            item {
                ListItem(
                    headlineContent = { Text("Force service reload") },
                    supportingContent = { Text("Restarts the background service.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reload icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Reconfigure pump") },
                    supportingContent = { Text("Disconnect and re-pair with a pump.") },
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.pump),
                            tint = Color.Unspecified,
                            contentDescription = "Settings icon",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.clickable {
                        showPumpSetupConfirmDialog = true
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Reconfigure app") },
                    supportingContent = { Text("Enable or disable insulin delivery actions.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        Prefs(context).setAppSetupComplete(false)
                        navController?.navigate(Screen.AppSetup.route)
                    }
                )
                Divider()
            }

            item {
                var ffEnabled by remember { mutableStateOf(FeatureFlag.enabled(context,
                    FeatureFlag.BTHostSwitch
                ))}

                if (ffEnabled) {
                    val roleLabel = when (currentDeviceRole) {
                        DeviceRole.PUMP_HOST -> "Phone (pump-host)"
                        DeviceRole.CLIENT -> "Watch (pump-host)"
                    }
                    ListItem(
                        headlineContent = { Text("Pump-host device") },
                        supportingContent = { Text("Currently: $roleLabel.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Devices,
                                contentDescription = "Device role icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            showDeviceRoleDialog = true
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text("Reset & start over on this device") },
                        supportingContent = { Text("Use if you're stuck. Forces this phone to be pump-host and clears the pump bond.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Devices,
                                contentDescription = "Reset icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            showRescueDialog = true
                        }
                    )
                    Divider()
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Nightscout") },
                    supportingContent = { Text("Configure Nightscout sync to upload pump data.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.CloudSync,
                            contentDescription = "Nightscout icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        navigateToNightscoutSettings()
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("xDrip") },
                    supportingContent = { Text("Configure xDrip broadcasts for pump and CGM data.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "xDrip icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        navigateToXdripSettings()
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Sync pump time") },
                    supportingContent = { Text("Set the pump's clock to the current phone time.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Sync time icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        showSyncTimeDialog = true
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Find my pump") },
                    supportingContent = { Text("Play a sound on the pump to help locate it.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = "Play sound icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        showPlaySoundDialog = true
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Debug options") },
                    supportingContent = { Text("Perform debug options.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.DeveloperMode,
                            contentDescription = "Settings icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        navigateToDebugOptions()
                    }
                )
                Divider()
            }
        }
    )

    if (showPumpSetupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPumpSetupConfirmDialog = false },
            title = { Text("Forget pump?") },
            text = {
                Text("Clears the pump bond on this phone. Place the Mobi on the charging pad to re-pair.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPumpSetupConfirmDialog = false
                        Prefs(context).setPumpSetupComplete(false)
                        Prefs(context).setPumpFinderPumpMac("")
                        Prefs(context).setPumpFinderPairingCodeType("")
                        Prefs(context).setPumpFinderServiceEnabled(true)
                        Prefs(context).setCurrentPumpSid(-1)
                        PumpState.resetState(context)
                        sendMessage(MessagePaths.TO_SERVER_APP_RELOAD, "".toByteArray())
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPumpSetupConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSupportBundleDialog && supportBundleSummary != null) {
        val summary = supportBundleSummary!!
        AlertDialog(
            onDismissRequest = {
                showSupportBundleDialog = false
                supportBundleSummary = null
            },
            title = { Text("Send PumpX2 Support Bundle") },
            text = {
                Text("Send ${summary.debugFileCount} debug files with ${formatLogLineCount(summary.totalLogLines)} total logs from ${summary.rangeStart} - ${summary.rangeEnd} to the developers for assistance?")
            },
            confirmButton = {
                TextButton(onClick = {
                    sendSupportBundleEmail(context)
                    showSupportBundleDialog = false
                    supportBundleSummary = null
                }) {
                    Text("Send email")
                }

                TextButton(onClick = {
                    shareSupportBundle(context)
                    showSupportBundleDialog = false
                    supportBundleSummary = null
                }) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSupportBundleDialog = false
                    supportBundleSummary = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sync Time Dialog
    if (showSyncTimeDialog) {
        AlertDialog(
            onDismissRequest = { showSyncTimeDialog = false },
            title = { Text("Sync Pump Time") },
            text = { Text("Set the pump's internal clock to the current phone time?") },
            confirmButton = {
                TextButton(onClick = {
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(ChangeTimeDateRequest(Instant.now()))
                    )
                    showSyncTimeDialog = false
                    Toast.makeText(context, "Pump time sync sent", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncTimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeviceRoleDialog) {
        val newRole = when (currentDeviceRole) {
            DeviceRole.PUMP_HOST -> DeviceRole.CLIENT
            DeviceRole.CLIENT -> DeviceRole.PUMP_HOST
        }
        val newRoleLabel = when (newRole) {
            DeviceRole.PUMP_HOST -> "Phone (pump-host)"
            DeviceRole.CLIENT -> "Watch (pump-host)"
        }
        val walkthrough = when (newRole) {
            DeviceRole.CLIENT ->
                "The phone restarts as client and the watch takes over as pump-host. Place the Mobi on the charging pad to re-pair."
            DeviceRole.PUMP_HOST ->
                "The phone restarts as pump-host and the watch flips to client. Place the Mobi on the charging pad to re-pair."
        }
        AlertDialog(
            onDismissRequest = { showDeviceRoleDialog = false },
            title = { Text("Switch pump-host to $newRoleLabel?") },
            text = { Text(walkthrough) },
            confirmButton = {
                TextButton(onClick = {
                    showDeviceRoleDialog = false
                    val activity = context as? Activity
                    if (activity != null) {
                        // `switchDeviceRole` calls `activity.recreate()`; the
                        // current Composable tree is discarded before any state
                        // write here would be observable, so we don't touch
                        // `currentDeviceRole` — the re-created Activity reads
                        // the fresh pref.
                        switchDeviceRole(activity, newRole)
                    } else {
                        Toast.makeText(context, "Unable to switch role: no activity context", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceRoleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRescueDialog) {
        AlertDialog(
            onDismissRequest = { showRescueDialog = false },
            title = { Text("Reset & start over?") },
            text = {
                Text("Forces this phone to pump-host, clears the pump bond, and asks the watch to flip to client. Place the Mobi on the charging pad to re-pair.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRescueDialog = false
                    val activity = context as? Activity
                    if (activity != null) {
                        rescueResetThisDevice(activity)
                    } else {
                        Toast.makeText(context, "Unable to rescue: no activity context", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescueDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Play Sound Dialog
    if (showPlaySoundDialog) {
        AlertDialog(
            onDismissRequest = { showPlaySoundDialog = false },
            title = { Text("Find My Pump") },
            text = { Text("Play a sound on the pump?") },
            confirmButton = {
                TextButton(onClick = {
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(PlaySoundRequest())
                    )
                    showPlaySoundDialog = false
                    Toast.makeText(context, "Playing sound on pump", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Play Sound")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaySoundDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SettingsDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            Settings(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}
