@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.wearx2.presentation.screens.sections

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.app.ShareCompat
import androidx.navigation.NavHostController
import com.google.common.base.Splitter
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.annotations.HistoryLogProps
import com.jwoglom.pumpx2.pump.messages.annotations.MessageProps
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusPermissionChangeReasonRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.util.MessageHelpers
import com.jwoglom.pumpx2.shared.JavaHelpers
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.util.SendType
import com.jwoglom.wearx2.shared.util.shortTimeAgo
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import java.util.stream.Collectors


@Composable
fun Debug(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
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
            .setChooserTitle("WearX2 Debug Data")
            .startChooser();
    }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
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
                                                        shareTextContents(messageCacheToJson(it), "WearX2 JSON debug data","text/json")
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
                                                shareTextContents(messagePairToJson(message), "WearX2 ${shortPumpMessageTitle(message.first)} debug data","text/json")
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
                    headlineText = { Text("View History Log Messages") },
                    supportingText = { Text("Displays pump history log messages") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        showHistoryLogs = true
                    }
                )

                if (showHistoryLogs) {
                    Popup(
                        onDismissRequest = { showHistoryLogs = false }
                    ) {
                        LaunchedEffect(Unit) {
                            sendMessage("/to-pump/debug-historylog-cache", "".toByteArray())
                        }

                        val historyLogCache = ds.historyLogCache.observeAsState()
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
                                                        shareTextContents(historyLogCacheToJson(it), "WearX2 History Log JSON data","text/json")
                                                    }
                                                }
                                            ) {
                                                Text("Export")
                                            }
                                        }
                                    }
                                }
                                if (historyLogCache.value?.entries?.isEmpty() == true) {
                                    item {
                                        Text("No history log entries present in cache.")
                                    }
                                }
                                historyLogCache.value?.entries?.sortedBy {
                                    it.key * -1
                                }?.forEach { log ->
                                    item {
                                        ListItem(
                                            headlineText = {
//                                                // message name
                                                Text(shortPumpMessageTitle(log.value))
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
                                                shareTextContents(historyLogToJson(log), "WearX2 History Log Event","text/json")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter start log ID")
    builder.setMessage("Enter the ID of the first history log item to return from")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK") { dialog, which ->
        val startLog = input1.text.toString()
        Timber.i("startLog id: %s", startLog)
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("Enter number of logs ")
        builder2.setMessage("Enter the max number of logs to return")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
        builder2.setView(input2)
        builder2.setPositiveButton(
            "OK"
        ) { dialog, which ->
            val maxLogs = input2.text.toString()
            Timber.i("idp segment: %s", maxLogs)
            sendPumpCommands(
                SendType.DEBUG_PROMPT,
                listOf(
                    HistoryLogRequest(startLog.toInt().toLong(), maxLogs.toInt())
                )
            )
            // tandemEventCallback.requestedHistoryLogStartId = startLog.toInt()
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
    WearX2Theme() {
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