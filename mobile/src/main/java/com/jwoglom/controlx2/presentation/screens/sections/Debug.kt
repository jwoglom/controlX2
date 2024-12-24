@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.app.AlertDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
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
import kotlin.math.min


@Composable
fun Debug(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var showSendPumpMessageMenu by remember { mutableStateOf(false) }
    var showMessageCache by remember { mutableStateOf(false) }
    var showHistoryLogs by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val ds = LocalDataStore.current

//    fun setClipboard(str: String) {
//        val clipboard: ClipboardManager =
//            context.getSystemService(ComponentActivity.CLIPBOARD_SERVICE) as ClipboardManager
//        val clip = ClipData.newPlainText(str, str)
//        clipboard.setPrimaryClip(clip)
//    }

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
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    ListItem(
                        headlineText = { Text("Send Pump Message") },
                        supportingText = { Text("Displays the response message for the given request.") },
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
                    headlineText = { Text("View Received Message Cache") },
                    supportingText = { Text("Displays recently received pump messages.") },
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
                                            headlineText = {
                                                // message name
                                                Text(shortPumpMessageTitle(message.first))
                                            },
                                            supportingText = {
                                                // message detail
                                                Text(shortPumpMessageDetail(message.first))
                                            },
                                            overlineText = {
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
                    headlineText = { Text("Get History Logs") },
                    supportingText = { Text("Fetches history logs within the given range.") },
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
                    headlineText = { Text("View History Log Messages") },
                    supportingText = { Text("Displays pump history log messages") },
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
                                            headlineText = {
//                                                // message name
                                                Text(shortHistoryLogPumpMessageTitle(log.value))
                                            },
                                            supportingText = {
                                                // message detail
                                                Text("${log.value}")
                                            },
                                            overlineText = {
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
                            headlineText = { Text("Disable Only Snoop Bluetooth") },
                            supportingText = { Text("Re-enables app functionality.") },
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
                            headlineText = { Text("Enable Only Snoop Bluetooth") },
                            supportingText = { Text("All app functionality will be disabled, for debugging purposes only.") },
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
                    headlineText = { Text("Download ControlX2 Debug Logs") },
                    supportingText = { Text("Exports a text file with filtered logcat output.") },
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
                    headlineText = { Text("Clear Debug Logs") },
                    supportingText = { Text("Clears the saved debug logs.") },
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
                    headlineText = { Text("Empty database") },
                    supportingText = { Text("Removes all saved history logs in sqlite.") },
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
        input1.setText("${historyLogStatus.lastSequenceNum-255}")
    }
    val layout = LinearLayout(context)
    layout.addView(input1)

    if (historyLogStatus != null) {
        val slider = Slider(context)
        slider.valueFrom = historyLogStatus.firstSequenceNum.toFloat()
        slider.valueTo = historyLogStatus.lastSequenceNum.toFloat()
        slider.value = (historyLogStatus.lastSequenceNum-255).toFloat()
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
private fun DefaultPreview() {
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