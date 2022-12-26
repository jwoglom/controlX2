@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.wearx2.presentation.screens.sections

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.navigation.NavHostController
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusPermissionChangeReasonRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.util.MessageHelpers
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.util.SendType
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.util.stream.Collectors


@Composable
fun Debug(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ds = LocalDataStore.current


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
                                contentDescription = "Reload icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            expanded = true
                        }
                    )
                    Divider()
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
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
                                        } else if (className == BolusPermissionRequest::class.java.name) {
                                            triggerMessageWithBolusIdParameter(
                                                context,
                                                sendPumpCommands,
                                                BolusPermissionRequest::class.java
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
                                    expanded = false
                                },
                                leadingIcon = {}
                            )
                            Divider()
                        }
                    }


                    LaunchedEffect (Unit) {
                        sendMessage("/to-pump/debug-message-cache", "".toByteArray())
                    }

                    val debugMessageCache = ds.debugMessageCache.observeAsState()
                    Text(
                        if (debugMessageCache.value != null) "${debugMessageCache.value}"
                        else ""
                    )
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
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK") { dialog, which ->
        val startLog = input1.text.toString()
        Timber.i("startLog id: %s", startLog)
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("Enter number of logs ")
        builder2.setMessage("Enter the max number of logs to return")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
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