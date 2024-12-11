package com.jwoglom.controlx2.presentation.screens

import android.widget.Toast
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
import androidx.compose.material3.Button
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
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.components.DialogScreen
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.PumpSetupStageDescription
import com.jwoglom.controlx2.presentation.components.PumpSetupStageProgress
import com.jwoglom.controlx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.response.authentication.CentralChallengeResponse
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
                            sendMessage("/to-phone/start-pump-finder", "skip_notif_permission".toByteArray())
                        }
                    ) {
                        Text("Continue Without Permission")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            when (ds.pumpSetupStage.value) {
                                PumpSetupStage.WAITING_PUMP_FINDER_INIT -> {
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
                            sendMessage("/to-phone/start-pump-finder", "".toByteArray())
                        }
                    ) {
                        Text("Retry")
                    }
                }
                PumpSetupStage.PUMP_FINDER_ENTER_PAIRING_CODE, PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE -> {
                    Button(
                        onClick = {
                            try {
                                Timber.i("enterPairingCode: $pairingCodeText ${ds.setupPairingCodeType.value} (${PumpState.getPairingCode(context)})")
                                val code = PumpChallengeRequestBuilder.processPairingCode(pairingCodeText, ds.setupPairingCodeType.value)
                                sendMessage("/to-phone/set-pairing-code", code.toByteArray())
                                ds.pumpSetupStage.value = setupStage.value!!.nextStage(PumpSetupStage.WAITING_PUMP_FINDER_CLEANUP)
                            } catch (e: Exception) {
                                Timber.w("pairingCodeInput: $e")
                                Toast.makeText(context, e.toString().replaceBefore("$", "").substring(1), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Pair")
                    }
                }
                PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
                    Button(
                        onClick = {
                            sendMessage("/to-phone/restart-pump-finder", "".toByteArray())
                        }
                    ) {
                        Text("Retry")
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
            val setupPairingCodeType = ds.setupPairingCodeType.observeAsState()
            PumpSetupStageDescription(
                initialSetup = true,
                pairingCodeStage = {
                    val focusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current

                    if (setupStage.value == PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE) {
                        LaunchedEffect(Unit) {
                            PumpState.setPairingCode(context, "")
                        }
                        Line(buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                append("The pairing code was invalid. ")
                            }
                            append("The code was either entered incorrectly or timed out.")
                        })
                        Line(buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("For t:slim X2: ")
                            }
                            append("Make sure the 'Pair Device' dialog is open on your pump.")
                        })
                    } else {
                        Line("Connecting to '${setupDeviceName.value}'")
                    }
                    Spacer(Modifier.height(16.dp))
                    when (ds.setupDeviceName.value?.let { determinePumpModel(it) }) {
                        KnownDeviceModel.TSLIM_X2 -> {
                            Line(buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append("For t:slim X2: ")
                                }

                                append("Please enter the pairing code displayed at:")
                            })
                            Line("Bluetooth Settings > Pair Device", bold = true)
                        }
                        KnownDeviceModel.MOBI -> {
                            Line(buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append("For Mobi: ")
                                }
                                append("Please enter the pairing PIN located adjacent to the cartridge area.")
                                Line("Once you hit Pair, press the pump button twice until you hear a beep to accept the connection.", bold = true)
                            })
                        }
                        else -> {}
                    }

                    Spacer(Modifier.height(32.dp))


                    when (setupPairingCodeType.value) {
                        PairingCodeType.LONG_16CHAR -> {
                            BasicTextField(
                                value = pairingCodeText,
                                onValueChange = {
                                    fun filterLongPairingCode(text: String): String {
                                        var processed = ""
                                        for (c in text.toCharArray()) {
                                            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') {
                                                processed += c
                                            }
                                        }
                                        return processed
                                    }

                                    val newPairingCode = filterLongPairingCode(it)
                                    if (pairingCodeText.length < 16 && newPairingCode.length == 16) {
                                        focusManager.clearFocus()
                                    }

                                    pairingCodeText = newPairingCode
                                    Timber.i("newPairingCode(LONG_16CHAR): $newPairingCode")
                                    PumpState.setPairingCode(context, newPairingCode)
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
                        }
                        PairingCodeType.SHORT_6CHAR -> {
                            BasicTextField(
                                value = pairingCodeText,
                                onValueChange = {
                                    fun filterShortPairingCode(text: String): String {
                                        var processed = ""
                                        for (c in text.toCharArray()) {
                                            if (c in '0'..'9') {
                                                processed += c
                                            }
                                        }
                                        return processed
                                    }

                                    val newPairingCode = filterShortPairingCode(it)
                                    if (pairingCodeText.length < 6 && newPairingCode.length == 6) {
                                        focusManager.clearFocus()
                                    }

                                    pairingCodeText = newPairingCode
                                    Timber.i("newPairingCode(SHORT_6CHAR): $newPairingCode")
                                    PumpState.setPairingCode(context, newPairingCode)
                                },
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Number,
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
                                        repeat(6) { index ->
                                            val chars = when {
                                                1*index >= pairingCodeText.length -> ""
                                                1*(index+1) >= pairingCodeText.length -> pairingCodeText.substring(1 * index)
                                                else -> pairingCodeText.substring(1 * index, 1 * (index+1))
                                            }
                                            val isFocused = index == pairingCodeText.length / 1
                                            Text(
                                                text = chars,
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.DarkGray,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .width(30.dp)
                                                    .border(
                                                        if (isFocused) 2.dp else 1.dp,
                                                        if (isFocused) Color.DarkGray else Color.LightGray,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(2.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                    }
                                }
                            )

                        }
                        null -> {}
                    }

                    LaunchedEffect(Unit) {
                        try {
                            focusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            Timber.w(e);
                        }
                    }
                }
            )
        }
    }
}

