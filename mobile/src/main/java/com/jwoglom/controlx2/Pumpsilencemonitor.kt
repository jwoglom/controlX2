package com.jwoglom.controlx2

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import timber.log.Timber

/**
 * Avisa cuando:
 *  - ControlX2 lleva mas de [LIMIT_MS] sin recibir NADA de la bomba (conexion perdida), o
 *  - la bomba lleva mas de [LIMIT_MS] sin lectura NUEVA del sensor (Control-IQ sin datos).
 *
 * La cuenta se reinicia con cada mensaje de la bomba / cada lectura nueva, y la notificacion se
 * retira sola al recuperarse. Usa una alarma del sistema (no un bucle), asi que no consume nada
 * entre lecturas y tolera el modo de ahorro de bateria (puede retrasarse unos minutos).
 */
class PumpSilenceMonitor(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var started = false
    private var lastMessageAtMs = 0L
    private var lastReadingAtMs = 0L
    private var lastReadingStamp: Long? = null
    private var lastScheduleAtMs = 0L
    private var pumpAlertActive = false
    private var sensorAlertActive = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            check()
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val now = System.currentTimeMillis()
        // Si al arrancar ya hay un problema, la cuenta empieza ahora.
        lastMessageAtMs = now
        lastReadingAtMs = now
        createChannel()
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ACTION_CHECK), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        schedule()
        Timber.i("PumpSilenceMonitor iniciado (limite %d min)", LIMIT_MS / 60000)
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        alarmManager.cancel(pendingIntent())
    }

    @Synchronized
    fun onMessage(message: Message) {
        if (!started) return
        val now = System.currentTimeMillis()
        lastMessageAtMs = now
        if (pumpAlertActive) {
            pumpAlertActive = false
            notificationManager.cancel(ID_PUMP)
        }

        if (message is CurrentEGVGuiDataResponse && message.cgmReading > 0) {
            val stamp = message.bgReadingTimestampSeconds.toLong()
            val previous = lastReadingStamp
            lastReadingStamp = stamp
            // Lectura nueva = la hora de la lectura cambia. La primera que se ve solo se anota.
            if (previous != null && stamp != previous) {
                lastReadingAtMs = now
                if (sensorAlertActive) {
                    sensorAlertActive = false
                    notificationManager.cancel(ID_SENSOR)
                }
            }
        }

        // Reprogramar como mucho una vez por minuto
        if (now - lastScheduleAtMs >= 60_000L) schedule()
    }

    @Synchronized
    private fun check() {
        if (!started) return
        val now = System.currentTimeMillis()
        val sinceMessage = now - lastMessageAtMs
        val sinceReading = now - lastReadingAtMs

        if (sinceMessage >= LIMIT_MS) {
            if (!pumpAlertActive) {
                pumpAlertActive = true
                notify(
                    ID_PUMP,
                    "Sin conexión con la bomba",
                    "ControlX2 lleva ${sinceMessage / 60000} min sin recibir datos de la bomba."
                )
            }
        } else if (sinceReading >= LIMIT_MS && !sensorAlertActive) {
            sensorAlertActive = true
            notify(
                ID_SENSOR,
                "Bomba sin lectura del sensor",
                "La bomba lleva ${sinceReading / 60000} min sin lectura nueva del sensor. " +
                    "Control-IQ puede no estar funcionando."
            )
        }
        schedule()
    }

    /** Programa la alarma para el primer aviso pendiente. */
    private fun schedule() {
        lastScheduleAtMs = System.currentTimeMillis()
        val candidates = mutableListOf<Long>()
        if (!pumpAlertActive) candidates += lastMessageAtMs + LIMIT_MS
        if (!sensorAlertActive) candidates += lastReadingAtMs + LIMIT_MS
        val at = candidates.minOrNull()
        if (at == null) {
            alarmManager.cancel(pendingIntent())
            return
        }
        // Si ya vencio, comprobar en 1 minuto (evita bucles rapidos).
        val triggerAt = maxOf(at, System.currentTimeMillis() + 60_000L)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(ACTION_CHECK).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context, 7001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Avisos de pérdida de datos", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Bomba sin conexión o sin lectura del sensor" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notify(id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.pump_notif_1d)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager.notify(id, n)
        } catch (e: SecurityException) {
            Timber.w(e, "Sin permiso para notificar")
        }
        Timber.w("PumpSilenceMonitor: %s - %s", title, text)
    }

    companion object {
        /** Tiempo sin datos antes de avisar. */
        const val LIMIT_MS = 30L * 60 * 1000
        private const val ACTION_CHECK = "com.jwoglom.controlx2.PUMP_SILENCE_CHECK"
        private const val CHANNEL_ID = "ControlX2 Pump Data Alerts"
        private const val ID_PUMP = 2001
        private const val ID_SENSOR = 2002
    }
}
