package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import timber.log.Timber

@Composable
fun BolusConditionPromptRegion(
    recalculate: () -> Unit,
) {
    val dataStore = LocalDataStore.current
    val bolusConditionsPrompt = dataStore.bolusConditionsPrompt.observeAsState()
    val bolusConditionsExcluded = dataStore.bolusConditionsExcluded.observeAsState()
    val bolusConditionsPromptAcknowledged = dataStore.bolusConditionsPromptAcknowledged.observeAsState()

    bolusConditionsPrompt.value?.forEach {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${it.msg}: ")
                    }
                    append(it.prompt?.promptMessage)
                }, Modifier.padding(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(3f)) {}

                Column(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            bolusConditionsPrompt.value?.let {
                                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                                    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf(it.first())
                                } else {
                                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(it.first())
                                }

                                if (dataStore.bolusConditionsExcluded.value == null) {
                                    dataStore.bolusConditionsExcluded.value = mutableSetOf(it.first())
                                } else {
                                    dataStore.bolusConditionsExcluded.value?.add(it.first())
                                }

                                dataStore.bolusConditionsPrompt.value?.drop(0)
                                recalculate()
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Reject")
                    }
                }

                Column(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            bolusConditionsPrompt.value?.let {
                                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                                    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf(it.first())
                                } else {
                                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(it.first())
                                }

                                if (dataStore.bolusConditionsExcluded.value != null) {
                                    dataStore.bolusConditionsExcluded.value?.remove(it.first())
                                }

                                dataStore.bolusConditionsPrompt.value?.drop(0)
                                recalculate()
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = "Apply")
                    }
                }
            }
        }
    }

    if (bolusConditionsPromptAcknowledged.value != null && bolusConditionsPromptAcknowledged.value!!.isNotEmpty()) {
        bolusConditionsPromptAcknowledged.value?.forEach {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        Timber.i("bolusConditionsPromptAcknowledged click")
                        dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>().let {
                            it.addAll(bolusConditionsPromptAcknowledged.value!!)
                            it
                        }
                        dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf()
                        dataStore.bolusConditionsExcluded.value = mutableSetOf()
                    }
            ) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(
                            when {
                                bolusConditionsExcluded.value?.contains(it) == true -> it.prompt?.whenIgnoredNotice ?: it.msg
                                else -> it.prompt?.whenAcceptedNotice ?: it.msg
                            }
                        )
                    }
                }, Modifier.padding(8.dp))
            }
        }
    }
}
