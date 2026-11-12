package com.jwoglom.controlx2.util

import android.content.Context
import java.util.Locale

/**
 * Lecturas de glucosa recibidas en directo de la bomba (hora de la lectura en segundos epoch y mg/dL),
 * guardadas desde el servicio de ControlX2 aunque la pantalla este cerrada. Sirven para calcular la
 * variacion entre lecturas consecutivas y rellenar el grafico mientras el historial de la bomba
 * todavia no ha sincronizado las ultimas lecturas.
 *
 * Tamano maximo: una lectura cada 5 min durante 25 h = unas 300 entradas (~4 KB) en SharedPreferences
 * ("WearX2", clave "cgm-live-history"). Las mas antiguas de 25 h se borran solas al guardar.
 */
object CgmLiveHistory {
    private const val FILE = "WearX2"
    private const val KEY = "cgm-live-history"
    private const val MAX_AGE_S = 25L * 3600

    private var cachedRaw: String? = null
    private var cached: List<Pair<Long, Int>> = emptyList()

    private fun load(context: Context): List<Pair<Long, Int>> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw == cachedRaw) return cached
        val parsed = raw.split(';').mapNotNull { item ->
            val p = item.split(':')
            val t = p.getOrNull(0)?.toLongOrNull()
            val v = p.getOrNull(1)?.toIntOrNull()
            if (t != null && v != null) t to v else null
        }
        cachedRaw = raw
        cached = parsed
        return parsed
    }

    @Synchronized
    fun record(context: Context, readingSeconds: Long, mgdl: Int, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        if (readingSeconds <= 0L || mgdl <= 0) return
        val list = load(context)
        if (list.any { it.first == readingSeconds }) return // misma lectura reenviada
        val updated = (list + (readingSeconds to mgdl))
            .filter { nowSeconds - it.first <= MAX_AGE_S }
            .sortedBy { it.first }
        val raw = updated.joinToString(";") { "${it.first}:${it.second}" }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        cachedRaw = raw
        cached = updated
    }

    /** Todas las lecturas guardadas desde [sinceSeconds], de antigua a reciente. */
    @Synchronized
    fun since(context: Context, sinceSeconds: Long): List<Pair<Long, Int>> =
        load(context).filter { it.first >= sinceSeconds }

    /**
     * Variacion entre la lectura de hora [readingSeconds] con valor [current] y la lectura guardada
     * inmediatamente anterior, si esta entre 1 y 9 min antes. null si no hay.
     */
    @Synchronized
    fun delta(context: Context, readingSeconds: Long, current: Int): Int? {
        val prev = load(context).filter { it.first < readingSeconds }.maxByOrNull { it.first } ?: return null
        val gap = readingSeconds - prev.first
        return if (gap in 1..(9 * 60)) current - prev.second else null
    }
}
