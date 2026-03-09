package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse

@Composable
fun FillCannulaWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    basalStatus: BasalStatus?,
    fillCannulaState: FillCannulaStateStreamResponse?,
    cannulaFillAmount: Double?,
    cannulaFillAmountStr: String?,
    allowedCannulaFillAmount: (Double?) -> Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onCannulaAmountChange: (String?, Double?) -> Unit,
    onSendFillRequest: () -> Unit,
) {
    CartridgeWorkflowScreen(title = "Fill Cannula", innerPadding = innerPadding, onBack = onBack,
        body = {
            when {
                fillCannulaState != null -> {
                    Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (fillCannulaState.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED) {
                        Text("Cannula filled.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text("Filling cannula with ${cannulaFillAmount} units...", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("State: ${fillCannulaState.stateId}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                basalStatus == BasalStatus.PUMP_SUSPENDED -> {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Enter the cannula fill size:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    DecimalOutlinedText(
                        title = "Fill size",
                        value = cannulaFillAmountStr,
                        onValueChange = {
                            val amount = if (it.isNullOrEmpty()) {
                                null
                            } else {
                                it.toDoubleOrNull()?.takeIf(allowedCannulaFillAmount)
                            }
                            onCannulaAmountChange(it, amount)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quick amounts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0.1" to "0.1U", "0.3" to "0.3U", "0.5" to "0.5U", "1.0" to "1.0U").forEach { (value, label) ->
                            Button(onClick = { onCannulaAmountChange(value, value.toDouble()) }, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        }
                    }
                }
                else -> {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Before filling your cannula, stop delivery of insulin.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        actions = {
            when {
                fillCannulaState?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED ->
                    PrimaryActionButton("Done", onClick = onDone)
                basalStatus == BasalStatus.PUMP_SUSPENDED ->
                    PrimaryActionButton(
                        text = if (allowedCannulaFillAmount(cannulaFillAmount)) "Fill ${cannulaFillAmount}u Cannula" else "Fill Cannula",
                        enabled = allowedCannulaFillAmount(cannulaFillAmount),
                        onClick = onSendFillRequest,
                    )
                else -> TextButton(onClick = onBack) { Text("Back") }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun FillCannulaWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            FillCannulaWorkflowScreen(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                fillCannulaState = null,
                cannulaFillAmount = null,
                cannulaFillAmountStr = null,
                allowedCannulaFillAmount = { it != null && it > 0 && it <= 3.0 },
                onBack = {},
                onDone = {},
                onCannulaAmountChange = { _, _ -> },
                onSendFillRequest = {},
            )
        }
    }
}
