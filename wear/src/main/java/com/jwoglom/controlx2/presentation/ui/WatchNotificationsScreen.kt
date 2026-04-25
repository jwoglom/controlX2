package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HighestAamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watch pump alerts / alarms / reminders / CGM alerts list with per-row
 * dismiss. Works in both `DeviceRole` values — `to-pump` messages route
 * through `HybridMessageBus` (PUMP_HOST → local BT, CLIENT → forwarded to
 * phone), so one code path covers both modes. Path reference written
 * without the slash-star suffix on purpose; Kotlin treats it as a nested
 * comment opener inside KDoc (see commits 7db3dc9, a9df6ac, 61a0262 for
 * the same trap).
 *
 * Reads from `dataStore.notificationBundle`, which `MainActivity.onPump
 * MessageReceived` populates whenever a `NotificationBundle.isNotification
 * Response(message)` arm fires — same shape mobile uses (mobile
 * `MainActivity.kt:820-825`). On entry, fires the full
 * `NotificationBundle.allRequests()` set with BUST_CACHE so the list
 * reflects current pump state instead of a stale cache.
 *
 * Dismissal mirrors mobile `NotificationItem.dismissNotification()` —
 * `DismissNotificationRequest(type, bitmask-or-id)` with `STANDARD`
 * SendType, `delay(500)` to let the pump apply the change, then the
 * full refresh request burst. `HighestAamResponse` (Tandem pump
 * malfunction) is surfaced read-only since pumpx2 doesn't expose a
 * dismiss path for it. On TSLIM_X2 the dismiss request is silently
 * accepted by the pump but doesn't actually clear the notification —
 * mobile surfaces this as an informational line; watch UI doesn't yet,
 * documented as a known limitation.
 */
@Composable
fun WatchNotificationsScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val ds = LocalDataStore.current
    val notificationBundle by ds.notificationBundle.observeAsState()

    fun refreshAll() {
        sendPumpCommands(
            SendType.BUST_CACHE,
            NotificationBundle.allRequests().toList(),
        )
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    val refreshScope = rememberCoroutineScope()
    var pendingDismiss by remember { mutableStateOf<Any?>(null) }

    val pending = pendingDismiss
    if (pending != null) {
        DismissConfirmAlert(
            notification = pending,
            onCancel = { pendingDismiss = null },
            onConfirm = {
                val request = buildDismissRequest(pending)
                if (request != null) {
                    refreshScope.launch {
                        sendPumpCommands(SendType.STANDARD, listOf(request))
                        delay(500)
                        refreshAll()
                    }
                }
                pendingDismiss = null
            },
        )
        return
    }

    val items = notificationBundle?.get()?.toList().orEmpty()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollableColumn(focusRequester, scalingLazyListState)
            .padding(horizontal = 8.dp),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams(),
    ) {
        item {
            Text(
                text = "Pump alerts",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (items.isEmpty()) {
            item {
                Text(
                    text = "No active alerts.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        } else {
            items.forEach { notification ->
                item {
                    NotificationChip(
                        notification = notification,
                        onClick = { pendingDismiss = notification },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationChip(
    notification: Any,
    onClick: () -> Unit,
) {
    val canDismiss = canDismissNotification(notification)
    Chip(
        onClick = { if (canDismiss) onClick() },
        enabled = canDismiss,
        label = {
            Text(
                text = notificationLabel(notification),
                fontSize = 13.sp,
            )
        },
        secondaryLabel = {
            Text(
                text = if (canDismiss) "Tap to dismiss" else "Cannot be dismissed",
                fontSize = 10.sp,
            )
        },
        colors = if (canDismiss) ChipDefaults.primaryChipColors()
            else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DismissConfirmAlert(
    notification: Any,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isAlarm = notification is AlarmStatusResponse.AlarmResponseType
    Alert(
        title = {
            Text(
                text = "Dismiss ${notificationLabel(notification)}?",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
            )
        },
        negativeButton = {
            Button(onClick = onCancel, colors = ButtonDefaults.secondaryButtonColors()) {
                Icon(Icons.Filled.Clear, contentDescription = "Keep")
            }
        },
        positiveButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.primaryButtonColors()) {
                Icon(Icons.Filled.Check, contentDescription = "Dismiss")
            }
        },
        icon = {
            Image(
                imageVector = if (isAlarm) Icons.Filled.Warning else Icons.Filled.Notifications,
                contentDescription = "Notification",
                modifier = Modifier.size(24.dp),
            )
        },
    ) {}
}

private fun notificationLabel(notification: Any): String = when (notification) {
    is AlertStatusResponse.AlertResponseType -> "Alert: ${notification.name}"
    is AlarmStatusResponse.AlarmResponseType -> "Alarm: ${notification.name}"
    is ReminderStatusResponse.ReminderType -> "Reminder: ${notification.name}"
    is CGMAlertStatusResponse.CGMAlert -> "CGM: ${notification.name}"
    is HighestAamResponse -> "Malfunction: ${notification.errorString ?: ""}"
    else -> notification.toString()
}

private fun canDismissNotification(notification: Any): Boolean = when (notification) {
    is AlertStatusResponse.AlertResponseType,
    is AlarmStatusResponse.AlarmResponseType,
    is ReminderStatusResponse.ReminderType,
    is CGMAlertStatusResponse.CGMAlert -> true
    else -> false
}

private fun buildDismissRequest(notification: Any): DismissNotificationRequest? = when (notification) {
    is AlertStatusResponse.AlertResponseType ->
        DismissNotificationRequest(
            DismissNotificationRequest.NotificationType.ALERT,
            notification.bitmask().toLong(),
        )
    is AlarmStatusResponse.AlarmResponseType ->
        DismissNotificationRequest(
            DismissNotificationRequest.NotificationType.ALARM,
            notification.bitmask().toLong(),
        )
    is ReminderStatusResponse.ReminderType ->
        DismissNotificationRequest(
            DismissNotificationRequest.NotificationType.REMINDER,
            notification.id().toLong(),
        )
    is CGMAlertStatusResponse.CGMAlert ->
        DismissNotificationRequest(
            DismissNotificationRequest.NotificationType.CGM_ALERT,
            notification.id().toLong(),
        )
    else -> null
}
