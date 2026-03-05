package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse

@Composable
fun FillTubingWorkflowScreen(
    basalStatus: BasalStatus?,
    inFillTubingMode: Boolean,
    fillTubingButtonDown: Boolean?,
    exitFillTubingState: ExitFillTubingModeStateStreamResponse?,
    willRestartTubingFill: Boolean,
    onBack: () -> Unit,
    onBeginFillTubing: () -> Unit,
    onFinishFillTubing: (restart: Boolean) -> Unit,
    onRestartFill: () -> Unit,
    onDone: () -> Unit,
) {
    CartridgeWorkflowScreen(title = "Fill Tubing", onBack = onBack,
        body = {
            when {
                exitFillTubingState != null -> {
                    Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (exitFillTubingState.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                        if (willRestartTubingFill) {
                            Text("Disconnect your pump from your body/site and press 'Restart Fill' button below.", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text("Successfully filled tubing. Press Done to exit.", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        Text(if (willRestartTubingFill) "Restarting fill, please wait..." else "Please wait, finalizing tubing fill...", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("NOT COMPLETE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                }
                inFillTubingMode && fillTubingButtonDown == null -> {
                    Text("Next step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Hold down the pump button to fill insulin through the tubing.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("You haven't filled any insulin through the tubing yet.", style = MaterialTheme.typography.bodyMedium)
                }
                inFillTubingMode && fillTubingButtonDown == true -> {
                    Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("FILLING... continue to hold down button until you see insulin drops at the cannula.", style = MaterialTheme.typography.bodyLarge)
                }
                inFillTubingMode && fillTubingButtonDown == false -> {
                    Text("Next step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("STOPPED FILLING... do you see insulin drops at the cannula?", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("If not, hold down the pump button to continue filling the tubing.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("If holding down the pump button does not move the motor, press the 'Restart Fill' button below to continue filling the tubing.", style = MaterialTheme.typography.bodyLarge)
                }
                basalStatus == BasalStatus.PUMP_SUSPENDED -> {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Disconnect your pump from your body/site and press 'Begin Fill Tubing' button below.", style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Before filling your tubing, stop delivery of insulin.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        actions = {
            when {
                exitFillTubingState?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED -> {
                    PrimaryActionButton(
                        text = if (willRestartTubingFill) "Restart Fill" else "Done",
                        enabled = basalStatus == BasalStatus.PUMP_SUSPENDED,
                        onClick = if (willRestartTubingFill) onRestartFill else onDone,
                    )
                }
                inFillTubingMode && fillTubingButtonDown == false -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onFinishFillTubing(true) },
                            enabled = basalStatus == BasalStatus.PUMP_SUSPENDED,
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) { Text("Restart Fill") }

                        Button(
                            onClick = { onFinishFillTubing(false) },
                            enabled = basalStatus == BasalStatus.PUMP_SUSPENDED,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text("Complete Fill") }
                    }
                }
                else -> PrimaryActionButton(
                    text = "Begin Fill Tubing",
                    enabled = basalStatus == BasalStatus.PUMP_SUSPENDED,
                    onClick = onBeginFillTubing,
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun FillTubingWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            FillTubingWorkflowScreen(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                inFillTubingMode = false,
                fillTubingButtonDown = null,
                exitFillTubingState = null,
                willRestartTubingFill = false,
                onBack = {},
                onBeginFillTubing = {},
                onFinishFillTubing = {},
                onRestartFill = {},
                onDone = {},
            )
        }
    }
}
