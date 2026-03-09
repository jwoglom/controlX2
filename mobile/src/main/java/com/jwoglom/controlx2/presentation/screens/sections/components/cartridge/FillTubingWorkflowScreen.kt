package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import kotlinx.coroutines.Job

@Composable
fun FillTubingWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    basalStatus: BasalStatus?,
    inFillTubingMode: Boolean,
    fillTubingButtonDown: Boolean?,
    exitFillTubingState: ExitFillTubingModeStateStreamResponse?,
    exitRequested: Boolean,
    activeNotifications: List<Any>,
    notificationsRefreshing: Boolean,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    refreshNotifications: () -> Job,
    onDismiss: () -> Unit,
    onSuspend: () -> Unit,
    onBeginFillTubing: () -> Unit,
    onFinishFillTubing: () -> Unit,
    onDone: () -> Unit,
    onCancelInProgress: () -> Unit,
) {
    var showSuspendConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }
    var loadingSuspend by rememberSaveable { mutableStateOf(false) }
    var loadingEnterMode by rememberSaveable { mutableStateOf(false) }
    var loadingExitMode by rememberSaveable { mutableStateOf(false) }
    var hasDisplayedFlow by rememberSaveable { mutableStateOf(false) }

    val step = when {
        exitFillTubingState?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED -> FillTubingStep.DONE
        exitRequested || (exitFillTubingState != null && !inFillTubingMode) -> FillTubingStep.EXITING
        inFillTubingMode -> FillTubingStep.FILLING
        basalStatus.isSuspendedForCartridgeWorkflow() -> FillTubingStep.ENTER_MODE
        else -> FillTubingStep.SUSPEND
    }

    val hasActiveNotifications = activeNotifications.isNotEmpty()
    val startedFlow = inFillTubingMode || exitRequested || exitFillTubingState != null

    LaunchedEffect(fillTubingButtonDown) {
        if (fillTubingButtonDown == true) {
            hasDisplayedFlow = true
        }
    }

    LaunchedEffect(step, basalStatus) {
        if (step != FillTubingStep.SUSPEND || basalStatus.isSuspendedForCartridgeWorkflow()) {
            loadingSuspend = false
        }
        if (step != FillTubingStep.ENTER_MODE && step != FillTubingStep.FILLING) {
            loadingEnterMode = false
        }
        if (step != FillTubingStep.EXITING) {
            loadingExitMode = false
        }
        if (step == FillTubingStep.SUSPEND) {
            hasDisplayedFlow = false
        }
    }

    CartridgeWorkflowScreen(
        title = "Fill Tubing",
        innerPadding = innerPadding,
        stepInfo = WizardStepInfo(step.stepNumber, 5),
        canCancel = true,
        onCancel = {
            if (startedFlow && step != FillTubingStep.DONE) {
                showCancelConfirm = true
            } else {
                onDismiss()
            }
        },
        body = {
            when (step) {
                FillTubingStep.SUSPEND -> {
                    Text("Important", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Filling tubing requires suspending insulin delivery. You will use the pump button to fill.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FillTubingStep.ENTER_MODE -> {
                    Text("Clear all active pump notifications before continuing.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    CartridgeNotificationsPanel(
                        notifications = activeNotifications,
                        refreshing = notificationsRefreshing,
                        sendPumpCommands = sendPumpCommands,
                        refreshNotifications = refreshNotifications,
                    )
                    if (hasActiveNotifications) {
                        NotificationsBlockingWarning("Clear all notifications before starting tubing fill.")
                    }
                }
                FillTubingStep.FILLING -> {
                    Text("Press and hold the button on your pump to fill tubing.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Release the button when you see insulin at the tubing tip.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    when (fillTubingButtonDown) {
                        true -> {
                            Text("FILLING... keep holding the pump button.", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        false -> Text("Pump button released. Can you see insulin at the end of the tubing?", style = MaterialTheme.typography.bodyLarge)
                        null -> Text("Waiting for tubing fill input...", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (hasActiveNotifications) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CartridgeNotificationsPanel(
                            notifications = activeNotifications,
                            refreshing = notificationsRefreshing,
                            sendPumpCommands = sendPumpCommands,
                            refreshNotifications = refreshNotifications,
                        )
                        NotificationsBlockingWarning("Clear all notifications before completing tubing fill.")
                    }
                }
                FillTubingStep.EXITING -> {
                    Text("Finalizing tubing fill process...", style = MaterialTheme.typography.bodyLarge)
                }
                FillTubingStep.DONE -> {
                    Text("You can now fill cannula and resume insulin.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        actions = {
            when (step) {
                FillTubingStep.SUSPEND -> {
                    PrimaryActionButton(
                        text = "Suspend insulin delivery",
                        loading = loadingSuspend,
                        onClick = { showSuspendConfirm = true },
                    )
                }
                FillTubingStep.ENTER_MODE -> {
                    PrimaryActionButton(
                        text = "Start tubing fill",
                        loading = loadingEnterMode,
                        enabled = !hasActiveNotifications,
                        onClick = {
                            loadingEnterMode = true
                            onBeginFillTubing()
                        },
                    )
                }
                FillTubingStep.FILLING -> {
                    when {
                        fillTubingButtonDown == true -> {
                            PrimaryActionButton(
                                text = "Filling...",
                                enabled = false,
                                onClick = {},
                            )
                        }
                        hasDisplayedFlow -> {
                            PrimaryActionButton(
                                text = "Complete tubing fill",
                                loading = loadingExitMode,
                                enabled = !hasActiveNotifications,
                                onClick = {
                                    loadingExitMode = true
                                    onFinishFillTubing()
                                },
                            )
                        }
                        else -> {
                            PrimaryActionButton(
                                text = "Hold pump button to fill",
                                enabled = false,
                                onClick = {},
                            )
                        }
                    }
                }
                FillTubingStep.EXITING -> {
                    PrimaryActionButton(
                        text = "Exiting fill mode...",
                        enabled = false,
                        onClick = {},
                    )
                }
                FillTubingStep.DONE -> PrimaryActionButton("Done", onClick = onDone)
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

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel tubing fill?") },
            text = { Text("The tubing fill is not complete. The app will try to exit fill mode.") },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("No, continue")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancelInProgress()
                    onDismiss()
                }) {
                    Text("Yes, cancel")
                }
            },
        )
    }
}

private enum class FillTubingStep(val stepNumber: Int) {
    SUSPEND(1),
    ENTER_MODE(2),
    FILLING(3),
    EXITING(4),
    DONE(5),
}

private fun BasalStatus?.isSuspendedForCartridgeWorkflow(): Boolean {
    return this == BasalStatus.PUMP_SUSPENDED || this == BasalStatus.BASALIQ_SUSPENDED
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
                exitRequested = false,
                activeNotifications = emptyList(),
                notificationsRefreshing = false,
                sendPumpCommands = { _, _ -> },
                refreshNotifications = { Job() },
                onDismiss = {},
                onSuspend = {},
                onBeginFillTubing = {},
                onFinishFillTubing = {},
                onDone = {},
                onCancelInProgress = {},
            )
        }
    }
}
