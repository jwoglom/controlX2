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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int, Int, Int, Boolean) -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    var carbRatio by remember { mutableStateOf("10") }
    var basalRate by remember { mutableStateOf("1.0") }
    var targetBG by remember { mutableStateOf("110") }
    var isf by remember { mutableStateOf("50") }
    var insulinDuration by remember { mutableStateOf("240") }
    var carbEntryEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add New Profile")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "Default Settings for First Segment",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = carbRatio,
                        onValueChange = { carbRatio = it },
                        label = { Text("Carb Ratio (g/u)") },
                        supportingText = { Text("Example: 10 = 1:10 ratio") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = basalRate,
                        onValueChange = { basalRate = it },
                        label = { Text("Basal Rate (u/hr)") },
                        supportingText = { Text("Example: 1.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = targetBG,
                        onValueChange = { targetBG = it },
                        label = { Text("Target BG (mg/dL)") },
                        supportingText = { Text("Example: 110") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = isf,
                        onValueChange = { isf = it },
                        label = { Text("ISF (mg/dL per u)") },
                        supportingText = { Text("Example: 50 = 1u:50 mg/dL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = insulinDuration,
                        onValueChange = { insulinDuration = it },
                        label = { Text("Insulin Duration (minutes)") },
                        supportingText = { Text("Example: 240 = 4 hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val carbRatioInt = (carbRatio.toFloatOrNull() ?: 10f) * 1000
                    val basalRateMilliunits = ((basalRate.toFloatOrNull() ?: 1.0f) * 1000).toInt()
                    val targetBGInt = targetBG.toIntOrNull() ?: 110
                    val isfInt = isf.toIntOrNull() ?: 50
                    val insulinDurationInt = insulinDuration.toIntOrNull() ?: 240

                    onConfirm(
                        profileName,
                        carbRatioInt.toInt(),
                        basalRateMilliunits,
                        targetBGInt,
                        isfInt,
                        insulinDurationInt,
                        carbEntryEnabled
                    )
                },
                enabled = profileName.isNotBlank()
            ) {
                Text("Create")
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

@Preview(showBackground = true, name = "Add Profile Dialog")
@Composable
internal fun AddProfileDialogPreview() {
    ControlX2Theme() {
        AddProfileDialog(
            onDismiss = {},
            onConfirm = { _, _, _, _, _, _, _ -> }
        )
    }
}
