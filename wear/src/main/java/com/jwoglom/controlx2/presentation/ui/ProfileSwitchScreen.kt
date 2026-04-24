package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
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
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager

/**
 * Watch active-profile picker. Works in both `DeviceRole` values — the
 * `to-pump` command dispatched on confirm routes through the existing
 * `HybridMessageBus` to either the local BT link (PUMP_HOST) or the phone
 * (CLIENT). Path referenced without its leading slash + trailing wildcard
 * on purpose: Kotlin 2.2 treats a slash-star inside a KDoc block as a
 * nested comment opener, which swallows the rest of the file. Same trap
 * that bit WearHybridMessageBus.kt in commit 7db3dc9.
 *
 * On entry, issues `IDPManager.nextMessages()` to fetch profile data (same
 * refresh shape mobile's `ProfileActions` uses); thereafter re-issues
 * every 60s via `intervalOf`. Tapping a non-active profile opens an
 * inline `Alert` confirmation; confirming dispatches
 * `profile.setActiveProfileMessage()` with `SendType.BUST_CACHE` and
 * re-issues `nextMessages()` to refresh the active marker.
 */
@Composable
fun ProfileSwitchScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val ds = LocalDataStore.current
    val idpManager by ds.idpManager.observeAsState()
    var pendingConfirm by remember { mutableStateOf<IDPManager.Profile?>(null) }

    val tick = intervalOf(60)
    LaunchedEffect(tick) {
        val manager = ds.idpManager.value ?: IDPManager()
        val messages = manager.nextMessages()
        if (messages.isNotEmpty()) {
            sendPumpCommands(SendType.STANDARD, messages)
        }
    }

    val pending: IDPManager.Profile? = pendingConfirm
    if (pending != null) {
        ProfileSwitchConfirmAlert(
            profile = pending,
            onCancel = { pendingConfirm = null },
            onConfirm = {
                sendPumpCommands(SendType.BUST_CACHE, listOf(pending.setActiveProfileMessage()))
                // Re-fetch so the active-profile marker updates without waiting
                // for the next 60s tick.
                val refreshMessages = (ds.idpManager.value ?: IDPManager()).nextMessages()
                if (refreshMessages.isNotEmpty()) {
                    sendPumpCommands(SendType.BUST_CACHE, refreshMessages)
                }
                pendingConfirm = null
            },
        )
        return
    }

    val profiles = idpManager?.profiles.orEmpty()
    val isComplete = idpManager?.isComplete == true
    val activeProfileId = idpManager?.activeProfile?.idpId

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollableColumn(focusRequester, scalingLazyListState)
            .padding(horizontal = 8.dp),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams(),
    ) {
        when {
            !isComplete -> item {
                Text(
                    text = "Loading profiles…",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            profiles.isEmpty() -> item {
                Text(
                    text = "No profiles found.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            else -> {
                item {
                    Text(
                        text = "Active profile",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                profiles.forEach { profile ->
                    item {
                        ProfileChip(
                            profile = profile,
                            isActive = profile.idpId == activeProfileId,
                            onClick = {
                                if (profile.idpId != activeProfileId) {
                                    pendingConfirm = profile
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChip(
    profile: IDPManager.Profile,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    // Active state is conveyed by chip color (primary vs secondary) + the
    // secondary-label text. Not using a conditional icon here to avoid
    // @Composable nullable-lambda gotchas — color + label is enough.
    Chip(
        onClick = onClick,
        label = {
            Text(
                text = profile.idpSettingsResponse.name,
                fontSize = 13.sp,
            )
        },
        secondaryLabel = {
            Text(
                text = if (isActive) "Active" else "Tap to activate",
                fontSize = 10.sp,
            )
        },
        colors = if (isActive) ChipDefaults.primaryChipColors()
            else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileSwitchConfirmAlert(
    profile: IDPManager.Profile,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Alert(
        title = {
            Text(
                text = "Activate \"${profile.idpSettingsResponse.name}\"?",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
            )
        },
        negativeButton = {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(imageVector = Icons.Filled.Clear, contentDescription = "Cancel")
            }
        },
        positiveButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.primaryButtonColors(),
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = "Activate profile")
            }
        },
        icon = {
            Image(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Profile",
                modifier = Modifier.size(24.dp),
            )
        },
    ) {}
}
