package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.jwoglom.controlx2.presentation.DataStore

@Composable
fun BolusConditionsPromptPhase(
    showBolusConditionPrompt: Boolean,
    onDismiss: () -> Unit,
    dataStore: DataStore,
    recalculate: () -> Unit,
    onPromptDone: () -> Unit,
) {
    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showBolusConditionPrompt, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusConditionsPrompt = dataStore.bolusConditionsPrompt.observeAsState()

        fun handlePromptAction(excludePrompt: Boolean) {
            bolusConditionsPrompt.value?.let {
                val prompt = it.first()
                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf(prompt)
                } else {
                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(prompt)
                }

                if (excludePrompt) {
                    if (dataStore.bolusConditionsExcluded.value == null) {
                        dataStore.bolusConditionsExcluded.value = mutableSetOf(prompt)
                    } else {
                        dataStore.bolusConditionsExcluded.value?.add(prompt)
                    }
                } else {
                    dataStore.bolusConditionsExcluded.value?.remove(prompt)
                }

                if (it.size == 1) {
                    onPromptDone()
                }
                dataStore.bolusConditionsPrompt.value?.drop(0)
                recalculate()
            }
        }

        Alert(
            title = {
                Text(
                    text = bolusConditionsPrompt.value?.firstOrNull()?.msg ?: "",
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(onClick = { handlePromptAction(true) }, colors = ButtonDefaults.secondaryButtonColors()) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Do not apply")
                }
            },
            positiveButton = {
                Button(onClick = { handlePromptAction(false) }, colors = ButtonDefaults.primaryButtonColors()) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Apply")
                }
            },
            scrollState = scrollState,
        ) {
            Text(
                text = bolusConditionsPrompt.value?.firstOrNull()?.prompt?.promptMessage ?: "",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
