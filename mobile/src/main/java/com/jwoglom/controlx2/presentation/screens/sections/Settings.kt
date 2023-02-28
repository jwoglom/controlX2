@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.util.AppVersionInfo
import com.jwoglom.pumpx2.pump.messages.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Settings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {

            item {
                val ver = AppVersionInfo(context)
                Text(buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                        append("ControlX2 ")
                        append(ver.version)
                    }
                }, Modifier.padding(start = 16.dp, top = 16.dp))
                Text(buildAnnotatedString {
                    append("with PumpX2 ")
                    append(ver.pumpX2)
                    append("\n")

                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Build: ")
                    }
                    append(ver.buildVersion)
                    append("\n")

                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Build time: ")
                    }
                    append(ver.buildTime)
                    append("\n")
                }, lineHeight = 20.sp, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
            }

            item {
                if (Prefs(context).serviceEnabled()) {
                    ListItem(
                        headlineText = { Text("Disable ControlX2 service") },
                        supportingText = { Text("Stops the background service and disables it from starting automatically when the app is opened.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Stop icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            Prefs(context).setServiceEnabled(false)
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    Thread.sleep(250)
                                }
                                sendMessage("/to-phone/force-reload", "".toByteArray())
                            }
                        }
                    )
                } else {
                    ListItem(
                        headlineText = { Text("Enable ControlX2 service") },
                        supportingText = { Text("Starts the background service and enables it to start automatically when the app is opened.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Start icon",
                            )
                        },
                        modifier = Modifier.clickable {
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
                    )
                }
                Divider()
            }
            item {
                if (Prefs(context).connectionSharingEnabled()) {
                    ListItem(
                        headlineText = { Text("Disable connection sharing") },
                        supportingText = { Text("Removes workarounds to run WearX2 and the t:connect app at the same time.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Stop icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            Prefs(context).setConnectionSharingEnabled(false)
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    Thread.sleep(250)
                                }
                                sendMessage("/to-phone/force-reload", "".toByteArray())
                            }
                        }
                    )
                } else {
                    ListItem(
                        headlineText = { Text("Enable connection sharing") },
                        supportingText = { Text("Enables workarounds to run WearX2 and the t:connect app at the same time.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Start icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            Prefs(context).setConnectionSharingEnabled(true)
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    Thread.sleep(250)
                                }
                                sendMessage("/to-phone/force-reload", "".toByteArray())
                            }
                        }
                    )
                }
                Divider()
            }

            item {
                ListItem(
                    headlineText = { Text("Force service reload") },
                    supportingText = { Text("Restarts the background service.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reload icon",
                        )
                    },
                    modifier = Modifier.clickable {
                        sendMessage("/to-phone/force-reload", "".toByteArray())
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineText = { Text("Reconfigure pump") },
                    supportingText = { Text("Change or re-pair with the connected pump.") },
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.pump),
                            tint = Color.Unspecified,
                            contentDescription = "Settings icon",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.clickable {
                        Prefs(context).setPumpSetupComplete(false)
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                Thread.sleep(250)
                            }
                            sendMessage(
                                "/to-phone/app-reload",
                                "".toByteArray()
                            )
                        }
                        navController?.navigate(Screen.PumpSetup.route)
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineText = { Text("Reconfigure app") },
                    supportingText = { Text("Enable or disable insulin delivery actions (for remote bolus).") },
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
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
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