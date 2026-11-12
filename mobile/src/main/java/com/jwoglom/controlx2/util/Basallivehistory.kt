package com.jwoglom.controlx2.util

import android.content.Context
import java.util.Locale

/**
 * Basal (u/h) que la bomba informa en directo, guardada desde el servicio de ControlX2 con la hora de
 * recepcion. Sirve para que el grafico y el texto al tocar muestren la basal actual mientras el
 * historial de la bomba aun no ha sincronizado los ultimos cambios de Control-IQ.
 *
 * Solo se guarda cuando el valor cambia (o cada 10 min como ancla): unas pocas entradas por hora,
 * 25 h como maximo, en SharedPreferences ("WearX2", clave "basal-live-history").
 */
object BasalLiveHistory {
    private const val FILE = "WearX2"
    private const val KEY = "basal-live-history"
    private const val MAX_AGE_S = 25L * 3600
    private const val ANCHOR_S = 600L

    private var cachedRaw: String? = null
    private var cached: List<Pair<Long, Double>> = emptyList()

    private fun load(context: Context): List<Pair<Long, Double>> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw == cachedRaw) return cached
        val parsed = raw.split(';').mapNotNull { item ->
            val p = item.split(':')
            val t = p.getOrNull(0)?.toLongOrNull()
            val v = p.getOrNull(1)?.toDoubleOrNull()
            if (t != null && v != null) t to v else null
        }
        cachedRaw = raw
        cached = parsed
        return parsed
    }

    @Synchronized
    fun record(context: Context, rateUnitsPerHour: Double, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        if (rateUnitsPerHour < 0.0 || rateUnitsPerHour.isNaN()) return
        val list = load(context)
        val last = list.lastOrNull()
        if (last != null && Math.abs(last.second - rateUnitsPerHour) < 0.0005 && nowSeconds - last.first < ANCHOR_S) return
        val updated = (list + (nowSeconds to rateUnitsPerHour)).filter { nowSeconds - it.first <= MAX_AGE_S }
        val raw = updated.joinToString(";") { "${it.first}:${String.format(Locale.US, "%.3f", it.second)}" }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        cachedRaw = raw
        cached = updated
    }

    /** Entradas guardadas desde [sinceSeconds], de antigua a reciente. */
    @Synchronized
    fun since(context: Context, sinceSeconds: Long): List<Pair<Long, Double>> =
        load(context).filter { it.first >= sinceSeconds }
}
