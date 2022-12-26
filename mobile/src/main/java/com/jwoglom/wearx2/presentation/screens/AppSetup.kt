package com.jwoglom.wearx2.presentation.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.Prefs
import com.jwoglom.wearx2.presentation.components.DialogScreen
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.theme.WearX2Theme

@Composable
fun AppSetup(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    var insulinDeliveryActions by remember { mutableStateOf(Prefs(context).insulinDeliveryActions()) }
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
                            navController?.navigate(Screen.Landing.route)
                            Prefs(context).setAppSetupComplete(true)
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
                        value = insulinDeliveryActions,
                        onValueChange = {
                            insulinDeliveryActions = !insulinDeliveryActions
                            Prefs(context).setInsulinDeliveryActions(insulinDeliveryActions)
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
            Line(buildAnnotatedString {
                append("Enabling insulin delivery actions allows you to perform remote boluses. This is an optional setting. ")
                withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                    append("WARNING: THIS SOFTWARE IS EXPERIMENTAL. VERIFY BOLUS OPERATIONS ON YOUR PUMP.")
                }
            })
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
            )
        }
    }
}