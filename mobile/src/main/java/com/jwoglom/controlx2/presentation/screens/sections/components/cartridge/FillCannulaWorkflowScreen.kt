package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun FillCannulaWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    basalStatus: BasalStatus?,
    fillCannulaState: FillCannulaStateStreamResponse?,
    primeAmount: Double,
    onPrimeAmountChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    onSuspend: () -> Unit,
    onResume: () -> Unit,
    onDone: () -> Unit,
    onSendFillRequest: () -> Unit,
) {
    var showSuspendConfirm by rememberSaveable { mutableStateOf(false) }
    var showResumeConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }
    var loadingSuspend by rememberSaveable { mutableStateOf(false) }
    var loadingFill by rememberSaveable { mutableStateOf(false) }
    var loadingResume by rememberSaveable { mutableStateOf(false) }
    var primeAmountText by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", primeAmount)) }

    val step = when {
        fillCannulaState?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED -> FillCannulaStep.DONE
        basalStatus.isSuspendedForCartridgeWorkflow() -> FillCannulaStep.ENTER_AMOUNT
        else -> FillCannulaStep.SUSPEND
    }

    LaunchedEffect(step, basalStatus) {
        if (step != FillCannulaStep.SUSPEND || basalStatus.isSuspendedForCartridgeWorkflow()) {
            loadingSuspend = false
        }
        if (step != FillCannulaStep.ENTER_AMOUNT) {
            loadingFill = false
        }
        if (step != FillCannulaStep.DONE || !basalStatus.isSuspendedForCartridgeWorkflow()) {
            loadingResume = false
        }
    }

    LaunchedEffect(primeAmount) {
        val normalized = String.format(Locale.US, "%.1f", primeAmount)
        if (primeAmountText != normalized) {
            primeAmountText = normalized
        }
    }

    CartridgeWorkflowScreen(
        title = "Fill Cannula",
        innerPadding = innerPadding,
        stepInfo = WizardStepInfo(step.stepNumber, 3),
        canCancel = true,
        onCancel = {
            if (step == FillCannulaStep.ENTER_AMOUNT) {
                showCancelConfirm = true
            } else {
                onDismiss()
            }
        },
        body = {
            when (step) {
                FillCannulaStep.SUSPEND -> {
                    Text("Important", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Filling cannula requires suspending insulin delivery. Select the prime amount carefully.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FillCannulaStep.ENTER_AMOUNT -> {
                    Text("Step 2: Set Prime Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select how much insulin to prime the cannula.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    DecimalOutlinedText(
                        title = "Prime amount (U)",
                        value = primeAmountText,
                        onValueChange = { value ->
                            primeAmountText = value
                            val parsed = value.toDoubleOrNull()
                            if (parsed != null) {
                                val rounded = (parsed.coerceIn(0.1, 2.0) * 10.0).roundToInt() / 10.0
                                onPrimeAmountChange(rounded)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Prime amount: ${"%.1f".format(primeAmount)}U", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = primeAmount.toFloat(),
                        onValueChange = {
                            val rounded = ((it * 10f).roundToInt() / 10f).coerceIn(0.1f, 2.0f)
                            onPrimeAmountChange(rounded.toDouble())
                            primeAmountText = String.format(Locale.US, "%.1f", rounded)
                        },
                        valueRange = 0.1f..2.0f,
                        steps = 18,
                    )
                }
                FillCannulaStep.DONE -> {
                    Text("Cannula fill complete!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${"%.1f".format(primeAmount)}U primed.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        actions = {
            when (step) {
                FillCannulaStep.SUSPEND ->
                    PrimaryActionButton(
                        text = "Suspend insulin delivery",
                        loading = loadingSuspend,
                        onClick = { showSuspendConfirm = true },
                    )
                FillCannulaStep.ENTER_AMOUNT ->
                    PrimaryActionButton(
                        text = "Fill cannula",
                        loading = loadingFill,
                        onClick = {
                            loadingFill = true
                            onSendFillRequest()
                        },
                    )
                FillCannulaStep.DONE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showResumeConfirm = true },
                            enabled = !loadingResume,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text(if (loadingResume) "Working..." else "Resume Insulin Delivery")
                        }
                        Button(
                            onClick = onDone,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    )

    if (showSuspendConfirm) {
        AlertDialog(
            onDismissRequest = { showSuspendConfirm = false },
            title = { Text("Suspend insulin delivery?") },
            text = { Text("This will stop all insulin delivery until you resume.") },
            dismissButton = {
                TextButton(onClick = { showSuspendConfirm = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuspendConfirm = false
                    loadingSuspend = true
                    onSuspend()
                }) {
                    Text("Suspend")
                }
            },
        )
    }

    if (showResumeConfirm) {
        AlertDialog(
            onDismissRequest = { showResumeConfirm = false },
            title = { Text("Resume insulin delivery?") },
            text = { Text("This will restart scheduled basal insulin.") },
            dismissButton = {
                TextButton(onClick = { showResumeConfirm = false }) {
                    Text("Not yet")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showResumeConfirm = false
                    loadingResume = true
                    onResume()
                }) {
                    Text("Resume")
                }
            },
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel cannula fill?") },
            text = { Text("The cannula fill is not complete.") },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("No, continue")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onDismiss()
                }) {
                    Text("Yes, cancel")
                }
            },
        )
    }
}

private enum class FillCannulaStep(val stepNumber: Int) {
    SUSPEND(1),
    ENTER_AMOUNT(2),
    DONE(3),
}

private fun BasalStatus?.isSuspendedForCartridgeWorkflow(): Boolean {
    return this == BasalStatus.PUMP_SUSPENDED || this == BasalStatus.BASALIQ_SUSPENDED
}

@Preview(showBackground = true)
@Composable
private fun FillCannulaWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            FillCannulaWorkflowScreen(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                fillCannulaState = null,
                primeAmount = 0.3,
                onPrimeAmountChange = {},
                onDismiss = {},
                onSuspend = {},
                onResume = {},
                onDone = {},
                onSendFillRequest = {},
            )
        }
    }
}
