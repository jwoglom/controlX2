package com.jwoglom.controlx2.util

import android.content.Context
import java.util.Locale

/**
 * Historial del IOB real que da la bomba, guardado desde que se instala (la bomba solo informa del
 * IOB actual, no de los pasados). Una muestra cada ~4 min durante 25 h, en SharedPreferences
 * ("WearX2", igual que el resto de ajustes). Se usa para mostrar el IOB al tocar el grafico.
 */
object IobHistory {
    private const val FILE = "WearX2"
    private const val KEY = "iob-history"
    private const val MAX_AGE_S = 25L * 3600
    private const val MIN_GAP_S = 240L
    private const val MAX_MATCH_S = 6L * 60

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
    fun record(context: Context, epochSeconds: Long, units: Double) {
        val list = load(context).toMutableList()
        val last = list.lastOrNull()
        if (last != null && epochSeconds - last.first in 0 until MIN_GAP_S) return
        list.add(epochSeconds to units)
        val trimmed = list.filter { epochSeconds - it.first <= MAX_AGE_S }
        val raw = trimmed.joinToString(";") { "${it.first}:${String.format(Locale.US, "%.3f", it.second)}" }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        cachedRaw = raw
        cached = trimmed
    }

    /** IOB registrado mas cercano a [epochSeconds] (maximo 6 min de diferencia), o null. */
    @Synchronized
    fun valueAt(context: Context, epochSeconds: Long): Double? {
        val best = load(context).minByOrNull { Math.abs(it.first - epochSeconds) } ?: return null
        return if (Math.abs(best.first - epochSeconds) <= MAX_MATCH_S) best.second else null
    }
}
