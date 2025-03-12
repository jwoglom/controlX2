@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.MalfunctionStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NotificationItem(
    notification: Any,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    refresh: () -> Job
) {
    val refreshScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun dismissNotification() {
        refreshScope.launch {
            when (notification) {
                is AlertStatusResponse.AlertResponseType -> {
                    sendPumpCommands(
                        SendType.BUST_CACHE, listOf(
                            DismissNotificationRequest(
                                DismissNotificationRequest.NotificationType.ALERT,
                                notification.bitmask().toLong()
                            )
                        )
                    )
                }

                is ReminderStatusResponse.ReminderType -> {
                    sendPumpCommands(
                        SendType.BUST_CACHE, listOf(
                            DismissNotificationRequest(
                                DismissNotificationRequest.NotificationType.REMINDER,
                                notification.id().toLong()
                            )
                        )
                    )
                }

                is AlarmStatusResponse.AlarmResponseType -> {
                    sendPumpCommands(
                        SendType.BUST_CACHE, listOf(
                            DismissNotificationRequest(
                                DismissNotificationRequest.NotificationType.ALARM,
                                notification.bitmask().toLong()
                            )
                        )
                    )
                }

                is CGMAlertStatusResponse.CGMAlert -> {
                    sendPumpCommands(
                        SendType.BUST_CACHE, listOf(
                            DismissNotificationRequest(
                                DismissNotificationRequest.NotificationType.CGM_ALERT,
                                notification.id().toLong()
                            )
                        )
                    )
                }
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(500)
                refresh()
            }
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
                    dismissNotification()
                    Toast.makeText(context, "Dismissing notification", Toast.LENGTH_SHORT).show()
                }
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            return@rememberSwipeToDismissBoxState true
        },
        // positional threshold of 25%
        positionalThreshold = { it * .25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { DismissBackground(dismissState)},
        content = {
            ListItem(
                headlineContent = {
                    Text(
                        when (notification) {
                            is AlertStatusResponse.AlertResponseType -> "Alert: ${notification.name}"
                            is ReminderStatusResponse.ReminderType -> "Reminder: ${notification.name}"
                            is AlarmStatusResponse.AlarmResponseType -> "Alarm: ${notification.name}"
                            is CGMAlertStatusResponse.CGMAlert -> "CGM Alert: ${notification.name}"
                            is MalfunctionStatusResponse -> "MALFUNCTION: ${notification.errorString}"
                            else -> "$notification"
                        }
                    )
                },
                supportingContent = {
                    when (notification) {
                        is AlertStatusResponse.AlertResponseType -> Text(
                            notification.description ?: ""
                        )

                        is AlarmStatusResponse.AlarmResponseType -> Text(
                            notification.description ?: ""
                        )

                        is MalfunctionStatusResponse -> Text("This alert cannot be cleared and DIY app developers cannot assist you with this problem.\nFor further instructions please contact Tandem technical support and reference the above code.")
                        else -> {}
                    }
                },
                leadingContent = {
                    when (notification) {
                        is AlertStatusResponse.AlertResponseType -> Icon(
                            Icons.Filled.Info,
                            contentDescription = "Alert"
                        )

                        is ReminderStatusResponse.ReminderType -> Icon(
                            Icons.Filled.Info,
                            contentDescription = "Reminder"
                        )

                        is AlarmStatusResponse.AlarmResponseType -> Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Alarm"
                        )

                        is CGMAlertStatusResponse.CGMAlert -> Icon(
                            Icons.Filled.Info,
                            contentDescription = "CGM Alert"
                        )

                        is MalfunctionStatusResponse -> Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Malfunction"
                        )
                    }
                }
            )
        }
    )
}

@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val color = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF1744)
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Dismiss"
        )
        Spacer(modifier = Modifier)
        Icon(
            Icons.Default.Delete,
            contentDescription = "Dismiss"
        )
    }
}