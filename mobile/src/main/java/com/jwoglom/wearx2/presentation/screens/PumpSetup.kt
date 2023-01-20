package com.jwoglom.wearx2.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.IconButton
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
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
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.builders.PumpChallengeRequestBuilder
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.Prefs
import com.jwoglom.wearx2.presentation.components.DialogScreen
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.components.PumpSetupStageDescription
import com.jwoglom.wearx2.presentation.components.PumpSetupStageProgress
import com.jwoglom.wearx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.sections.Settings
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import timber.log.Timber

@Composable
fun PumpSetup(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val setupStage = ds.pumpSetupStage.observeAsState()

    var pairingCodeText by remember { mutableStateOf(when (PumpState.getPairingCode(context)) {
        null -> ""
        else -> PumpState.getPairingCode(context)
    }) }
    DialogScreen(
        "Pump Setup",
        buttonContent = {
            when (setupStage.value) {
                PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
                    Button(
                        onClick = {
                            sendMessage("/to-phone/start-comm", "skip_notif_permission".toByteArray())
                        }
                    ) {
                        Text("Continue Without Permission")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            when (ds.pumpSetupStage.value) {
                                PumpSetupStage.WAITING_PUMPX2_INIT -> {
                                    if (navController?.popBackStack() == false) {
                                        navController.navigate(Screen.FirstLaunch.route)
                                    }
                                    Prefs(context).setPumpSetupComplete(false)
                                }
                                else -> {
                                    ds.pumpSetupStage.value =
                                        PumpSetupStage.values()[setupStage.value!!.ordinal - 1]
                                }
                            }
                        }
                    ) {
                        Text("Back")
                    }
                }
            }
            when (setupStage.value) {
                PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
                    Button(
                        onClick = {
                            sendMessage("/to-phone/start-comm", "".toByteArray())
                        }
                    ) {
                        Text("Retry")
                    }
                }
                PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE, PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
                    Button(
                        onClick = {
                            try {
                                val code = PumpChallengeRequestBuilder.processPairingCode(pairingCodeText)
                                PumpChallengeRequestBuilder.create(0, code, ByteArray(0))
                                sendMessage("/to-phone/set-pairing-code", code.toByteArray())
                                ds.pumpSetupStage.value = setupStage.value!!.nextStage(PumpSetupStage.PUMPX2_WAITING_TO_PAIR)
                            } catch (e: Exception) {
                                Timber.w("pairingCodeInput: $e")
                            }
                        }
                    ) {
                        Text("Pair")
                    }
                }
                PumpSetupStage.PUMPX2_PUMP_CONNECTED -> {
                    Button(
                        onClick = {
                            navController?.navigate(Screen.AppSetup.route)
                            Prefs(context).setPumpSetupComplete(true)
                        }
                    ) {
                        Text("Next")
                    }
                }
                else -> {
                    /*
                    Button(
                        onClick = {
                            ds.setupStage.value = SetupStage.values()[setupStage.value!!.ordinal + 1]
                        }
                    ) {
                        Text("Advance for testing")
                    }
                    */
                }
            }
        }
    ) {
        item {
            ServiceDisabledMessage(sendMessage = sendMessage)
        }
        item {
            PumpSetupStageProgress(initialSetup = true)
        }
        item {
            val setupDeviceName = ds.setupDeviceName.observeAsState()
            PumpSetupStageDescription(
                initialSetup = true,
                pairingCodeStage = {
                    val focusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current

                    if (setupStage.value == PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE) {
                        LaunchedEffect(Unit) {
                            pairingCodeText = ""
                        }
                        Line(buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                append("The pairing code was invalid. ")
                            }
                            append("The code was either entered incorrectly or timed out. Make sure the 'Pair Device' dialog is open on your pump.")
                        })
                    } else {
                        Line("Initial connection made to '${setupDeviceName.value}'")
                    }
                    Spacer(Modifier.height(16.dp))
                    Line("Please enter the pairing code displayed at:")
                    Line("Bluetooth Settings > Pair Device", bold = true)

                    Spacer(Modifier.height(32.dp))

                    LaunchedEffect (Unit) {
                        if (pairingCodeText.isEmpty()) {
                            focusRequester.requestFocus()
                        }
                    }
                    BasicTextField(
                        value = pairingCodeText,
                        onValueChange = {
                            fun filterPairingCode(text: String): String {
                                var processed = ""
                                for (c in text.toCharArray()) {
                                    if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') {
                                        processed += c
                                    }
                                }
                                return processed
                            }

                            val newPairingCode = filterPairingCode(it)
                            if (pairingCodeText.length < 16 && newPairingCode.length == 16) {
                                focusManager.clearFocus()
                            }

                            pairingCodeText = newPairingCode
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester),
                        decorationBox = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .fillMaxWidth()
                            ) {
                                repeat(4) { index ->
                                    val chars = when {
                                        4*index >= pairingCodeText.length -> ""
                                        4*(index+1) >= pairingCodeText.length -> pairingCodeText.substring(4 * index)
                                        else -> pairingCodeText.substring(4 * index, 4 * (index+1))
                                    }
                                    val isFocused = index == pairingCodeText.length / 4
                                    Text(
                                        text = chars,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.DarkGray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .width(70.dp)
                                            .border(
                                                if (isFocused) 2.dp else 1.dp,
                                                if (isFocused) Color.DarkGray else Color.LightGray,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(2.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (index != 3) {
                                        Text(
                                            text = "-",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = Color.DarkGray,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }
                    )

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            )
        }
    }
}

enum class PumpSetupStage(val step: Int, val description: String) {
    PERMISSIONS_NOT_GRANTED(0, "Permissions not granted"),
    WAITING_PUMPX2_INIT(1, "Waiting for PumpX2 init"),
    PUMPX2_SEARCHING_FOR_PUMP(1, "Searching for pump"),
    PUMPX2_PUMP_DISCONNECTED(2, "Pump disconnected, reconnecting"),
    PUMPX2_PUMP_DISCOVERED(2, "Pump discovered, connecting"),
    PUMPX2_PUMP_MODEL_METADATA(3, "Initial pump metadata received"),
    PUMPX2_INITIAL_PUMP_CONNECTION(3, "Initial connection established"),
    PUMPX2_WAITING_FOR_PAIRING_CODE(4, "Waiting for pairing code"),
    PUMPX2_INVALID_PAIRING_CODE(4, "Invalid pairing code"),
    PUMPX2_WAITING_TO_PAIR(4, "Waiting to pair"),
    PUMPX2_PUMP_CONNECTED(5, "Pairing code accepted"),
    ;

    fun nextStage(stage: PumpSetupStage): PumpSetupStage {
        Timber.d("PumpSetupStage.nextStage(%s) from %s", stage, this)
        if (stage == PUMPX2_INVALID_PAIRING_CODE) {
            return stage
        }

        if (this == PUMPX2_INVALID_PAIRING_CODE && stage.ordinal < PUMPX2_INVALID_PAIRING_CODE.ordinal) {
            return this
        }

        if (stage == PUMPX2_PUMP_DISCONNECTED) {
            return stage
        }

        if (ordinal < stage.ordinal) {
            return stage
        }

        return this
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
            LocalDataStore.current.pumpSetupStage.value = PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE
            PumpSetup(
                sendMessage = {_, _ -> },
            )
        }
    }
}