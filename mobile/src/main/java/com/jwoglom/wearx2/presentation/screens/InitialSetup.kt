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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.components.DialogScreen
import com.jwoglom.wearx2.presentation.theme.WearX2Theme

@Composable
fun InitialSetup(
    navController: NavHostController? = null
) {
    val ds = LocalDataStore.current
    val setupStage = ds.setupStage.observeAsState()

    var progress by remember { mutableStateOf(0.0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    var pairingCodeText by remember { mutableStateOf("") }

    LaunchedEffect (setupStage.value) {
        progress = ((setupStage.value?.ordinal ?: 0) / SetupStage.FINAL_STAGE.ordinal).toFloat()
    }

    DialogScreen(
        "Pump Setup",
        buttonContent = {
            Button(
                onClick = {
                    when (ds.setupStage.value) {
                        SetupStage.WAITING_PUMPX2_INIT -> {
                            navController?.popBackStack()
                        }
                        else -> {
                            ds.setupStage.value = SetupStage.values()[setupStage.value!!.ordinal - 1]
                        }
                    }
                }
            ) {
                Text("Back")
            }
            when (setupStage.value) {
                SetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE -> {
                    Button(
                        onClick = {
                            // TODO: pair
                            ds.setupStage.value = SetupStage.values()[setupStage.value!!.ordinal + 1]
                        }
                    ) {
                        Text("Pair")
                    }
                }
                SetupStage.FINAL_STAGE -> {
                    Button(
                        onClick = {}
                    ) {
                        Text("Next")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            ds.setupStage.value = SetupStage.values()[setupStage.value!!.ordinal + 1]
                        }
                    ) {
                        Text("Advance for testing")
                    }
                }
            }
        }
    ) {
        item {
            Line(
                "${setupStage.value?.description} (stage ${1+ (setupStage.value?.ordinal ?: 0)} of ${1+SetupStage.FINAL_STAGE.ordinal})",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp)
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {}
                    .padding(10.dp)
                    .fillMaxWidth(),
                progress = animatedProgress,
            )
        }
        item {
            when (setupStage.value) {
                SetupStage.PUMPX2_SEARCHING_FOR_PUMP -> {
                    Line("Open your pump and select:")
                    Line("Options > Device Settings > Bluetooth Settings", bold = true)
                    Spacer(Modifier.height(16.dp))
                    Line("Enable the 'Mobile Connection' option and press 'Pair Device.' If already paired, press 'Unpair Device' first.")
                }
                SetupStage.PUMPX2_PUMP_DISCOVERED -> {
                    Line("Connecting to '${ds.setupDeviceName.value}'")
                }
                SetupStage.PUMPX2_INITIAL_PUMP_CONNECTION -> {
                    Line("Initial connection made to '${ds.setupDeviceName.value}'")
                }
                SetupStage.PUMPX2_PUMP_MODEL -> {
                    Line("Initial connection made to '${ds.setupDeviceName.value}'")
                }
                SetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE -> {
                    Line("Initial connection made to '${ds.setupDeviceName.value}'")
                    Spacer(Modifier.height(16.dp))
                    Line("Please enter the pairing code displayed at:")
                    Line("Bluetooth Settings > Pair Device", bold = true)
                    Spacer(Modifier.height(32.dp))
                    val focusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current

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
                        decorationBox = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.focusRequester(focusRequester).fillMaxWidth()
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
                                        color = Color.DarkGray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .width(60.dp)
                                            .border(
                                                if (isFocused) 2.dp else 1.dp,
                                                if (isFocused) Color.DarkGray else Color.LightGray,
                                                RoundedCornerShape(8.dp))
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
                else -> {}
            }
        }
    }
}

enum class SetupStage(val description: String) {
    WAITING_PUMPX2_INIT("Waiting for PumpX2 init"),
    PUMPX2_SEARCHING_FOR_PUMP("Searching for pump"),
    PUMPX2_PUMP_DISCOVERED("Pump discovered, connecting"),
    PUMPX2_INITIAL_PUMP_CONNECTION("Initial connection established"),
    PUMPX2_PUMP_MODEL("Initial connection established"),
    PUMPX2_WAITING_FOR_PAIRING_CODE("Waiting for pairing code"),
    PUMPX2_INVALID_PAIRING_CODE("Invalid pairing code"),
    PUMPX2_PAIRING_CODE_ACCEPTED("Pairing code accepted"),
    FINAL_STAGE("Complete"),
    ;
}

@Composable
private fun Line(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        fontWeight = when (bold) {
            true -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            LocalDataStore.current.setupStage.value = SetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE
            InitialSetup()
        }
    }
}