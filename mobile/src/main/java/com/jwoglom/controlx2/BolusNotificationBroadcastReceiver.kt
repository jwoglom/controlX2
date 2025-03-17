package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.shared.Hex
import timber.log.Timber
import java.time.Instant


class BolusNotificationBroadcastReceiver : BroadcastReceiver(), MessageClient.OnMessageReceivedListener {
    private lateinit var messageClient: MessageClient


    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.i("BolusNotificationBroadcastReceiver $context $intent")

        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)

        val action = intent?.getStringExtra("action")
        val notifId = getCurrentNotificationId(context)
        when (action) {
            "INITIATE" -> {
                validateBolusInfo(intent, context)?.let { intentRequest ->
                    Timber.w("BolusNotificationBroadcastReceiver performing $intentRequest")
                    val secretKey = prefs(context)?.getString("initiateBolusSecret", "") ?: ""

                    val rawBytes = InitiateConfirmedBolusSerializer.toBytes(
                        secretKey,
                        intentRequest
                    )

                    Timber.i(
                        "BolusNotificationBroadcastReceiver initiate-confirmed-bolus bytes: ${
                            String(
                                rawBytes
                            )
                        }"
                    )

                    // waitForApiClient()
                    val bolusSource = prefs(context)?.getString("initiateBolusSource", "") ?: ""
                    if (bolusSource == "wear") {
                        sendMessage("/to-wear/initiate-confirmed-bolus", rawBytes)
                    } else if (bolusSource == "phone") {
                        sendMessage("/to-phone/initiate-confirmed-bolus", rawBytes)
                    }
                    if (!PumpState.actionsAffectingInsulinDeliveryEnabled()) {
                        // The same message will appear on the wearable
                        reply(
                            context, notifId, confirmBolusRequestBaseNotification(
                                context,
                                "Bolus Not Enabled",
                                "A bolus was requested, but actions affecting insulin delivery are not enabled in the phone app settings."
                            )
                        )
                        return
                    }

                    val cancelIntent =
                        Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                            putExtra("action", "CANCEL")
                            putExtra("bolusId", intentRequest.bolusID)
                        }
                    val cancelPendingIntent = PendingIntent.getBroadcast(
                        context,
                        2003,
                        cancelIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                    )

                    reply(
                        context, notifId, confirmBolusRequestBaseNotification(
                            context,
                            "Requesting Bolus",
                            "${bolusSummaryText(intentRequest)} " +
                                    "will be delivered"
                        ).addAction(R.drawable.decline, "Cancel", cancelPendingIntent)
                    )
                }
            }
            "INITIATE_RESPONSE" -> {
                getCurrentBolusToConfirm(context)?.let { initiateRequest ->
                    getInitiateBolusResponse(intent)?.let { initiateResponse ->
                        if (initiateResponse.status != 0 || initiateResponse.statusType != InitiateBolusResponse.BolusResponseStatus.SUCCESS) {
                            Timber.e("Bolus was not initiated. $initiateRequest $initiateResponse")
                            reply(
                                context,
                                notifId,
                                confirmBolusRequestBaseNotification(
                                    context,
                                    "Bolus Rejected By Pump",
                                    "The pump reported that it could not initiate the bolus: ${initiateResponse.statusType}"
                                )
                            )
                            resetBolusPrefs(context)
                            return
                        }

                        if (initiateRequest.bolusID != initiateResponse.bolusId) {
                            Timber.e("Sanity error. bolus id does not match: $initiateRequest $initiateResponse")
                            reply(
                                context,
                                notifId,
                                confirmBolusRequestBaseNotification(
                                    context,
                                    "Sanity Check Error",
                                    "Please check your pump to see if the bolus was delivered."
                                )
                            )
                            resetBolusPrefs(context)
                            return
                        }

                        val cancelIntent =
                            Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                                putExtra("action", "CANCEL")
                                putExtra("bolusId", initiateResponse.bolusId)
                            }
                        val cancelPendingIntent = PendingIntent.getBroadcast(
                            context,
                            2003,
                            cancelIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                        )

                        reply(
                            context, notifId, confirmBolusRequestBaseNotification(
                                context,
                                "Bolus Requested",
                                "The ${bolusSummaryText(initiateRequest)} is being delivered"
                            ).addAction(R.drawable.decline, "Cancel", cancelPendingIntent)
                        )
                    }
                }
            }
            "REJECT" -> {
                reply(
                    context, notifId, confirmBolusRequestBaseNotification(
                        context,
                        "Bolus Rejected By Phone",
                        "The bolus will not be completed."
                    )
                )
                resetBolusPrefs(context)
                // waitForApiClient()
                sendMessage("/to-wear/bolus-rejected", "from_phone".toByteArray())
            }
            "CANCEL" -> {
                val bolusId = intent.getIntExtra("bolusId", 0)

                repeat (5) {
                    // fall-open on allowing cancellation, and retry several times
                    sendMessage(
                        "/to-pump/command",
                        PumpMessageSerializer.toBytes(CancelBolusRequest(bolusId))
                    )
                    Thread.sleep(500)
                    // waitForApiClient()
                }
            }
            else -> {
                Timber.w("BolusNotificationBroadcastReceiver request invalid: $action ${intent?.extras}")
                reply(
                    context,
                    notifId,
                    confirmBolusRequestBaseNotification(
                        context,
                        "Bolus Receiver Request Invalid",
                        "The bolus will not be delivered."
                    )
                )
                resetBolusPrefs(context)
            }
        }
    }

    private fun validateBolusInfo(intent: Intent?, context: Context?): InitiateBolusRequest? {
        val intentRequestBytes = intent?.getByteArrayExtra("request")
        val notifId = getCurrentNotificationId(context)
        if (intentRequestBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid intent: $intentRequestBytes ${intent?.extras}")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(context, "Bolus Error", "Invalid intent.")
            )
            resetBolusPrefs(context)
            return null
        }

        val currentBolusToConfirm = getCurrentBolusToConfirm(context)

        val intentRequest = PumpMessageSerializer.fromBytes(intentRequestBytes)
        val expired = checkBolusTimeExpired(context)
        if (expired != null) {
            Timber.e("Bolus expired: $intentRequest")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(
                    context,
                    "Bolus Request Expired",
                    "The bolus request expired ${shortTimeAgo(Instant.now().plusMillis(expired))}. Boluses time out 1 minute after they are requested."
                )
            )
            resetBolusPrefs(context)
            return null
        }

        if (currentBolusToConfirm == null) {
            Timber.e("No current bolus to confirm, bolus request invalid: $intentRequest")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(
                    context,
                    "Bolus Request Invalid",
                    "The bolus request was invalid: $intentRequest"
                )
            )
            resetBolusPrefs(context)
            return null
        }

        if (!intentRequest.cargo.contentEquals(currentBolusToConfirm.cargo) || intentRequest !is InitiateBolusRequest) {
            Timber.e("BolusNotificationBroadcastReceiver mismatched intent intentRequest=$intentRequest currentBolusToConfirm=$currentBolusToConfirm ${intentRequest.cargo} != ${currentBolusToConfirm.cargo}")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(context, "Bolus Error", "Mismatched intent.")
            )
            resetBolusPrefs(context)
            return null
        }


        return intentRequest
    }

    private fun bolusSummaryText(intentRequest: InitiateBolusRequest): String {
        return "${
            twoDecimalPlaces(
                InsulinUnit.from1000To1(intentRequest.totalVolume)
            )
        }u bolus"
    }

    private fun getCurrentBolusToConfirm(context: Context?): InitiateBolusRequest? {
        val prefBytes = Hex.decodeHex(prefs(context)?.getString("initiateBolusRequest", ""))
        if (prefBytes == null || prefBytes.isEmpty()) {
            return null
        }

        val prefRequest = PumpMessageSerializer.fromBytes(prefBytes)
        if (prefRequest !is InitiateBolusRequest) {
            return null
        }

        return prefRequest
    }

    private fun checkBolusTimeExpired(context: Context?): Long? {
        val initiateMillis = prefs(context)?.getLong("initiateBolusTime", 0L) ?: return -1

        val nowMillis = Instant.now().toEpochMilli()

        // Bolus expires after 1 minute
        if (nowMillis - initiateMillis > 60 * 1000) {
            return nowMillis - initiateMillis
        }

        return null
    }

    private fun getInitiateBolusResponse(intent: Intent?): InitiateBolusResponse? {
        val intentResponseBytes = intent?.getByteArrayExtra("response")
        if (intentResponseBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid response intent")
            return null
        }

        return PumpMessageSerializer.fromBytes(intentResponseBytes) as InitiateBolusResponse
    }

    private fun resetBolusPrefs(context: Context?) {
        prefs(context)?.edit()
            ?.remove("initiateBolusRequest")
            ?.remove("initiateBolusTime")
            ?.apply()
    }

    private fun getCurrentNotificationId(context: Context?): Int {
        return prefs(context)?.getInt("initiateBolusNotificationId", 100) ?: 0
    }

    private fun reply(
        context: Context?,
        bolusNotificationId: Int,
        builder: NotificationCompat.Builder
    ) {
        cancelNotif(context, bolusNotificationId)
        makeNotif(context, bolusNotificationId, builder.build())
    }

    private fun makeNotif(context: Context?, id: Int, notif: Notification) {
        val notificationManager =
            context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        notificationManager.notify(id, notif)
    }

    private fun cancelNotif(context: Context?, id: Int) {
        val notificationManager =
            context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        notificationManager.cancel(id)
    }

    private fun prefs(context: Context?): SharedPreferences? {
        return context?.getSharedPreferences("WearX2", WearableListenerService.MODE_PRIVATE)
    }

    fun sendMessage(path: String, message: ByteArray) {
        Timber.i("bolusNotificationBroadcastReceiver sendMessage: $path ${String(message)}")
        val messageClient = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)

        fun inner(node: Node) {
            messageClient.sendMessage(node.id, path, message)
                .addOnSuccessListener {
                    Timber.d("bolusNotificationBroadcastReceiver message sent: $path ${String(message)} to: $node")
                }
                .addOnFailureListener {
                    Timber.w("bolusNotificationBroadcastReceiver sendMessage callback: ${it} for: $path ${String(message)}")
                }
        }
        if (!path.startsWith("/to-wear")) {
            inner(nodeClient.localNode.result)
        }
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                Timber.d("bolusNotificationBroadcastReceiver sendMessage nodes: ${nodes}")
                nodes.forEach { node ->
                    inner(node)
                }
            }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // does not currently listen for anything
    }

}