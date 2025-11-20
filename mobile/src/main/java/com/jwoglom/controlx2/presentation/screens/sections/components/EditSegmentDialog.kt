@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.MinsTime
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import timber.log.Timber

@Composable
fun EditSegmentDialog(
    profile: IDPManager.Profile,
    segment: IDPSegmentResponse,
    segmentIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (MinsTime, Float, Long, Int, Int) -> Unit
) {
    val totalMinutes = segment.profileStartTime
    val initialHour = totalMinutes / 60
    val initialMinute = totalMinutes % 60
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    var basalRate by remember { mutableStateOf(InsulinUnit.from1000To1(segment.profileBasalRate.toLong()).toString()) }
    var carbRatio by remember { mutableStateOf(segment.profileCarbRatio.toString()) }
    var targetBG by remember { mutableStateOf(segment.profileTargetBG.toString()) }
    var isf by remember { mutableStateOf(segment.profileISF.toString()) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Profile Segment")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
            ) {
                item {
                    Text("Editing segment ${segmentIndex} in ${profile.idpSettingsResponse.name} (#${profile.idpId})", fontSize = 14.sp)
                }
                item {
                    Text(
                        "Start Time",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = basalRate,
                        onValueChange = { basalRate = it },
                        label = { Text("Basal Rate (u/hr)") },
                        placeholder = { Text("e.g., 1.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = carbRatio,
                        onValueChange = { carbRatio = it },
                        label = { Text("Carb Ratio (g/u)") },
                        supportingText = { Text("Example: 10 = 1:10 ratio") },
                        placeholder = { Text("e.g., 10g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = targetBG,
                        onValueChange = { targetBG = it },
                        label = { Text("Target BG") },
                        placeholder = { Text("e.g., 110") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = isf,
                        onValueChange = { isf = it },
                        label = { Text("ISF (Insulin Sensitivity Factor)") },
                        supportingText = { Text("Example: 50 = 1u:50 mg/dL") },
                        placeholder = { Text("e.g., 50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMessage.isNotEmpty()) {
                    item {
                        Text(
                            errorMessage,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val hours = timePickerState.hour
                        val minutes = timePickerState.minute
                        val newStartTime = MinsTime(hours, minutes)
                        val basalRateFloat = basalRate.toFloatOrNull() ?: 0f
                        val carbRatioLong = carbRatio.toLongOrNull() ?: 0L
                        val targetBGInt = targetBG.toIntOrNull() ?: 0
                        val isfInt = isf.toIntOrNull() ?: 0

                        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                            errorMessage = "Invalid time. Hours must be 0-23, minutes must be 0-59."
                            return@TextButton
                        }
                        if (basalRateFloat <= 0) {
                            errorMessage = "Basal rate must be greater than 0"
                            return@TextButton
                        }
                        if (carbRatioLong <= 0) {
                            errorMessage = "Carb ratio must be greater than 0"
                            return@TextButton
                        }
                        if (targetBGInt <= 0) {
                            errorMessage = "Target BG must be greater than 0"
                            return@TextButton
                        }
                        if (isfInt <= 0) {
                            errorMessage = "ISF must be greater than 0"
                            return@TextButton
                        }

                        onConfirm(newStartTime, basalRateFloat, carbRatioLong, targetBGInt, isfInt)
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        Timber.e(e, "Error updating segment")
                    }
                }
            ) {
                Text("Update Segment")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, name = "Edit Segment Dialog")
@Composable
internal fun EditSegmentDialogPreview() {
    ControlX2Theme() {
        // Create a mock profile and segment for preview
        val mockResponse = com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse(
            1, // idpId
            "Morning Profile", // name
            1, // carbEntryEnabled
            240, // insulinDuration
            0, // maxBolusAmount
            false // bolusCalculatorEnabled
        )
        val mockProfile = IDPManager.Profile(mockResponse, false)

        val mockSegment = IDPSegmentResponse(
            0, // profileId
            0, // segmentId
            480, // profileStartTime - 8:00 AM in minutes
            0, // profileEndTime
            1000L, // profileBasalRate - 1.0 u/hr in milliunits (Long)
            10, // profileCarbRatio
            50, // profileISF
            110 // profileTargetBG
        )

        EditSegmentDialog(
            profile = mockProfile,
            segment = mockSegment,
            segmentIndex = 0,
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> }
        )
    }
}
