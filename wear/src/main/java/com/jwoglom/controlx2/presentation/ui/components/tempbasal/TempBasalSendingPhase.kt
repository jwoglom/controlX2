package com.jwoglom.controlx2.presentation.ui.components.tempbasal

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog

@Composable
fun TempBasalSendingPhase(
    showDialog: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(
        showDialog = showDialog,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = {
                Text(
                    text = "Setting temp rate...",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {},
            positiveButton = {},
        ) {
            Text(
                text = "Sending request to pump...",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
