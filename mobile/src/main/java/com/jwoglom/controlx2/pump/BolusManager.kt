package com.jwoglom.controlx2.pump

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.jwoglom.controlx2.BolusNotificationBroadcastReceiver
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.shared.Hex
import timber.log.Timber
import java.time.Instant

class BolusManager(
    private val context: Context,
    private val sendWearCommMessage: (String, ByteArray) -> Unit,
) {
    private var bolusNotificationId: Int = 1000

    enum class BolusRequestSource(val id: String) {
        PHONE("phone"),
        WEAR("wear")
    }

    fun confirmBolusRequest(request: InitiateBolusRequest, source: BolusRequestSource) {
        val units = twoDecimalPlaces(InsulinUnit.from1000To1(request.totalVolume))
        Timber.i("confirmBolusRequest $units: $request")
        bolusNotificationId++
        prefs()?.edit()
            ?.putString("initiateBolusRequest", Hex.encodeHexString(PumpMessageSerializer.toBytes(request)))
            ?.putString("initiateBolusSecret", Hex.encodeHexString(Bytes.getSecureRandom10Bytes()))
            ?.putString("initiateBolusSource", source.id)
            ?.putLong("initiateBolusTime", Instant.now().toEpochMilli())
            ?.putInt("initiateBolusNotificationId", bolusNotificationId)
            ?.commit().let {
                if (it != true) {
                    Timber.e("synchronous preference write failed in confirmBolusRequest")
                }
            }

        fun getRejectIntent(): PendingIntent {
            val rejectIntent =
                Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "REJECT")
                }
            return PendingIntent.getBroadcast(
                context,
                2000,
                rejectIntent,
                FLAG_IMMUTABLE or FLAG_ONE_SHOT
            )
        }

        fun getConfirmIntent(): PendingIntent {
            val confirmIntent =
                Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "INITIATE")
                    putExtra("request", PumpMessageSerializer.toBytes(request))
                }
            return PendingIntent.getBroadcast(
                context,
                2001,
                confirmIntent,
                FLAG_IMMUTABLE or FLAG_ONE_SHOT
            )
        }

        val minNotifyThreshold = Prefs(context).bolusConfirmationInsulinThreshold()
        val autoApproveTimeout = if (source == BolusRequestSource.WEAR)
            Prefs(context).wearBolusAutoApproveTimeoutSeconds() else 0
        sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_MIN_NOTIFY_THRESHOLD, "$minNotifyThreshold".toByteArray())
        sendWearCommMessage(MessagePaths.TO_CLIENT_WEAR_AUTO_APPROVE_TIMEOUT, "$autoApproveTimeout".toByteArray())

        if (InsulinUnit.from1000To1(request.totalVolume) >= minNotifyThreshold || minNotifyThreshold == 0.0) {
            Timber.i("Requesting permission for bolus because $units >= minNotifyThreshold=$minNotifyThreshold")

            val builder = confirmBolusRequestBaseNotification(
                context,
                "Bolus Request",
                if (autoApproveTimeout > 0) "$units units. Auto-approving in ${autoApproveTimeout}s unless canceled."
                else "$units units. Press Confirm to deliver."
            )
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L))

            builder.addAction(R.drawable.decline, "Reject", getRejectIntent())

            builder.addAction(R.drawable.bolus_icon, "Confirm ${units}u", getConfirmIntent())

            val notif = builder.build()
            Timber.i("bolus notification $bolusNotificationId $builder $notif")
            makeNotif(bolusNotificationId, notif)

            // Broadcast to phone UI for in-app dialog
            sendWearCommMessage(MessagePaths.TO_SERVER_BOLUS_CONFIRM_DIALOG,
                "${Hex.encodeHexString(PumpMessageSerializer.toBytes(request))}|${source.id}|$autoApproveTimeout".toByteArray())

            // Schedule auto-approve if timeout is set
            if (autoApproveTimeout > 0) {
                scheduleAutoApprove(autoApproveTimeout, getConfirmIntent())
            }
        } else {
            Timber.i("Sending immediate bolus request because $units is less than minNotifyThreshold=$minNotifyThreshold")

            val confirmIntent =
                Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "INITIATE")
                    putExtra("request", PumpMessageSerializer.toBytes(request))
                }
            val confirmPendingIntent = PendingIntent.getBroadcast(
                context,
                2001,
                confirmIntent,
                FLAG_IMMUTABLE or FLAG_ONE_SHOT
            )
            // wait to avoid prefs not being saved
            Thread.sleep(250)
            confirmPendingIntent.send()
        }
    }

    private fun scheduleAutoApprove(timeoutSeconds: Int, confirmIntent: PendingIntent) {
        cancelAutoApproveStatic(context)
        val handler = Handler(Looper.getMainLooper())
        _autoApproveHandler = handler
        val runnable = Runnable {
            Timber.i("Auto-approving bolus after ${timeoutSeconds}s timeout")
            try {
                confirmIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                Timber.w("Auto-approve PendingIntent already cancelled (bolus was manually handled)")
            }
            _autoApproveRunnable = null
            _autoApproveHandler = null
        }
        _autoApproveRunnable = runnable
        handler.postDelayed(runnable, timeoutSeconds * 1000L)
    }

    fun resetBolusPrefs() {
        prefs()?.edit()
            ?.remove("initiateBolusRequest")
            ?.remove("initiateBolusTime")
            ?.apply()
    }

    private fun prefs(): SharedPreferences? {
        return context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
    }

    private fun makeNotif(id: Int, notif: Notification) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notif)
    }

    companion object {
        @Volatile
        private var _autoApproveHandler: Handler? = null
        @Volatile
        private var _autoApproveRunnable: Runnable? = null

        fun cancelAutoApproveStatic(context: Context?) {
            _autoApproveRunnable?.let { runnable ->
                _autoApproveHandler?.removeCallbacks(runnable)
                Timber.i("Auto-approve timer cancelled")
            }
            _autoApproveRunnable = null
            _autoApproveHandler = null
        }
    }
}

fun confirmBolusRequestBaseNotification(context: Context?, title: String, text: String): NotificationCompat.Builder {
    val notificationChannelId = "Confirm Bolus"

    val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        notificationChannelId,
        "Confirm Bolus",
        NotificationManager.IMPORTANCE_HIGH
    ).let {
        it.description = "Confirm Bolus"
        it
    }
    notificationManager.createNotificationChannel(channel)

    return NotificationCompat.Builder(
        context,
        notificationChannelId
    )
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(R.drawable.bolus_icon)
        .setTicker(title)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true)
}
