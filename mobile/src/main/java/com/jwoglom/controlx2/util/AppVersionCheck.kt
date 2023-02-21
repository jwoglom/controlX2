package com.jwoglom.controlx2.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.common.base.Strings
import org.json.JSONObject
import timber.log.Timber
import java.util.*


const val AppVersionCheckUrl = "https://versioncheck.controlx2.org/check/"
const val AppOpenUrl = "https://github.com/jwoglom/controlx2/releases"

/**
 * On [CommService] launch, a scheduled task is set for the first background update which:
 *  1. Sends a network request to https://versioncheck.controlx2.org/check/ containing:
 *    - PumpX2 software version
 *    - Current timezone of the phone
 *    - Current region name in the locale
 *  2. Parse the response, identifying whether there is an updated version available.
 *  3. If an updated version is available, sends a system notification which, when clicked,
 *     opens the GitHub releases page to download the update.
 *
 * Following this check, another check is not performed until the background update following
 * the next time the background service is started (when onCreate is next triggered).
 */
fun AppVersionCheck(context: Context) {
    val version = AppVersionInfo(context)
    val data = buildAppVersionCheckData(version)

    val req = JsonObjectRequest(
        Request.Method.GET,
        "${AppVersionCheckUrl}${version.version}",
        data,
        { response ->
            Timber.i("AppVersionCheck response: $response")
            val upToDate: Boolean? = response.getBoolean("upToDate")
            if (upToDate == false) {
                val newVersion = Strings.nullToEmpty(response.getString("newVersion"))
                val description = Strings.nullToEmpty(response.getString("description"))

                notifyForUpdate(context, description, newVersion)
            }
        },
        { error ->
            Timber.e(error, "AppVersionCheck error: $error")
        }
    )

    VolleyQueue.getInstance(context).add(req)
}

fun buildAppVersionCheckData(version: AppVersionInfo): JSONObject {
    val o = JSONObject()
    o.put("version", version)

    val u = JSONObject()
    u.put("timezone", TimeZone.getDefault().id)
    u.put("countryCode", Locale.getDefault().country)

    o.put("user", u)

    return o
}

fun notifyForUpdate(context: Context, description: String, newVersion: String) {
    val notificationChannelId = "ControlX2 App Updates"

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
    val channel = NotificationChannel(
        notificationChannelId,
        "Notification channel for app update notifications",
        NotificationManager.IMPORTANCE_HIGH
    ).let {
        it.description = "ControlX2 App Updates"
        it.setShowBadge(false)
        it.lockscreenVisibility = 1

        it
    }
    notificationManager.createNotificationChannel(channel)

    val notificationIntent = Intent(Intent.ACTION_VIEW, Uri.parse(AppOpenUrl))
    val pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent,
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

    NotificationCompat.Builder(context, notificationChannelId)
        .setContentTitle("ControlX2 Update Available: $newVersion")
        .setContentText(description)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
}