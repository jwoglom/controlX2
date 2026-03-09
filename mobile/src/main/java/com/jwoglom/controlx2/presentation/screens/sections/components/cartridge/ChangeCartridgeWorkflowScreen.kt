package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import kotlinx.coroutines.Job

@Composable
fun ChangeCartridgeWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    basalStatus: BasalStatus?,
    inChangeCartridgeMode: Boolean,
    enterChangeCartridgeState: EnterChangeCartridgeModeStateStreamResponse?,
    detectingCartridgeState: DetectingCartridgeStateStreamResponse?,
    activeNotifications: List<Any>,
    notificationsRefreshing: Boolean,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    refreshNotifications: () -> Job,
    onDismiss: () -> Unit,
    onSuspend: () -> Unit,
    onEnter: () -> Unit,
    onExit: () -> Unit,
    onDone: () -> Unit,
    onCancelInProgress: () -> Unit,
) {
    var showSuspendConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }
    var loadingSuspend by rememberSaveable { mutableStateOf(false) }
    var loadingEnterMode by rememberSaveable { mutableStateOf(false) }

    val step = when {
        detectingCartridgeState?.isComplete == true -> ChangeCartridgeStep.DONE
        detectingCartridgeState != null -> ChangeCartridgeStep.DETECTING
        enterChangeCartridgeState?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE ->
            ChangeCartridgeStep.PHYSICAL_CHANGE
        inChangeCartridgeMode -> ChangeCartridgeStep.WAITING_FOR_READY
        basalStatus.isSuspendedForCartridgeWorkflow() -> ChangeCartridgeStep.ENTER_MODE
        else -> ChangeCartridgeStep.SUSPEND
    }

    val hasActiveNotifications = activeNotifications.isNotEmpty()
    val startedFlow = inChangeCartridgeMode || enterChangeCartridgeState != null || detectingCartridgeState != null

    LaunchedEffect(step, basalStatus) {
        if (step != ChangeCartridgeStep.SUSPEND || basalStatus.isSuspendedForCartridgeWorkflow()) {
            loadingSuspend = false
        }
        if (step != ChangeCartridgeStep.ENTER_MODE && step != ChangeCartridgeStep.WAITING_FOR_READY) {
            loadingEnterMode = false
        }
    }

    CartridgeWorkflowScreen(
        title = "Change Cartridge",
        innerPadding = innerPadding,
        stepInfo = WizardStepInfo(step.stepNumber, 6),
        canCancel = true,
        onCancel = {
            if (startedFlow && step != ChangeCartridgeStep.DONE) {
                showCancelConfirm = true
            } else {
                onDismiss()
            }
        },
        body = {
            when (step) {
                ChangeCartridgeStep.SUSPEND -> {
                    Text("Important", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Changing your cartridge requires suspending insulin delivery first.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                ChangeCartridgeStep.ENTER_MODE -> {
                    Text("Step 2: Prepare Pump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Clear all active pump notifications before continuing.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    CartridgeNotificationsPanel(
                        notifications = activeNotifications,
                        refreshing = notificationsRefreshing,
                        sendPumpCommands = sendPumpCommands,
                        refreshNotifications = refreshNotifications,
                    )
                    if (hasActiveNotifications) {
                        NotificationsBlockingWarning("Clear all notifications before starting cartridge change.")
                    }
                }
                ChangeCartridgeStep.WAITING_FOR_READY -> {
                    Text("Step 3: Waiting for Pump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("The pump is preparing for cartridge change.", style = MaterialTheme.typography.bodyLarge)
                }
                ChangeCartridgeStep.PHYSICAL_CHANGE -> {
                    Text("Step 4: Replace Cartridge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Remove the old cartridge.", style = MaterialTheme.typography.bodyLarge)
                    Text("2. Insert a new cartridge and secure it.", style = MaterialTheme.typography.bodyLarge)
                    Text("3. Press 'New Cartridge Inserted' when complete.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    CartridgeNotificationsPanel(
                        notifications = activeNotifications,
                        refreshing = notificationsRefreshing,
                        sendPumpCommands = sendPumpCommands,
                        refreshNotifications = refreshNotifications,
                    )
                    if (hasActiveNotifications) {
                        NotificationsBlockingWarning("Clear all notifications before continuing.")
                    }
                }
                ChangeCartridgeStep.DETECTING -> {
                    Text("Step 5: Detecting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Detecting insulin in cartridge...", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${detectingCartridgeState?.percentComplete ?: 0}% complete",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ChangeCartridgeStep.DONE -> {
                    Text("Cartridge change complete!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You can now fill tubing and fill cannula.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        actions = {
            when (step) {
                ChangeCartridgeStep.SUSPEND -> {
                    PrimaryActionButton(
                        text = "Suspend insulin delivery",
                        loading = loadingSuspend,
                        onClick = { showSuspendConfirm = true },
                    )
                }
                ChangeCartridgeStep.ENTER_MODE -> {
                    PrimaryActionButton(
                        text = "Start cartridge change",
                        loading = loadingEnterMode,
                        enabled = !hasActiveNotifications,
                        onClick = {
                            loadingEnterMode = true
                            onEnter()
                        },
                    )
                }
                ChangeCartridgeStep.WAITING_FOR_READY -> {
                    PrimaryActionButton(
                        text = "Waiting for pump...",
                        enabled = false,
                        onClick = {},
                    )
                }
                ChangeCartridgeStep.PHYSICAL_CHANGE -> {
                    PrimaryActionButton(
                        text = "New Cartridge Inserted",
                        enabled = !hasActiveNotifications,
                        onClick = onExit,
                    )
                }
                ChangeCartridgeStep.DETECTING -> {
                    PrimaryActionButton(
                        text = "Detecting...",
                        enabled = false,
                        onClick = {},
                    )
                }
                ChangeCartridgeStep.DONE -> {
                    PrimaryActionButton("Done", onClick = onDone)
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

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel cartridge change?") },
            text = { Text("The cartridge change is not complete. The app will try to exit change mode.") },
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

private enum class ChangeCartridgeStep(val stepNumber: Int) {
    SUSPEND(1),
    ENTER_MODE(2),
    WAITING_FOR_READY(3),
    PHYSICAL_CHANGE(4),
    DETECTING(5),
    DONE(6),
}

private fun BasalStatus?.isSuspendedForCartridgeWorkflow(): Boolean {
    return this == BasalStatus.PUMP_SUSPENDED || this == BasalStatus.BASALIQ_SUSPENDED
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
                activeNotifications = emptyList(),
                notificationsRefreshing = false,
                sendPumpCommands = { _, _ -> },
                refreshNotifications = { Job() },
                onDismiss = {},
                onSuspend = {},
                onEnter = {},
                onExit = {},
                onDone = {},
                onCancelInProgress = {},
            )
        }
    }
}
