package com.jwoglom.wearx2

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.shared.Hex
import com.jwoglom.wearx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import timber.log.Timber
import java.time.Instant


public class BolusNotificationBroadcastReceiver : BroadcastReceiver(),
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private lateinit var mApiClient: GoogleApiClient

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.i("BolusNotificationBroadcastReceiver $context $intent")
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

                    mApiClient = GoogleApiClient.Builder(context!!)
                        .addApi(Wearable.API)
                        .addConnectionCallbacks(this@BolusNotificationBroadcastReceiver)
                        .addOnConnectionFailedListener(this@BolusNotificationBroadcastReceiver)
                        .build()

                    mApiClient.connect()
                    while (!mApiClient.isConnected && mApiClient.hasConnectedApi(Wearable.API)) {
                        Timber.d("BolusNotificationBroadcastReceiver is waiting on mApiClient connection")
                        Thread.sleep(250)
                    }

                    sendMessage("/to-wear/initiate-confirmed-bolus", rawBytes)
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
                    reply(
                        context, notifId, confirmBolusRequestBaseNotification(
                            context,
                            "Requesting Bolus",
                            "${bolusSummaryText(intentRequest)} " +
                                    "will be delivered"
                        )
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
                            resetPrefs(context)
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
                            resetPrefs(context)
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
            "CANCEL" -> {
                val bolusId = intent.getIntExtra("bolusId", 0)

                repeat (5) {
                    // fall-open on allowing cancellation, and retry several times
                    sendMessage(
                        "/to-pump/command",
                        PumpMessageSerializer.toBytes(CancelBolusRequest(bolusId))
                    )
                    Thread.sleep(500)
                }
            }
            else -> {
                Timber.w("BolusNotificationBroadcastReceiver rejected action: $action ${intent?.extras}")
                reply(
                    context,
                    notifId,
                    confirmBolusRequestBaseNotification(
                        context,
                        "Bolus Rejected",
                        "The bolus will not be delivered."
                    )
                )
                resetPrefs(context)
            }
        }
    }

    private fun validateBolusInfo(intent: Intent?, context: Context?): InitiateBolusRequest? {
        val intentRequestBytes = intent?.getByteArrayExtra("request")
        val notifId = getCurrentNotificationId(context)
        if (intentRequestBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid intent")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(context, "Bolus Error", "Invalid intent.")
            )
            resetPrefs(context)
            return null
        }

        val currentBolusToConfirm = getCurrentBolusToConfirm(context)

        val intentRequest = PumpMessageSerializer.fromBytes(intentRequestBytes)
        if (!intentRequest?.cargo.contentEquals(currentBolusToConfirm?.cargo) || intentRequest !is InitiateBolusRequest) {
            Timber.w("BolusNotificationBroadcastReceiver mismatched intent $intentRequest $currentBolusToConfirm")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(context, "Bolus Error", "Mismatched intent.")
            )
            resetPrefs(context)
            return null
        }


        if (checkBolusTimeExpired(context)) {
            Timber.e("Bolus expired: $intentRequest")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(
                    context,
                    "Bolus Request Expired",
                    "The bolus request expired: $intentRequest"
                )
            )
            resetPrefs(context)
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

    private fun checkBolusTimeExpired(context: Context?): Boolean {
        val initiateMillis = prefs(context)?.getLong("initiateBolusTime", 0L) ?: return true

        val nowMillis = Instant.now().toEpochMilli()

        // Bolus expires after 1 minute
        if (nowMillis - initiateMillis > 60 * 1000) {
            return true
        }

        return false
    }

    private fun getInitiateBolusResponse(intent: Intent?): InitiateBolusResponse? {
        val intentResponseBytes = intent?.getByteArrayExtra("response")
        if (intentResponseBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid response intent")
            return null
        }

        return PumpMessageSerializer.fromBytes(intentResponseBytes) as InitiateBolusResponse
    }

    private fun resetPrefs(context: Context?) {
        prefs(context)?.edit()?.remove("initiateBolusRequest")?.apply()
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

    override fun onConnected(bundle: Bundle?) {
        Timber.i("broadcastReceiver onConnected $bundle")
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.i("broadcastReceiver onConnectionSuspended: $id")
        mApiClient.reconnect()
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.i("broadcastReceiver onConnectionFailed: $result")
        mApiClient.connect()
    }

    fun sendMessage(path: String, message: ByteArray) {
        Timber.i("service sendMessage: $path ${String(message)}")
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("service sendMessage nodes: ${nodes.nodes}")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                    .setResultCallback { result ->
                        if (result.status.isSuccess) {
                            Timber.i("service message sent: $path ${String(message)} to: $node")
                        } else {
                            Timber.w(
                                "service sendMessage callback: ${result.status} for: $path ${
                                    String(
                                        message
                                    )
                                }"
                            )
                        }
                    }
            }
        }
    }

}