@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.wearx2.presentation.screens

import android.app.AlertDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.Prefs
import com.jwoglom.wearx2.presentation.components.DialogScreen
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
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
    val setupComplete by remember { mutableStateOf(true) }

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
            when {
                setupComplete -> {
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
                else -> {}
            }
        }
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .toggleable(
                        value = connectionSharingEnabled,
                        onValueChange = {
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
                        },
                        role = Role.Checkbox
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = connectionSharingEnabled,
                    onCheckedChange = null // null recommended for accessibility with screenreaders
                )
                Line(
                    "Enable t:connect app connection sharing",
                    bold = true,
                    modifier = Modifier.padding(start = 16.dp))
                Line("Enables workarounds to run WearX2 and the t:connect app at the same time.")
            }
            Divider()
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .toggleable(
                        value = insulinDeliveryActions,
                        onValueChange = {
                            fun update() {
                                insulinDeliveryActions = !insulinDeliveryActions
                                Prefs(context).setInsulinDeliveryActions(insulinDeliveryActions)
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
                            if (!insulinDeliveryActions) {
                                AlertDialog.Builder(context)
                                    .setMessage("WARNING: THIS SOFTWARE IS UNOFFICIAL AND EXPERIMENTAL. ENABLING INSULIN DELIVERY ACTIONS WILL ALLOW YOUR PHONE OR WATCH TO REMOTELY SEND BOLUSES TO YOUR PUMP. BE AWARE OF THE SECURITY AND SAFETY IMPLICATIONS OF ENABLING THIS SETTING. FOR SAFETY, VERIFY BOLUS OPERATIONS ON YOUR PUMP. THE PUMP WILL BEEP WHEN A BOLUS COMMAND IS SENT.")
                                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                                    .setPositiveButton("Enable") { dialog, _ ->
                                        dialog.dismiss()
                                        update()
                                    }
                                    .show()
                            } else {
                                update()
                            }
                        },
                        role = Role.Checkbox
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = insulinDeliveryActions,
                    onCheckedChange = null // null recommended for accessibility with screenreaders
                )
                Line(
                    "Enable insulin delivery actions",
                    bold = true,
                    modifier = Modifier.padding(start = 16.dp))
            }
            Line("Enabling insulin delivery actions allows you to perform remote boluses from your phone or watch.")
            Spacer(Modifier.height(16.dp))
            Divider()
        }
        item {
            if (insulinDeliveryActions || preview) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Require bolus confirmation:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    BasicTextField(
                        value = bolusConfirmationInsulinThreshold.let { if (it == 0.0) "" else "$it" },
                        onValueChange = { itStr ->
                            itStr.toDoubleOrNull()?.let {
                                bolusConfirmationInsulinThreshold = it
                                Prefs(context).setBolusConfirmationInsulinThreshold(it)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number,
                        ),
                        decorationBox = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                Text(
                                    text = bolusConfirmationInsulinThreshold.let { if (it == 0.0) "always" else "above ${twoDecimalPlaces(it)} u"},
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            Color.DarkGray,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(4.dp)
                                )
                            }
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Divider()
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    WearX2Theme() {
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