enum class PumpSetupStage(val step: Int, val description: String) {
    PERMISSIONS_NOT_GRANTED(0, "Permissions not granted"),
    WAITING_PUMP_FINDER_INIT(1, "Waiting for PumpFinder init"),
    PUMP_FINDER_SEARCHING_FOR_PUMPS(1, "Searching for Tandem pumps"),
    PUMP_FINDER_SELECT_PUMP(2, "Select a pump to connect to"),
    PUMP_FINDER_CHOOSE_PAIRING_CODE_TYPE(3, "Choose pairing code type"),
    PUMP_FINDER_ENTER_PAIRING_CODE(4, "Enter pairing code"),
    WAITING_PUMP_FINDER_CLEANUP(5, "Establishing connection: Waiting for PumpFinder to clean up"),
    WAITING_PUMPX2_INIT(5, "Establishing connection: Waiting for PumpX2 init"),
    PUMPX2_SEARCHING_FOR_PUMP(5, "Establishing connection: Searching for pump"),
    PUMPX2_PUMP_DISCONNECTED(5, "Pump disconnected, reconnecting"),
    PUMPX2_PUMP_DISCOVERED(5, "Establishing connection: Pump discovered, connecting"),
    PUMPX2_PUMP_MODEL_METADATA(5, "Establishing connection: Initial pump metadata received"),
    PUMPX2_INITIAL_PUMP_CONNECTION(6, "Establishing connection: Initial connection established"),
    PUMPX2_WAITING_FOR_PAIRING_CODE(6, "Establishing connection: Waiting to send pairing code"),
    PUMPX2_SENDING_PAIRING_CODE(6, "Establishing connection: Sending pairing code"),
    PUMPX2_INVALID_PAIRING_CODE(4, "Invalid pairing code"),
    PUMPX2_PUMP_CONNECTED(7, "Pairing code accepted"),
    ;

    fun nextStage(stage: PumpSetupStage): PumpSetupStage {
        Timber.d("PumpSetupStage.nextStage(%s) from %s", stage, this)
        if (stage == PUMPX2_INVALID_PAIRING_CODE) {
            return stage
        }

        if (this == PUMPX2_SENDING_PAIRING_CODE && stage == PUMPX2_PUMP_DISCONNECTED) {
            return PUMPX2_INVALID_PAIRING_CODE
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
    ControlX2Theme() {
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