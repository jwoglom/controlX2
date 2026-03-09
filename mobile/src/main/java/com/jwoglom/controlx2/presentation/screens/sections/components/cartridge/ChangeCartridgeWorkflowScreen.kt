package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
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
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse

@Composable
fun ChangeCartridgeWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    basalStatus: BasalStatus?,
    inChangeCartridgeMode: Boolean,
    enterChangeCartridgeState: EnterChangeCartridgeModeStateStreamResponse?,
    detectingCartridgeState: DetectingCartridgeStateStreamResponse?,
    onBack: () -> Unit,
    onEnter: () -> Unit,
    onExit: () -> Unit,
    onDone: () -> Unit,
) {
    CartridgeWorkflowScreen(title = "Change Cartridge", innerPadding = innerPadding, onBack = onBack,
        body = {
            when {
                detectingCartridgeState != null -> {
                    Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (detectingCartridgeState.isComplete) {
                        Text("Cartridge change complete.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text("Detecting insulin in the cartridge...", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${detectingCartridgeState.percentComplete}% complete", style = MaterialTheme.typography.bodyMedium)
                }
                enterChangeCartridgeState?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE -> {
                    Text("Next step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You can now remove the cartridge from the pump.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("When you've inserted a new cartridge, press 'Cartridge Inserted' button below.", style = MaterialTheme.typography.bodyLarge)
                }
                inChangeCartridgeMode -> {
                    Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Preparing to change cartridge...", style = MaterialTheme.typography.bodyLarge)
                }
                basalStatus == BasalStatus.PUMP_SUSPENDED -> {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Disconnect your pump from your body/site and press 'Change Cartridge' button below.", style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Before changing your cartridge, stop delivery of insulin.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        actions = {
            when {
                detectingCartridgeState?.isComplete == true -> PrimaryActionButton("Done", onClick = onDone)
                enterChangeCartridgeState?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE ->
                    PrimaryActionButton("Cartridge Inserted", onClick = onExit)
                else -> PrimaryActionButton(
                    text = "Change Cartridge",
                    enabled = basalStatus == BasalStatus.PUMP_SUSPENDED,
                    onClick = onEnter,
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ChangeCartridgeWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            ChangeCartridgeWorkflowScreen(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                inChangeCartridgeMode = false,
                enterChangeCartridgeState = null,
                detectingCartridgeState = null,
                onBack = {},
                onEnter = {},
                onExit = {},
                onDone = {},
            )
        }
    }
}
