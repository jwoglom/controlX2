package com.jwoglom.controlx2.presentation.ui.components.tempbasal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog

@Composable
fun TempBasalConfirmPhase(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    percent: Int,
    durationMinutes: Int,
) {
    fun prettyDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h${m}m"
    }

    Dialog(
        showDialog = showDialog,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = {
                Text(
                    text = "Set ${percent}% temp rate for ${prettyDuration(durationMinutes)}?",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            },
            positiveButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.primaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set")
                }
            },
        ) {
            Text(
                text = "This will set a temporary basal rate of ${percent}% for ${prettyDuration(durationMinutes)}.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
