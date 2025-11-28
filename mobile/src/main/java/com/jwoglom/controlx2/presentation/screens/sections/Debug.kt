@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.google.android.material.slider.Slider
import com.google.common.base.Splitter
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.annotations.HistoryLogProps
import com.jwoglom.pumpx2.pump.messages.annotations.MessageProps
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeControlIQSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeTimeDateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DeleteIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RenameIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetActiveIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetG6TransmitterIdRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBasalLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBolusLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetModesRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpAlertSnoozeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpSoundsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetQuickBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetSleepScheduleRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StartDexcomG6SensorSessionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetDexcomG7PairingCodeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusPermissionChangeReasonRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.util.MessageHelpers
import com.jwoglom.pumpx2.shared.JavaHelpers
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.pumpx2.pump.PumpState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import java.util.stream.Collectors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


@Composable
fun Debug(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager: ClipboardManager = LocalClipboardManager.current


    var showSendPumpMessageMenu by remember { mutableStateOf(false) }
    var showMessageCache by remember { mutableStateOf(false) }
    var showHistoryLogs by remember { mutableStateOf(false) }
    var showPumpState by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val ds = LocalDataStore.current

    fun setClipboard(str: String) {
        clipboardManager.setText(AnnotatedString(str))
        Toast.makeText(context, "Saved to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareTextContents(str: String, label: String, mimeType: String) {
        ShareCompat.IntentBuilder(context)
            .setType(mimeType)
            .setText(str)
            .setChooserTitle(label)
            .startChooser();
    }

    fun shareDebugLog(context: Context) {
        val filePath = File(context.filesDir, "debugLog-MUA.txt")
        val uri = FileProvider.getUriForFile(context, context.packageName, filePath)
        context.startActivity(ShareCompat.IntentBuilder(context)
            .setType("text/plain")
            .setStream(uri)
            .intent
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri, "text/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun clearDebugLog(context: Context) {
        AlertDialog.Builder(context)
            .setMessage("Are you sure you want to clear the saved debug logs?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                val filePath = File(context.filesDir, "debugLog-MUA.txt")
                filePath.delete()

            }
            .show()
    }

    fun emptyDatabase(context: Context) {
        AlertDialog.Builder(context)
            .setMessage("Are you sure you want to empty the database?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                coroutineScope.launch {
                    val historyLogDb = HistoryLogDatabase.getDatabase(context)
                    val historyLogDao = historyLogDb.historyLogDao()
                    historyLogDao.deleteAll()
                }
            }
            .show()
    }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Debug")
                Divider()
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    ListItem(
                        headlineContent = { Text("Send Pump Message") },
                        supportingContent = { Text("Displays the response message for the given request.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Build,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            showSendPumpMessageMenu = true
                        }
                    )
                    DropdownMenu(
                        expanded = showSendPumpMessageMenu,
                        onDismissRequest = { showSendPumpMessageMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val requestMessages = MessageHelpers.getAllPumpRequestMessages()
                            .stream().filter { m: String ->
                                !m.startsWith("authentication.") && !m.startsWith(
                                    "historyLog."
                                )
                            }.collect(Collectors.toList())

                        requestMessages.forEach { message ->
                            DropdownMenuItem(
                                text = { Text("$message") },
                                onClick = {
                                    try {
                                        val className =
                                            MessageHelpers.REQUEST_PACKAGE + "." + message

                                        // Custom processing for arguments
                                        if (className == IDPSegmentRequest::class.java.name) {
                                            triggerIDPSegmentDialog(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == IDPSettingsRequest::class.java.name) {
                                            triggerIDPSettingsDialog(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == HistoryLogRequest::class.java.name) {
                                            triggerHistoryLogRequestDialog(
                                                context,
                                                sendPumpCommands
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == InitiateBolusRequest::class.java.name) {
                                            triggerInitiateBolusRequestDialog(
                                                context,
                                                sendPumpCommands
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == CancelBolusRequest::class.java.name) {
                                            triggerCancelBolusRequestDialog(
                                                context,
                                                sendPumpCommands
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == BolusPermissionChangeReasonRequest::class.java.name) {
                                            triggerMessageWithBolusIdParameter(
                                                context,
                                                sendPumpCommands,
                                                BolusPermissionChangeReasonRequest::class.java
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == BolusPermissionReleaseRequest::class.java.name) {
                                            triggerMessageWithBolusIdParameter(
                                                context,
                                                sendPumpCommands,
                                                BolusPermissionReleaseRequest::class.java
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == RemoteBgEntryRequest::class.java.name) {
                                            triggerRemoteBgEntryRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == RemoteCarbEntryRequest::class.java.name) {
                                            triggerRemoteCarbEntryRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetModesRequest::class.java.name) {
                                            triggerSetModesRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetSleepScheduleRequest::class.java.name) {
                                            triggerSetSleepScheduleRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetTempRateRequest::class.java.name) {
                                            triggerSetTempRateRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == ChangeControlIQSettingsRequest::class.java.name) {
                                            triggerChangeControlIQSettingsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetQuickBolusSettingsRequest::class.java.name) {
                                            triggerSetQuickBolusSettingsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == DismissNotificationRequest::class.java.name) {
                                            triggerDismissNotificationRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetG6TransmitterIdRequest::class.java.name) {
                                            triggerSetG6TransmitterIdRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == StartDexcomG6SensorSessionRequest::class.java.name) {
                                            triggerStartDexcomG6SensorSessionRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetDexcomG7PairingCodeRequest::class.java.name) {
                                            triggerSetDexcomG7PairingCodeRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == FillCannulaRequest::class.java.name) {
                                            triggerFillCannulaRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == ChangeTimeDateRequest::class.java.name) {
                                            triggerChangeTimeDateRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetActiveIDPRequest::class.java.name) {
                                            triggerSetActiveIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == CreateIDPRequest::class.java.name) {
                                            triggerCreateIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == RenameIDPRequest::class.java.name) {
                                            triggerRenameIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == DeleteIDPRequest::class.java.name) {
                                            triggerDeleteIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetIDPSettingsRequest::class.java.name) {
                                            triggerSetIDPSettingsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetIDPSegmentRequest::class.java.name) {
                                            triggerSetIDPSegmentRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetMaxBolusLimitRequest::class.java.name) {
                                            triggerSetMaxBolusLimitRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetMaxBasalLimitRequest::class.java.name) {
                                            triggerSetMaxBasalLimitRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetPumpSoundsRequest::class.java.name) {
                                            triggerSetPumpSoundsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetPumpAlertSnoozeRequest::class.java.name) {
                                            triggerSetPumpAlertSnoozeRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        }
                                        val clazz = Class.forName(className)
                                        Timber.i("Instantiated %s: %s", className, clazz)
                                        sendPumpCommands(
                                            SendType.DEBUG_PROMPT,
                                            listOf(
                                                clazz.newInstance() as Message
                                            )
                                        )
                                    } catch (e: ClassNotFoundException) {
                                        Timber.e(e)
                                        e.printStackTrace()
                                    } catch (e: IllegalAccessException) {
                                        Timber.e(e)
                                        e.printStackTrace()
                                    } catch (e: InstantiationException) {
                                        Timber.e(e)
                                        e.printStackTrace()
                                    }
                                    showSendPumpMessageMenu = false
                                },
                                leadingIcon = {}
                            )
                            Divider()
                        }
                    }

                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("View Received Message Cache") },
                    supportingContent = { Text("Displays recently received pump messages.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        showMessageCache = true
                    }
                )

                if (showMessageCache) {
                    Popup(
                        onDismissRequest = { showMessageCache = false }
                    ) {
                        LaunchedEffect(Unit) {
                            sendMessage("/to-pump/debug-message-cache", "".toByteArray())
                        }

                        val debugMessageCache = ds.debugMessageCache.observeAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            LazyColumn {
                                item {
                                    Spacer(Modifier.height(64.dp))
                                }
                                item {
                                    LazyRow(Modifier.fillMaxWidth()) {
                                        item {
                                            IconButton(
                                                onClick = {
                                                    showMessageCache = false
                                                }
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close")
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        item {
                                            FilledTonalButton(
                                                onClick = {
                                                    debugMessageCache.value?.let {
                                                        shareTextContents(messageCacheToJson(it), "ControlX2 JSON debug data","text/json")
                                                    }
                                                }
                                            ) {
                                                Text("Export")
                                            }
                                        }
                                    }
                                }
                                if (debugMessageCache.value?.isEmpty() == true) {
                                    item {
                                        Text("No message entries present in cache.")
                                    }
                                }
                                debugMessageCache.value?.sortedBy {
                                    it.second.epochSecond * -1
                                }?.forEach { message ->
                                    item {
                                        ListItem(
                                            headlineContent = {
                                                // message name
                                                Text(shortPumpMessageTitle(message.first))
                                            },
                                            supportingContent = {
                                                // message detail
                                                Text(shortPumpMessageDetail(message.first))
                                            },
                                            overlineContent = {
                                                // time
                                                Text("${message.second} (${shortTimeAgo(message.second, nowThresholdSeconds = 1)})")
                                            },
                                            modifier = Modifier.clickable {
                                                shareTextContents(messagePairToJson(message), "ControlX2 ${shortPumpMessageTitle(message.first)} debug data","text/json")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Get History Logs") },
                    supportingContent = { Text("Fetches history logs within the given range.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        Timber.d("pressed Get History Logs")
                        coroutineScope.launch {
                            dataStore.historyLogStatus.value = null
                            sendPumpCommands(SendType.STANDARD, listOf(HistoryLogStatusRequest()))

                            for (i in 1..50) {
                                Timber.d("waiting for historyLogStatus: ${dataStore.historyLogStatus}")
                                if (dataStore.historyLogStatus.value != null) {
                                    break
                                }
                                withContext(Dispatchers.IO) {
                                    Thread.sleep(100)
                                }
                            }

                            triggerHistoryLogRequestDialog(
                                context,
                                sendPumpCommands,
                                dataStore.historyLogStatus.value
                            )
                        }
                    }
                )
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("View History Log Messages") },
                    supportingContent = { Text("Displays pump history log messages") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        sendMessage("/to-pump/debug-historylog-cache", "".toByteArray())
                        Handler(Looper.getMainLooper()).postDelayed({
                            showHistoryLogs = true
                        }, 500)
                    }
                )

                if (showHistoryLogs) {
                    Popup(
                        onDismissRequest = { showHistoryLogs = false }
                    ) {
                        val historyLogCache = ds.historyLogCache.observeAsState()
                        var filterToType by remember { mutableStateOf<String?>(null) }
                        var filterFields by remember { mutableStateOf(historyLogCache.value?.entries?.map { shortHistoryLogPumpMessageTitle(it.value) }?.toSet()?.sorted()) }
                        LaunchedEffect (historyLogCache.value) {
                            filterFields = historyLogCache.value?.entries?.map { shortHistoryLogPumpMessageTitle(it.value) }?.toSet()?.sorted()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                                .background(Color.DarkGray.copy(alpha = 0.3f))
                        ) {
                            LazyColumn {
                                item {
                                    Spacer(Modifier.height(64.dp))
                                }
                                item {
                                    LazyRow(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                        item {
                                            IconButton(
                                                onClick = {
                                                    showHistoryLogs = false
                                                }
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close")
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        item {
                                            FilledTonalButton(
                                                onClick = {
                                                    historyLogCache.value?.let {
                                                        shareTextContents(historyLogCacheToJson(it), "ControlX2 History Log JSON data","text/json")
                                                    }
                                                }
                                            ) {
                                                Text("Export")
                                            }
                                        }
                                    }
                                }

                                item {
                                    var expanded by remember { mutableStateOf(false) }
                                    val icon = if (expanded)
                                        Icons.Filled.KeyboardArrowUp
                                    else
                                        Icons.Filled.KeyboardArrowDown

                                    var textFieldSize by remember { mutableStateOf(Size.Zero)}

                                    Box (Modifier
                                        .background(Color.White)
                                        .padding(start = 16.dp, end = 16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = when (filterToType) {
                                                null -> "<All>"
                                                else -> "$filterToType"
                                            },
                                            onValueChange = { filterToType = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coordinates ->
                                                    // This value is used to assign to
                                                    // the DropDown the same width
                                                    textFieldSize = coordinates.size.toSize()
                                                },
                                            label = { Text("Filter") },
                                            trailingIcon = {
                                                Icon(icon, "contentDescription",
                                                    Modifier.clickable { expanded = !expanded })
                                            },
                                            textStyle = TextStyle.Default.copy(fontSize = 16.sp),
                                        )

                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier
                                                .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
                                        ) {
                                            filterFields?.forEach { label ->
                                                DropdownMenuItem(onClick = {
                                                    filterToType = label
                                                    expanded = false
                                                }, text = {
                                                    Text(text = label)
                                                })
                                            }
                                        }
                                    }
                                }

                                if (historyLogCache.value?.entries?.isEmpty() == true) {
                                    item {
                                        Text(
                                            "No history log entries present in cache.",
                                            modifier = Modifier
                                                .background(Color.White)
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        )
                                    }
                                } else {
                                    item {
                                        Text(
                                            "${historyLogCache.value?.size ?: 0} history log entries",
                                            modifier = Modifier
                                                .background(Color.White)
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .clickable {
                                                    sendMessage("/to-pump/debug-historylog-cache", "".toByteArray())
                                                }
                                        )
                                    }
                                }
                                historyLogCache.value?.entries?.sortedBy {
                                    it.key * -1
                                }?.filter {
                                    if (filterToType == null) {
                                        true
                                    } else {
                                        shortHistoryLogPumpMessageTitle(it.value) == filterToType
                                    }
                                }?.forEach { log ->
                                    item {
                                        ListItem(
                                            headlineContent = {
//                                                // message name
                                                Text(shortHistoryLogPumpMessageTitle(log.value))
                                            },
                                            supportingContent = {
                                                // message detail
                                                Text("${log.value}")
                                            },
                                            overlineContent = {
                                                // time
                                                Text("#${log.key} ${log.value.pumpTimeSecInstant}")
                                            },
                                            modifier = Modifier.clickable {
                                                shareTextContents(historyLogToJson(log), "ControlX2 History Log Event","text/json")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (Prefs(context).connectionSharingEnabled()) {
                item {
                    Divider()
                }

                item {
                    if (Prefs(context).onlySnoopBluetoothEnabled()) {
                        ListItem(
                            headlineContent = { Text("Disable Only Snoop Bluetooth") },
                            supportingContent = { Text("Re-enables app functionality.") },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Stop icon",
                                )
                            },
                            modifier = Modifier.clickable {
                                Prefs(context).setOnlySnoopBluetoothEnabled(false)
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        Thread.sleep(250)
                                    }
                                    sendMessage("/to-phone/force-reload", "".toByteArray())
                                }
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text("Enable Only Snoop Bluetooth") },
                            supportingContent = { Text("All app functionality will be disabled, for debugging purposes only.") },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Start icon",
                                )
                            },
                            modifier = Modifier.clickable {
                                Prefs(context).setOnlySnoopBluetoothEnabled(true)
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        Thread.sleep(250)
                                    }
                                    sendMessage("/to-phone/force-reload", "".toByteArray())
                                }
                            }
                        )
                    }
                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Download ControlX2 Debug Logs") },
                    supportingContent = { Text("Exports a text file with filtered logcat output.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        shareDebugLog(context)
                    }
                )
            }


            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("View PumpState") },
                    supportingContent = { Text("Displays the pump MAC, pairing key, and authentication secrets.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        showPumpState = true
                    }
                )

                if (showPumpState) {
                    val exportedPumpState = PumpState.exportState(context)
                    Popup(
                        onDismissRequest = { showPumpState = false }
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                                .background(Color.DarkGray.copy(alpha = 0.7f))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                item {
                                    Spacer(Modifier.height(64.dp))
                                }
                                item {
                                    TextField(
                                        value = exportedPumpState,
                                        label = { Text("PumpState") },
                                        onValueChange = {v -> },
                                        modifier = Modifier.fillMaxWidth().height(200.dp).padding(10.dp).background(Color.White)
                                    )
                                }

                                item {
                                    LazyRow {
                                        item {
                                            Button(onClick = {
                                                setClipboard(exportedPumpState)
                                            }) {
                                                Text("Save to clipboard")
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        item {
                                            Button(onClick = {
                                                showPumpState = false
                                            }) {
                                                Text("Close")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Clear Debug Logs") },
                    supportingContent = { Text("Clears the saved debug logs.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        clearDebugLog(context)
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Empty database") },
                    supportingContent = { Text("Removes all saved history logs in sqlite.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        emptyDatabase(context)
                    }
                )
            }
        }
    )
}


fun triggerIDPSegmentDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter IDP ID")
    builder.setMessage("Enter the ID for the Insulin Delivery Profile")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK") { dialog, which ->
        val idpId = input1.text.toString()
        Timber.i("idp id: %s", idpId)
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("Enter segment index")
        builder2.setMessage("Enter the index for the Insulin Delivery Profile segment")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder2.setView(input2)
        builder2.setPositiveButton(
            "OK"
        ) { dialog, which ->
            val idpSegment = input2.text.toString()
            Timber.i("idp segment: %s", idpSegment)
            sendPumpCommands(
                SendType.DEBUG_PROMPT,
                listOf(
                    IDPSegmentRequest(idpId.toInt(), idpSegment.toInt())
                )
            )
        }
        builder2.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder2.show()
    }
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerIDPSettingsDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter IDP ID")
    builder.setMessage("Enter the ID for the Insulin Delivery Profile")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK") { dialog, which ->
        val idpId = input1.text.toString()
        Timber.i("idp id: %s", idpId)
        sendPumpCommands(
            SendType.DEBUG_PROMPT,
            listOf(
                IDPSettingsRequest(idpId.toInt())
            )
        )
    }
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerHistoryLogRequestDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogStatus: HistoryLogStatusResponse? = null,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter start log ID")
    builder.setMessage("Enter the ID of the first history log item to return:\n\n${historyLogStatus ?: ""}")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
    input1.width = 200
    if (historyLogStatus != null) {
        input1.setText("${max(0, historyLogStatus.lastSequenceNum-255)}")
    }
    val layout = LinearLayout(context)
    layout.addView(input1)

    if (historyLogStatus != null) {
        val slider = Slider(context)
        slider.valueFrom = historyLogStatus.firstSequenceNum.toFloat()
        slider.valueTo = historyLogStatus.lastSequenceNum.toFloat()
        slider.value = max(0, historyLogStatus.lastSequenceNum-255).toFloat()
        slider.stepSize = 1.0f
        slider.addOnChangeListener { slider, value, fromUser -> input1.setText("${value.toLong()}") }
        layout.addView(slider)
    }

    builder.setView(layout)
    builder.setPositiveButton("OK") { dialog, which ->
        val startLog = input1.text.toString()
        Timber.i("startLog id: %s", startLog)
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("Enter number of logs")
        builder2.setMessage("Enter the max number of logs to return")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
        if (historyLogStatus != null) {
            input2.setText("${min(startLog.toLong()+255, historyLogStatus.lastSequenceNum)-startLog.toLong()}")
        }
        builder2.setView(input2)

        if (historyLogStatus != null) {
            builder2.setNeutralButton("Get All") { dialog, which ->
                triggerHistoryLogRangePrompt(context, sendPumpCommands, startLog.toLong(), historyLogStatus.lastSequenceNum)
            }
        }
        builder2.setPositiveButton(
            "OK"
        ) { dialog, which ->
            val maxLogs = input2.text.toString()
            Timber.i("max logs: %s", maxLogs)
            triggerHistoryLogRangePrompt(context, sendPumpCommands, startLog.toLong(), startLog.toLong()+maxLogs.toLong())

        }
        builder2.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder2.show()
    }
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerHistoryLogRangePrompt(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    startLog: Long,
    endLog: Long
) {
    if (endLog - startLog in 0..255) {
        sendPumpCommands(
            SendType.STANDARD,
            listOf(
                HistoryLogRequest(startLog, (endLog - startLog).toInt())
            )
        )
        return
    }

    val chunkCount = ceil(((endLog - startLog) / 255).toDouble())
    AlertDialog.Builder(context)
        .setTitle("History Log Request")
        .setMessage("Sequence number range $startLog - $endLog will require $chunkCount chunks. Continue?")
        .setPositiveButton("OK") { dialog, which ->
            Handler(Looper.getMainLooper()).post {
                triggerHistoryLogRange(context, sendPumpCommands, startLog, endLog)
            }
        }
        .setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
        .show()
}

fun triggerHistoryLogRange(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    startLog: Long,
    endLog: Long
)  {
    var num = 0
    var totalNums = 1
    for (i in startLog..endLog step 256) {
        val localNum = num
        val count = if (i+255 > endLog) (endLog - i).toInt() else 255
        Handler(Looper.getMainLooper()).postDelayed({
            Timber.i("HistoryLogRequest ${localNum+1} from $i to ${i+count} count=$count")
            sendPumpCommands(
                SendType.STANDARD,
                listOf(
                    HistoryLogRequest(i, count)
                )
            )
            Toast.makeText(context, "${localNum+1}/${totalNums}: Requesting $i to ${i+count}", Toast.LENGTH_SHORT).show()
        }, localNum.toLong() * 7000)
        num++
        totalNums++
    }
}

fun triggerInitiateBolusRequestDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter units to deliver bolus")
    builder.setMessage("Enter the number of units in INTEGER FORM: 1000 = 1 unit, 100 = 0.1 unit, 10 = 0.01 unit. Minimum value is 50 (0.05 unit)")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
        val numUnitsStr = input1.text.toString()
        Timber.i("numUnits: %s", numUnitsStr)
        if ("" == numUnitsStr) {
            Timber.e("Not delivering bolus because no units entered.")
            return@OnClickListener
        }
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("CONFIRM BOLUS!!")
        builder2.setMessage("Enter the bolus ID from BolusPermissionRequest. THIS WILL ACTUALLY DELIVER THE BOLUS. Enter a blank value to cancel.")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder2.setView(input2)
        builder2.setPositiveButton("OK",
            DialogInterface.OnClickListener { dialog, which ->
                val bolusIdStr = input2.text.toString()
                Timber.i("currentIob: %s", bolusIdStr)
                if ("" == bolusIdStr) {
                    Timber.e("Not delivering bolus because no bolus ID entered.")
                    return@OnClickListener
                }
                val numUnits = numUnitsStr.toInt()
                val bolusId = bolusIdStr.toInt()
                // tandemEventCallback.lastBolusId = bolusId
                // InitiateBolusRequest(long totalVolume, int bolusTypeBitmask, long foodVolume, long correctionVolume, int bolusCarbs, int bolusBG, long bolusIOB)
                sendPumpCommands(
                    SendType.DEBUG_PROMPT,
                    listOf(
                        InitiateBolusRequest(
                            numUnits.toLong(),
                            bolusId,
                            BolusDeliveryHistoryLog.BolusType.toBitmask(BolusDeliveryHistoryLog.BolusType.FOOD2),
                            0L,
                            0L,
                            0,
                            0,
                            0
                        )
                    )
                )
            })
        builder2.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder2.show()
    })
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerCancelBolusRequestDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("CancelBolusRequest")
    builder.setMessage("Enter the bolus ID (this can be received from currentStatus.LastBolusStatusV2)")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
//    if (tandemEventCallback.lastBolusId > 0) {
//        input1.setText(java.lang.String.valueOf(tandemEventCallback.lastBolusId))
//    }
    builder.setView(input1)
    builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
        val bolusIdStr = input1.text.toString()
        Timber.i("bolusId: %s", bolusIdStr)
        if ("" == bolusIdStr) {
            Timber.e("Not cancelling bolus because no units entered.")
            return@OnClickListener
        }
        val bolusId = bolusIdStr.toInt()
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(CancelBolusRequest(bolusId)))
    })
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerMessageWithBolusIdParameter(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    messageClass: Class<out Message>
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle(messageClass.simpleName)
    builder.setMessage("Enter the bolus ID (this can be received from the in-progress bolus)")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
//    if (tandemEventCallback.lastBolusId > 0) {
//        input1.setText(java.lang.String.valueOf(tandemEventCallback.lastBolusId))
//    }
    builder.setView(input1)
    builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
        val bolusIdStr = input1.text.toString()
        Timber.i("bolusId: %s", bolusIdStr)
        if ("" == bolusIdStr) {
            Timber.e("Not sending message because no bolus ID entered.")
            return@OnClickListener
        }
        val bolusId = bolusIdStr.toInt()
        val constructorType = arrayOf<Class<*>?>(
            Long::class.javaPrimitiveType
        )
        val message: Message
        message = try {
            messageClass.getConstructor(*constructorType).newInstance(bolusId)
        } catch (e: IllegalAccessException) {
            Timber.e(e)
            return@OnClickListener
        } catch (e: InstantiationException) {
            Timber.e(e)
            return@OnClickListener
        } catch (e: InvocationTargetException) {
            Timber.e(e)
            return@OnClickListener
        } catch (e: NoSuchMethodException) {
            Timber.e(e)
            return@OnClickListener
        }
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
    })
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}


fun parseBoolInput(text: String): Boolean = text == "1" || text.equals("true", ignoreCase = true)

fun triggerRemoteBgEntryRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("RemoteBgEntryRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val bgInput = EditText(context)
    bgInput.hint = "BG (mg/dL)"
    bgInput.inputType = InputType.TYPE_CLASS_NUMBER
    val calibrationInput = EditText(context)
    calibrationInput.hint = "Use for CGM calibration? (true/false/1/0)"
    val autopopInput = EditText(context)
    autopopInput.hint = "Is autopop BG? (true/false/1/0)"
    val pumpTimeInput = EditText(context)
    pumpTimeInput.hint = "Pump time seconds since boot"
    pumpTimeInput.inputType = InputType.TYPE_CLASS_NUMBER
    val bolusIdInput = EditText(context)
    bolusIdInput.hint = "Bolus ID"
    bolusIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(bgInput, calibrationInput, autopopInput, pumpTimeInput, bolusIdInput).forEach {
        layout.addView(it)
    }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = RemoteBgEntryRequest(
            bgInput.text.toString().toInt(),
            parseBoolInput(calibrationInput.text.toString()),
            parseBoolInput(autopopInput.text.toString()),
            pumpTimeInput.text.toString().toLong(),
            bolusIdInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerRemoteCarbEntryRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("RemoteCarbEntryRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val carbsInput = EditText(context)
    carbsInput.hint = "Carbs (g)"
    carbsInput.inputType = InputType.TYPE_CLASS_NUMBER
    val pumpTimeInput = EditText(context)
    pumpTimeInput.hint = "Pump time seconds since boot"
    pumpTimeInput.inputType = InputType.TYPE_CLASS_NUMBER
    val bolusIdInput = EditText(context)
    bolusIdInput.hint = "Bolus ID"
    bolusIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(carbsInput, pumpTimeInput, bolusIdInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = RemoteCarbEntryRequest(
            carbsInput.text.toString().toInt(),
            pumpTimeInput.text.toString().toLong(),
            bolusIdInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetModesRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetModesRequest")
    val input = EditText(context)
    input.hint = "Mode bitmap (1-4)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        val bitmap = input.text.toString().toInt()
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetModesRequest(bitmap)))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetSleepScheduleRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetSleepScheduleRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val slotInput = EditText(context)
    slotInput.hint = "Slot"
    slotInput.inputType = InputType.TYPE_CLASS_NUMBER
    val scheduleInput = EditText(context)
    scheduleInput.hint = "Schedule bytes (comma-separated)"
    val flagInput = EditText(context)
    flagInput.hint = "Flag"
    flagInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(slotInput, scheduleInput, flagInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val scheduleBytes = scheduleInput.text.toString()
            .split(',')
            .filter { it.isNotBlank() }
            .map { it.trim().toInt().toByte() }
            .toByteArray()
        val message = SetSleepScheduleRequest(
            slotInput.text.toString().toInt(),
            scheduleBytes,
            flagInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetTempRateRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetTempRateRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val minutesInput = EditText(context)
    minutesInput.hint = "Minutes (>=15)"
    minutesInput.inputType = InputType.TYPE_CLASS_NUMBER
    val percentInput = EditText(context)
    percentInput.hint = "Percent (0-250)"
    percentInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(minutesInput, percentInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetTempRateRequest(
            minutesInput.text.toString().toInt(),
            percentInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerChangeControlIQSettingsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("ChangeControlIQSettingsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val enabledInput = EditText(context)
    enabledInput.hint = "Enabled (true/false/1/0)"
    val weightInput = EditText(context)
    weightInput.hint = "Weight lbs"
    weightInput.inputType = InputType.TYPE_CLASS_NUMBER
    val tdiInput = EditText(context)
    tdiInput.hint = "Total daily insulin units"
    tdiInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(enabledInput, weightInput, tdiInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = ChangeControlIQSettingsRequest(
            parseBoolInput(enabledInput.text.toString()),
            weightInput.text.toString().toInt(),
            tdiInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetQuickBolusSettingsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetQuickBolusSettingsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val enabledInput = EditText(context)
    enabledInput.hint = "Enabled (true/false/1/0)"
    val modeInput = EditText(context)
    modeInput.hint = "Mode (UNITS or CARBS)"
    val incrementInput = EditText(context)
    incrementInput.hint = "Increment enum (e.g., UNITS_0_5)"
    listOf(enabledInput, modeInput, incrementInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val enabled = parseBoolInput(enabledInput.text.toString())
        val mode = SetQuickBolusSettingsRequest.QuickBolusMode.valueOf(modeInput.text.toString().uppercase())
        val increment = SetQuickBolusSettingsRequest.QuickBolusIncrement.valueOf(
            incrementInput.text.toString().uppercase()
        )
        val message = SetQuickBolusSettingsRequest(enabled, mode, increment)
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerDismissNotificationRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("DismissNotificationRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val notificationIdInput = EditText(context)
    notificationIdInput.hint = "Notification ID"
    notificationIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    val notificationTypeInput = EditText(context)
    notificationTypeInput.hint = "Notification type (0-3)"
    notificationTypeInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(notificationIdInput, notificationTypeInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = DismissNotificationRequest(
            DismissNotificationRequest.NotificationType.fromId(
                notificationTypeInput.text.toString().toInt()
            ) ?: DismissNotificationRequest.NotificationType.REMINDER,
            notificationIdInput.text.toString().toLong(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetG6TransmitterIdRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetG6TransmitterIdRequest")
    val input = EditText(context)
    input.hint = "Transmitter ID"
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetG6TransmitterIdRequest(input.text.toString())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerStartDexcomG6SensorSessionRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("StartDexcomG6SensorSessionRequest")
    val input = EditText(context)
    input.hint = "Sensor code (0 for no code)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        val code = if (input.text.isNullOrBlank()) 0 else input.text.toString().toInt()
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(StartDexcomG6SensorSessionRequest(code)))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetDexcomG7PairingCodeRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetDexcomG7PairingCodeRequest")
    val input = EditText(context)
    input.hint = "Pairing code"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetDexcomG7PairingCodeRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerFillCannulaRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("FillCannulaRequest")
    val input = EditText(context)
    input.hint = "Prime size (milliunits)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(FillCannulaRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerChangeTimeDateRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("ChangeTimeDateRequest")
    val input = EditText(context)
    input.hint = "Tandem epoch seconds"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(ChangeTimeDateRequest(input.text.toString().toLong())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetActiveIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetActiveIDPRequest")
    val input = EditText(context)
    input.hint = "IDP ID"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetActiveIDPRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerCreateIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("CreateIDPRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val nameInput = EditText(context)
    nameInput.hint = "Profile name"
    val carbRatioInput = EditText(context)
    carbRatioInput.hint = "First segment carb ratio"
    carbRatioInput.inputType = InputType.TYPE_CLASS_NUMBER
    val basalRateInput = EditText(context)
    basalRateInput.hint = "First segment basal rate"
    basalRateInput.inputType = InputType.TYPE_CLASS_NUMBER
    val targetBgInput = EditText(context)
    targetBgInput.hint = "First segment target BG"
    targetBgInput.inputType = InputType.TYPE_CLASS_NUMBER
    val isfInput = EditText(context)
    isfInput.hint = "First segment ISF"
    isfInput.inputType = InputType.TYPE_CLASS_NUMBER
    val durationInput = EditText(context)
    durationInput.hint = "Insulin duration"
    durationInput.inputType = InputType.TYPE_CLASS_NUMBER
    val carbEntryInput = EditText(context)
    carbEntryInput.hint = "Carb entry"
    carbEntryInput.inputType = InputType.TYPE_CLASS_NUMBER
    val sourceIdInput = EditText(context)
    sourceIdInput.hint = "Source IDP ID (blank for new)"
    listOf(
        nameInput,
        carbRatioInput,
        basalRateInput,
        targetBgInput,
        isfInput,
        durationInput,
        carbEntryInput,
        sourceIdInput,
    ).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = if (sourceIdInput.text.isNullOrBlank()) {
            CreateIDPRequest(
                nameInput.text.toString(),
                carbRatioInput.text.toString().toInt(),
                basalRateInput.text.toString().toInt(),
                targetBgInput.text.toString().toInt(),
                isfInput.text.toString().toInt(),
                durationInput.text.toString().toInt(),
                carbEntryInput.text.toString().toInt(),
            )
        } else {
            CreateIDPRequest(
                nameInput.text.toString(),
                sourceIdInput.text.toString().toInt(),
            )
        }
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerRenameIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("RenameIDPRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val idInput = EditText(context)
    idInput.hint = "IDP ID"
    idInput.inputType = InputType.TYPE_CLASS_NUMBER
    val nameInput = EditText(context)
    nameInput.hint = "New profile name"
    listOf(idInput, nameInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(RenameIDPRequest(idInput.text.toString().toInt(), nameInput.text.toString())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerDeleteIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("DeleteIDPRequest")
    val input = EditText(context)
    input.hint = "IDP ID"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(DeleteIDPRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetIDPSettingsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetIDPSettingsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val idInput = EditText(context)
    idInput.hint = "IDP ID"
    idInput.inputType = InputType.TYPE_CLASS_NUMBER
    val insulinDurationInput = EditText(context)
    insulinDurationInput.hint = "Insulin duration"
    insulinDurationInput.inputType = InputType.TYPE_CLASS_NUMBER
    val carbEntryInput = EditText(context)
    carbEntryInput.hint = "Carb entry"
    carbEntryInput.inputType = InputType.TYPE_CLASS_NUMBER
    val changeTypeInput = EditText(context)
    changeTypeInput.hint = "Change type (CHANGE_INSULIN_DURATION or CHANGE_CARB_ENTRY)"
    listOf(idInput, insulinDurationInput, carbEntryInput, changeTypeInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val changeType = SetIDPSettingsRequest.ChangeType.valueOf(changeTypeInput.text.toString().uppercase())
        val message = SetIDPSettingsRequest(
            idInput.text.toString().toInt(),
            insulinDurationInput.text.toString().toInt(),
            carbEntryInput.text.toString().toInt(),
            changeType,
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetIDPSegmentRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetIDPSegmentRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val idpIdInput = EditText(context)
    idpIdInput.hint = "IDP ID"
    idpIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    val unknownIdInput = EditText(context)
    unknownIdInput.hint = "Unknown ID"
    unknownIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    val segmentIndexInput = EditText(context)
    segmentIndexInput.hint = "Segment index"
    segmentIndexInput.inputType = InputType.TYPE_CLASS_NUMBER
    val operationInput = EditText(context)
    operationInput.hint = "Operation (MODIFY_SEGMENT_ID/CREATE_SEGMENT/DELETE_SEGMENT_ID)"
    val startTimeInput = EditText(context)
    startTimeInput.hint = "Profile start time"
    startTimeInput.inputType = InputType.TYPE_CLASS_NUMBER
    val basalRateInput = EditText(context)
    basalRateInput.hint = "Profile basal rate"
    basalRateInput.inputType = InputType.TYPE_CLASS_NUMBER
    val carbRatioInput = EditText(context)
    carbRatioInput.hint = "Profile carb ratio"
    carbRatioInput.inputType = InputType.TYPE_CLASS_NUMBER
    val targetBgInput = EditText(context)
    targetBgInput.hint = "Profile target BG"
    targetBgInput.inputType = InputType.TYPE_CLASS_NUMBER
    val isfInput = EditText(context)
    isfInput.hint = "Profile ISF"
    isfInput.inputType = InputType.TYPE_CLASS_NUMBER
    val statusInput = EditText(context)
    statusInput.hint = "Status bitmask"
    statusInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(
        idpIdInput,
        unknownIdInput,
        segmentIndexInput,
        operationInput,
        startTimeInput,
        basalRateInput,
        carbRatioInput,
        targetBgInput,
        isfInput,
        statusInput,
    ).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetIDPSegmentRequest(
            idpIdInput.text.toString().toInt(),
            unknownIdInput.text.toString().toInt(),
            segmentIndexInput.text.toString().toInt(),
            SetIDPSegmentRequest.IDPSegmentOperation.valueOf(operationInput.text.toString().uppercase()),
            startTimeInput.text.toString().toInt(),
            basalRateInput.text.toString().toInt(),
            carbRatioInput.text.toString().toLong(),
            targetBgInput.text.toString().toInt(),
            isfInput.text.toString().toInt(),
            statusInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetMaxBolusLimitRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetMaxBolusLimitRequest")
    val input = EditText(context)
    input.hint = "Max bolus (milliunits)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetMaxBolusLimitRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetMaxBasalLimitRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetMaxBasalLimitRequest")
    val input = EditText(context)
    input.hint = "Max hourly basal (milliunits)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetMaxBasalLimitRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetPumpSoundsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetPumpSoundsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val quickBolusInput = EditText(context)
    quickBolusInput.hint = "Quick bolus annunciation"
    quickBolusInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val generalInput = EditText(context)
    generalInput.hint = "General annunciation"
    generalInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val reminderInput = EditText(context)
    reminderInput.hint = "Reminder annunciation"
    reminderInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val alertInput = EditText(context)
    alertInput.hint = "Alert annunciation"
    alertInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val alarmInput = EditText(context)
    alarmInput.hint = "Alarm annunciation"
    alarmInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val cgmAInput = EditText(context)
    cgmAInput.hint = "CGM alert annunciation A"
    cgmAInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val cgmBInput = EditText(context)
    cgmBInput.hint = "CGM alert annunciation B"
    cgmBInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val changeBitmaskInput = EditText(context)
    changeBitmaskInput.hint = "Change bitmask"
    changeBitmaskInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    listOf(
        quickBolusInput,
        generalInput,
        reminderInput,
        alertInput,
        alarmInput,
        cgmAInput,
        cgmBInput,
        changeBitmaskInput,
    ).forEach { layout.addView(it) }
    builder.setView(layout)
//    builder.setNeutralButton("RunTest") { dialog, _ ->
//        dialog.dismiss()
//        val message = {r: Int -> SetPumpSoundsRequest(
//            r,
//            3,
//            r,
//            3,
//            3,
//            3,
//            0,
//            0,
//            4,
//        )}
//        sendPumpCommands(SendType.DEBUG_PROMPT, (-127..128).flatMap { i -> listOf(message(i)) } )
//    }
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetPumpSoundsRequest(
            quickBolusInput.text.toString().toInt(),
            generalInput.text.toString().toInt(),
            reminderInput.text.toString().toInt(),
            alertInput.text.toString().toInt(),
            alarmInput.text.toString().toInt(),
            cgmAInput.text.toString().toInt(),
            cgmBInput.text.toString().toInt(),
            changeBitmaskInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetPumpAlertSnoozeRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetPumpAlertSnoozeRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val enabledInput = EditText(context)
    enabledInput.hint = "Snooze enabled (true/false/1/0)"
    val durationInput = EditText(context)
    durationInput.hint = "Snooze duration minutes"
    durationInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(enabledInput, durationInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetPumpAlertSnoozeRequest(
            parseBoolInput(enabledInput.text.toString()),
            durationInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun messageCacheToJson(cache: List<Pair<Message, Instant>>): String {
    val arr = JSONArray()
    cache.forEach {
        arr.put(JSONObject(messagePairToJson(it)))
    }
    return arr.toString()
}

fun messagePairToJson(it: Pair<Message, Instant>): String {
    val obj = JSONObject()
    obj.put("message", it.first.javaClass.name)
    obj.put("props", messagePropsToJson(it.first.props()))
    obj.put("fields", JSONObject(JavaHelpers.autoToStringJson(it.first, setOf())))
    obj.put("time", it.second)
    obj.put("timeEpoch", it.second.epochSecond)
    return obj.toString()
}

fun messagePropsToJson(props: MessageProps): JSONObject? {
    val raw = props.toString().removePrefix("@com.jwoglom.pumpx2.pump.messages.annotations.MessageProps(").removeSuffix(")")
    val spl = Splitter.on(", ")
            .withKeyValueSeparator('=')
            .split(raw) as Map<*, *>?
    return spl?.let { JSONObject(it) }
}

fun shortPumpMessageTitle(message: Any): String {
    return message.javaClass.name.replace(
        "com.jwoglom.pumpx2.pump.messages.response.",
        ""
    )
}

fun shortHistoryLogPumpMessageTitle(message: HistoryLog): String {
    val title = message.javaClass.name.replace(
        "com.jwoglom.pumpx2.pump.messages.response.historyLog.",
        ""
    )
    if (title == "UnknownHistoryLog") {
        return "${title}_${message.typeId()}"
    }
    return title
}

fun shortPumpMessageDetail(message: Message): String {
    return message.toString().removePrefix("${message.javaClass.simpleName}[").removeSuffix("]")
}

fun verbosePumpMessage(message: Message): String {
    return message.verboseToString().replace("com.jwoglom.pumpx2.pump.messages.", "")
}

fun historyLogCacheToJson(cache: Map<Long, HistoryLog>): String {
    val arr = JSONArray()
    cache.entries.sortedBy {
        it.key
    }.forEach {
        arr.put(JSONObject(historyLogToJson(it)))
    }
    return arr.toString()
}

fun historyLogMessagePropsToJson(props: HistoryLogProps?): JSONObject? {
    if (props == null) {
        return JSONObject()
    }
    val raw = props.toString().removePrefix("@com.jwoglom.pumpx2.pump.messages.annotations.HistoryLogProps(").removeSuffix(")")
    val spl = Splitter.on(", ")
        .withKeyValueSeparator('=')
        .split(raw) as Map<*, *>?
    return spl?.let { JSONObject(it) }
}

fun historyLogToJson(it: Map.Entry<Long, HistoryLog>): String {
    val obj = JSONObject()
    obj.put("historyLog", it.value.javaClass.name)
    obj.put("props", historyLogMessagePropsToJson(it.value.props()))
    obj.put("fields", JSONObject(JavaHelpers.autoToStringJson(it.value, setOf())))
    obj.put("sequenceNum", it.key)
    obj.put("pumpTimeSec", it.value.pumpTimeSec)
    obj.put("pumpTime", it.value.pumpTimeSecInstant)
    return obj.toString()
}

@Preview(showBackground = true)
@Composable
internal fun DebugDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            Debug(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}