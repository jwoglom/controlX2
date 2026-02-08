@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBasalLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBolusLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BasalLimitSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun SafetyLimitsActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val maxBolusSettings = ds.globalMaxBolusSettingsResponse.observeAsState()
    val basalLimitSettings = ds.basalLimitSettingsResponse.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, safetyLimitsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = safetyLimitsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("SafetyLimitsActions loading: remaining ${nullFields.size}: ${safetyLimitsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("SafetyLimitsActions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("SafetyLimitsActions loading done: ${safetyLimitsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading SafetyLimitsActions with force")
        refreshing = true

        safetyLimitsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading SafetyLimitsActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading SafetyLimitsActions from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    var showMaxBolusDialog by remember { mutableStateOf(false) }
    var showMaxBasalDialog by remember { mutableStateOf(false) }
    var maxBolusText by remember { mutableStateOf("") }
    var maxBasalText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            content = {
                item {
                    ListItem(
                        headlineContent = { Text("Back") },
                        leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable { navigateBack() },
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                    )
                    HeaderLine("Safety Limits")
                    Divider()
                }

                if (refreshing) {
                    item {
                        LoadSpinner("Loading safety limits...")
                    }
                }

                // Max Bolus Limit
                item {
                    val bolus = maxBolusSettings.value
                    ListItem(
                        headlineContent = { Text("Maximum Bolus") },
                        supportingContent = {
                            if (bolus != null) {
                                Text(
                                    "Current: ${InsulinUnit.from1000To1(bolus.maxBolus.toLong())} units\n" +
                                    "Default: ${InsulinUnit.from1000To1(bolus.maxBolusDefault.toLong())} units\n" +
                                    "Range: 1 - 25 units"
                                )
                            } else {
                                Text("Loading...")
                            }
                        },
                        leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        modifier = Modifier.clickable {
                            maxBolusText = if (bolus != null) {
                                InsulinUnit.from1000To1(bolus.maxBolus.toLong()).toString()
                            } else ""
                            showMaxBolusDialog = true
                        }
                    )
                    Divider()
                }

                // Max Basal Rate Limit
                item {
                    val basal = basalLimitSettings.value
                    ListItem(
                        headlineContent = { Text("Maximum Basal Rate") },
                        supportingContent = {
                            if (basal != null) {
                                Text(
                                    "Current: ${InsulinUnit.from1000To1(basal.basalLimit)} U/hr\n" +
                                    "Default: ${InsulinUnit.from1000To1(basal.basalLimitDefault)} U/hr\n" +
                                    "Range: 1 - 15 U/hr"
                                )
                            } else {
                                Text("Loading...")
                            }
                        },
                        leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        modifier = Modifier.clickable {
                            maxBasalText = if (basal != null) {
                                InsulinUnit.from1000To1(basal.basalLimit).toString()
                            } else ""
                            showMaxBasalDialog = true
                        }
                    )
                    Divider()
                }

                item {
                    TextButton(onClick = navigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to ${LandingSection.ACTIONS.label}")
                    }
                }
            }
        )
    }

    // Max Bolus Dialog
    if (showMaxBolusDialog) {
        AlertDialog(
            onDismissRequest = { showMaxBolusDialog = false },
            title = { Text("Set Maximum Bolus") },
            text = {
                OutlinedTextField(
                    value = maxBolusText,
                    onValueChange = { maxBolusText = it },
                    label = { Text("Max bolus (units, 1-25)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val units = maxBolusText.toDoubleOrNull()
                    if (units != null) {
                        val milliunits = (units * 1000).toInt()
                        if (milliunits in SetMaxBolusLimitRequest.MIN_BOLUS_LIMIT_MILLIUNITS..SetMaxBolusLimitRequest.MAX_BOLUS_LIMIT_MILLIUNITS) {
                            sendPumpCommands(
                                SendType.STANDARD,
                                listOf(SetMaxBolusLimitRequest(milliunits))
                            )
                            showMaxBolusDialog = false
                            refreshScope.launch {
                                delay(500)
                                refresh()
                            }
                        } else {
                            Toast.makeText(context, "Must be between 1 and 25 units", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaxBolusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Max Basal Dialog
    if (showMaxBasalDialog) {
        AlertDialog(
            onDismissRequest = { showMaxBasalDialog = false },
            title = { Text("Set Maximum Basal Rate") },
            text = {
                OutlinedTextField(
                    value = maxBasalText,
                    onValueChange = { maxBasalText = it },
                    label = { Text("Max basal rate (U/hr, 1-15)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val units = maxBasalText.toDoubleOrNull()
                    if (units != null) {
                        val milliunits = (units * 1000).toInt()
                        if (milliunits in SetMaxBasalLimitRequest.MIN_BASAL_LIMIT_MILLIUNITS..SetMaxBasalLimitRequest.MAX_BASAL_LIMIT_MILLIUNITS) {
                            sendPumpCommands(
                                SendType.STANDARD,
                                listOf(SetMaxBasalLimitRequest(milliunits))
                            )
                            showMaxBasalDialog = false
                            refreshScope.launch {
                                delay(500)
                                refresh()
                            }
                        } else {
                            Toast.makeText(context, "Must be between 1 and 15 U/hr", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaxBasalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

val safetyLimitsCommands = listOf(
    GlobalMaxBolusSettingsRequest(),
    BasalLimitSettingsRequest(),
)

val safetyLimitsFields = listOf(
    dataStore.globalMaxBolusSettingsResponse,
    dataStore.basalLimitSettingsResponse,
)

@Preview(showBackground = true)
@Composable
internal fun SafetyLimitsActionsPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            SafetyLimitsActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}
