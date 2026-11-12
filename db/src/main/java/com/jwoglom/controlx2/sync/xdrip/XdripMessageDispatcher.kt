package com.jwoglom.controlx2.sync.xdrip

import android.content.Context
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusSnapshot
import com.jwoglom.controlx2.sync.xdrip.models.XdripSgvPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripTimedValue
import com.jwoglom.controlx2.sync.xdrip.models.XdripTreatmentPayload
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import timber.log.Timber
import java.time.Duration
import java.time.Instant

internal sealed class DispatchEvent {
    data class PumpBattery(val percent: Int) : DispatchEvent()
    data class PumpIob(val units: Double) : DispatchEvent()
    data class PumpReservoir(val units: Int) : DispatchEvent()
    data class PumpBasal(val unitsPerHour: Double) : DispatchEvent()
    data class BasalTreatment(val unitsPerHour: Double) : DispatchEvent()
    // readingTime = hora real de la lectura del sensor segun la bomba (null si no es fiable)
    data class CgmSgv(val mgdl: Int, val trendRate: Int, val readingTime: Instant? = null) : DispatchEvent()
    data class TreatmentInitiated(val bolusId: Int, val status: String) : DispatchEvent()
    data class TreatmentStatus(val bolusId: Int, val requestedVolumeMilli: Long, val status: String, val timestamp: Instant) : DispatchEvent()
    data class CarbEntry(val seqId: Long, val grams: Int, val timestamp: Instant)
    data class CarbEntries(val entries: List<CarbEntry>) : DispatchEvent()
    data object Other : DispatchEvent()
}

class XdripMessageDispatcher(
    private val broadcaster: XdripBroadcaster,
    private val configProvider: () -> XdripSyncConfig,
    private val nowProvider: () -> Instant = { Instant.now() }
) {
    constructor(context: Context) : this(
        broadcaster = XdripBroadcastSender(context),
        // The host apps store xDrip config in the legacy "WearX2" SharedPreferences
        // file (mobile Prefs.prefs() and wear WearPrefs.prefs() both use this file).
        configProvider = {
            XdripSyncConfig.load(
                context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
            )
        }
    )

    private val latestPumpSnapshot = XdripDeviceStatusSnapshot()

    fun onReceiveMessage(message: Message) {
        onEvent(message.toDispatchEvent())
    }

    internal fun onEvent(event: DispatchEvent) {
        val config = configProvider()
        if (!config.enabled) return

        val receivedAt = nowProvider()
        val categories = updateSnapshot(event, receivedAt)

        if (config.sendCgmSgv && event is DispatchEvent.CgmSgv) {
            // Se usa la hora real de la lectura: asi la misma lectura genera siempre el mismo
            // payload (se filtra como duplicada) y xDrip+ calcula bien la tendencia.
            val readingTime = event.readingTime ?: receivedAt
            Timber.i(
                "CGM mgdl=%d trendRate=%d readingTime=%s receivedAt=%s",
                event.mgdl, event.trendRate, event.readingTime, receivedAt
            )
            // La flecha sale del trendRate de la bomba (decimas de mg/dL por minuto, p. ej. -10 =
            // -1,0 mg/dL/min), que es lo que muestra ControlX2. La pendiente de las lecturas de los
            // ultimos 15 min se sigue calculando: es el respaldo si el trendRate no es utilizable y
            // reacciona mas despacio, asi que no se usa como fuente principal.
            val slopeDirection = directionFromReadings(event.mgdl, event.readingTime)
            val pumpDirection = directionFromPumpTrendRate(event.trendRate)
            val direction = pumpDirection ?: slopeDirection
            Timber.i(
                "CGM direction=%s (bomba=%s, pendiente=%s)",
                direction, pumpDirection, slopeDirection
            )
            val sgvPayload = XdripSgvPayload(
                mgdl = event.mgdl,
                mills = readingTime.toEpochMilli(),
                direction = direction
            ).toJsonArrayString()
            broadcaster.sendSgv(sgvPayload, config.cgmSgvMinimumIntervalSeconds)
        }

        if (config.sendPumpDeviceStatus && StatusCategory.PUMP_STATUS in categories) {
            val deviceStatusPayload = latestPumpSnapshot
                .toPayload(createdAt = receivedAt)
                .toJsonString()
            broadcaster.sendDeviceStatus(deviceStatusPayload, config.pumpDeviceStatusMinimumIntervalSeconds)
        }

        if (config.sendTreatments && StatusCategory.TREATMENT in categories) {
            val treatmentPayload = when (event) {
                // El "bolus iniciado" no lleva unidades: crea una entrada de 0 U que se suma a la del
                // estado. Se envia solo el estado, que si trae la insulina.
                is DispatchEvent.TreatmentInitiated -> null

                // La bomba repite este mensaje varias veces por bolus (REQUESTING, ...). Se envia
                // solo la primera vez con unidades para cada bolusId.
                is DispatchEvent.TreatmentStatus ->
                    if (event.requestedVolumeMilli > 0 && shouldSendBolusStatus(event.bolusId)) {
                        XdripTreatmentPayload
                            .fromStatus(
                                bolusId = event.bolusId,
                                requestedVolumeMilli = event.requestedVolumeMilli,
                                status = event.status,
                                timestamp = event.timestamp
                            )
                            .toJsonArrayString()
                    } else null

                // Solo se envia el basal si cambia la tasa o si caduca el tramo anterior; antes
                // salia una entrada nueva cada minuto, todas con hora distinta.
                is DispatchEvent.BasalTreatment ->
                    if (shouldSendBasal(event.unitsPerHour, receivedAt)) {
                        XdripTreatmentPayload
                            .forBasalRate(
                                unitsPerHour = event.unitsPerHour,
                                durationMinutes = BASAL_TREATMENT_DURATION_MINUTES,
                                timestamp = receivedAt
                            )
                            .toJsonArrayString()
                    } else null

                // Carbohidratos introducidos en la bomba (history log). Se envian los nuevos de las
                // ultimas horas, una sola vez cada uno; el historico antiguo no se reenvia.
                is DispatchEvent.CarbEntries -> {
                    val fresh = newCarbEntries(event.entries, receivedAt)
                    if (fresh.isEmpty()) null else {
                        val arr = org.json.JSONArray()
                        fresh.forEach {
                            arr.put(XdripTreatmentPayload.forCarbs(it.grams, it.seqId, it.timestamp).toJsonObject())
                        }
                        Timber.i("CARBS enviados a xDrip: %s", fresh)
                        arr.toString()
                    }
                }

                else -> null
            }
            if (treatmentPayload != null) {
                val isCarbs = event is DispatchEvent.CarbEntries
                broadcaster.sendTreatments(
                    treatmentsJsonString = treatmentPayload,
                    // Los carbohidratos no usan el intervalo minimo: son pocos y no deben perderse.
                    minimumIntervalSeconds = if (isCarbs) 0 else config.treatmentsMinimumIntervalSeconds,
                    alsoSendNewFood = !isCarbs
                )
            }
        }

        if (config.sendStatusLine && StatusCategory.PUMP_STATUS in categories) {
            // Linea corta para que quepa en la fila del reloj (junto a "3'" y la bateria del movil):
            // "IOB 8,88u 0,65u/h". Los minutos desde la lectura ya los pone xDrip en esa fila.
            val statusline = buildString {
                latestPumpSnapshot.iobUnits?.value?.let { append("IOB ${twoDecimalPlaces(it)}u") }
                latestPumpSnapshot.basalUnitsPerHour?.value?.let {
                    if (isNotEmpty()) append(' ')
                    append("${twoDecimalPlaces(it)}u/h")
                }
            }
            broadcaster.sendExternalStatusline(statusline, config.statusLineMinimumIntervalSeconds)
        }
    }

    private fun updateSnapshot(event: DispatchEvent, receivedAt: Instant): Set<StatusCategory> {
        return when (event) {
            is DispatchEvent.PumpBattery -> {
                latestPumpSnapshot.batteryPercent = XdripTimedValue(event.percent, receivedAt)
                setOf(StatusCategory.PUMP_STATUS)
            }
            is DispatchEvent.PumpIob -> {
                latestPumpSnapshot.iobUnits = XdripTimedValue(event.units, receivedAt)
                setOf(StatusCategory.PUMP_STATUS)
            }
            is DispatchEvent.PumpReservoir -> {
                latestPumpSnapshot.cartridgeUnits = XdripTimedValue(event.units, receivedAt)
                setOf(StatusCategory.PUMP_STATUS)
            }
            is DispatchEvent.PumpBasal -> {
                latestPumpSnapshot.basalUnitsPerHour = XdripTimedValue(event.unitsPerHour, receivedAt)
                setOf(StatusCategory.PUMP_STATUS)
            }
            is DispatchEvent.BasalTreatment -> {
                latestPumpSnapshot.basalUnitsPerHour = XdripTimedValue(event.unitsPerHour, receivedAt)
                setOf(StatusCategory.PUMP_STATUS, StatusCategory.TREATMENT)
            }
            is DispatchEvent.CgmSgv -> {
                latestPumpSnapshot.applySgvValue(event.mgdl, receivedAt)
                setOf(StatusCategory.PUMP_STATUS)
            }
            is DispatchEvent.TreatmentInitiated,
            is DispatchEvent.TreatmentStatus,
            is DispatchEvent.CarbEntries -> setOf(StatusCategory.TREATMENT)
            is DispatchEvent.Other -> setOf(StatusCategory.OTHER)
        }
    }

    private fun Message.toDispatchEvent(): DispatchEvent {
        return when (this) {
            is CurrentBatteryAbstractResponse -> DispatchEvent.PumpBattery(batteryPercent)
            is ControlIQIOBResponse -> DispatchEvent.PumpIob(InsulinUnit.from1000To1(pumpDisplayedIOB))
            is InsulinStatusResponse -> DispatchEvent.PumpReservoir(currentInsulinAmount)
            is CurrentBasalStatusResponse -> DispatchEvent.BasalTreatment(InsulinUnit.from1000To1(currentBasalRate))
            is CurrentEGVGuiDataResponse -> DispatchEvent.CgmSgv(
                mgdl = cgmReading,
                trendRate = trendRate,
                readingTime = pumpSecondsToInstantOrNull(bgReadingTimestampSeconds.toLong())
            )
            is InitiateBolusResponse -> DispatchEvent.TreatmentInitiated(bolusId, statusType.toString())
            is CurrentBolusStatusResponse -> DispatchEvent.TreatmentStatus(
                bolusId = bolusId,
                requestedVolumeMilli = requestedVolume,
                status = status.toString(),
                timestamp = pumpLocalTimeToInstant(timestampInstant)
            )
            is HistoryLogStreamResponse -> {
                val carbs = historyLogs.filterIsInstance<CarbEnteredHistoryLog>().mapNotNull {
                    val grams = it.carbs.toInt()
                    if (grams <= 0) null else DispatchEvent.CarbEntry(
                        seqId = it.sequenceNum.toLong(),
                        grams = grams,
                        timestamp = pumpLocalTimeToInstant(it.pumpTimeSecInstant)
                    )
                }
                if (carbs.isEmpty()) DispatchEvent.Other else DispatchEvent.CarbEntries(carbs)
            }
            else -> DispatchEvent.Other
        }
    }

    private val sentCarbSeqIds = LinkedHashSet<Long>()

    /** Entradas de carbohidratos aun no enviadas y de menos de [CARB_MAX_AGE]. */
    @Synchronized
    private fun newCarbEntries(entries: List<DispatchEvent.CarbEntry>, now: Instant): List<DispatchEvent.CarbEntry> {
        val fresh = entries.filter {
            it.seqId !in sentCarbSeqIds && Duration.between(it.timestamp, now) <= CARB_MAX_AGE
        }
        fresh.forEach { sentCarbSeqIds.add(it.seqId) }
        while (sentCarbSeqIds.size > 200) sentCarbSeqIds.remove(sentCarbSeqIds.first())
        return fresh
    }

    private var lastBolusStatusSentId: Int? = null

    @Synchronized
    private fun shouldSendBolusStatus(bolusId: Int): Boolean {
        if (bolusId == lastBolusStatusSentId) return false
        lastBolusStatusSentId = bolusId
        return true
    }

    private var lastBasalRate: Double? = null
    private var lastBasalSentAt: Instant? = null

    @Synchronized
    private fun shouldSendBasal(rate: Double, now: Instant): Boolean {
        val lastAt = lastBasalSentAt
        val expired = lastAt == null ||
            // Se reenvia 1 minuto antes de que caduque el tramo anterior para que no haya huecos
            // sin basal activo en xDrip+ (y en el reloj que lo lee de ahi).
            Duration.between(lastAt, now) >= Duration.ofMinutes(BASAL_TREATMENT_DURATION_MINUTES.toLong() - 1)
        if (rate == lastBasalRate && !expired) return false
        lastBasalRate = rate
        lastBasalSentAt = now
        return true
    }

    /**
     * Flecha a partir del trendRate de la bomba, en decimas de mg/dL por minuto. Usa las bandas
     * habituales de Dexcom: <1 estable, 1-2 flecha a 45 grados, 2-3 una flecha, >=3 doble flecha.
     * Devuelve null si el valor no es creible (se usa entonces la pendiente de las lecturas).
     */
    private fun directionFromPumpTrendRate(trendRate: Int): String? {
        val rate = trendRate / 10.0
        if (Math.abs(rate) > MAX_PUMP_TREND_MGDL_PER_MIN) return null
        return when {
            rate <= -3 -> "DoubleDown"
            rate <= -2 -> "SingleDown"
            rate <= -1 -> "FortyFiveDown"
            rate < 1 -> "Flat"
            rate < 2 -> "FortyFiveUp"
            rate < 3 -> "SingleUp"
            else -> "DoubleUp"
        }
    }

    private val recentReadings = ArrayDeque<Pair<Instant, Int>>()

    /**
     * Calcula la flecha a partir de las lecturas de los ultimos 15 minutos (mg/dL por minuto).
     * Sin hora de lectura fiable, o con menos de 4 minutos de historial, devuelve "NONE".
     */
    @Synchronized
    private fun directionFromReadings(mgdl: Int, readingTime: Instant?): String {
        if (readingTime == null) return "NONE"
        val last = recentReadings.lastOrNull()
        if (last == null || readingTime.isAfter(last.first)) {
            recentReadings.addLast(readingTime to mgdl)
        } else if (readingTime != last.first) {
            return "NONE" // lectura antigua o fuera de orden
        }
        while (recentReadings.isNotEmpty() &&
            Duration.between(recentReadings.first().first, readingTime) > SLOPE_WINDOW
        ) recentReadings.removeFirst()

        val oldest = recentReadings.first()
        val minutes = Duration.between(oldest.first, readingTime).seconds / 60.0
        if (minutes < MIN_SLOPE_MINUTES) return "NONE"
        val slope = (mgdl - oldest.second) / minutes
        Timber.i("CGM slope=%.2f mg/dL/min over %.1f min", slope, minutes)
        return when {
            slope <= -3 -> "DoubleDown"
            slope <= -2 -> "SingleDown"
            slope <= -1 -> "FortyFiveDown"
            slope < 1 -> "Flat"
            slope < 2 -> "FortyFiveUp"
            slope < 3 -> "SingleUp"
            else -> "DoubleUp"
        }
    }

    // Desfase aprendido entre la hora que da la bomba (leida como UTC por PumpX2) y la hora real.
    // Se calcula con las lecturas de glucosa, que siempre son recientes, y se aplica tambien a
    // los bolus. Asi funciona con cualquier zona horaria y aunque la bomba no se haya cambiado.
    @Volatile private var learnedOffset: Duration? = null
    @Volatile private var learnedOffsetAt: Instant? = null

    /**
     * Convierte los segundos de la bomba (desde 2008-01-01) en un Instant real.
     *
     * PumpX2 los lee como UTC, pero la bomba guarda su hora local, asi que hay un desfase que es
     * un multiplo de 15 minutos. Una vez aprendido se conserva mientras la lectura resultante sea
     * verosimil (de hace 0 a 30 min): redondear de nuevo en cada lectura movia las lecturas de mas
     * de 7 minutos al tramo siguiente y las dejaba en el futuro, y xDrip+ las guardaba asi.
     * Nunca se devuelve una hora posterior a la actual. Si no cuadra, devuelve null.
     */
    private fun pumpSecondsToInstantOrNull(pumpSeconds: Long): Instant? {
        if (pumpSeconds <= 0L) return null
        val now = nowProvider()
        val raw = Instant.ofEpochSecond(PUMP_EPOCH_UNIX_SECONDS + pumpSeconds)

        // 1) Desfase ya aprendido: se conserva si la lectura resultante es verosimil.
        learnedOffset?.let { offset ->
            val candidate = raw.plus(offset)
            val age = Duration.between(candidate, now)
            if (age >= MIN_READING_AGE && age <= MAX_KEPT_READING_AGE) {
                learnedOffsetAt = now
                return if (candidate.isAfter(now)) now else candidate
            }
        }

        // 2) Aprender: multiplo de 15 min que deja la lectura entre -2 y +13 min respecto a ahora.
        val diff = Duration.between(raw, now).seconds
        var offsetSeconds =
            Math.floorDiv(diff + FUTURE_MARGIN.seconds, OFFSET_STEP_SECONDS) * OFFSET_STEP_SECONDS

        // La bomba suele tener la hora local del movil. Si ese valor esperado queda a un solo
        // tramo de distancia, se prefiere: resuelve las lecturas de mas de 13 min y los relojes
        // de la bomba algo adelantados.
        val expected = -java.time.ZoneId.systemDefault().rules.getOffset(now).totalSeconds.toLong()
        if (Math.abs(offsetSeconds - expected) == OFFSET_STEP_SECONDS) offsetSeconds = expected

        val offset = Duration.ofSeconds(offsetSeconds)
        val candidate = raw.plus(offset)
        val age = Duration.between(candidate, now)
        if (offset.abs() > MAX_PUMP_OFFSET || age < MIN_LEARN_AGE || age > MAX_KEPT_READING_AGE) {
            Timber.w("CGM readingTime descartada: raw=%s now=%s offset=%s age=%s", raw, now, offset, age)
            return null
        }
        if (offset != learnedOffset) Timber.i("Desfase de la bomba aprendido: %s", offset)
        learnedOffset = offset
        learnedOffsetAt = now
        return if (candidate.isAfter(now)) now else candidate
    }

    /**
     * Hora real de un evento de bolus. Usa el desfase aprendido con la glucosa (si es de la
     * ultima hora). Si no hay, reinterpreta la hora de la bomba como hora local del movil.
     * Si el resultado queda en el futuro (> 5 min) se usa la hora actual.
     */
    private fun pumpLocalTimeToInstant(pumpInstant: Instant): Instant {
        val now = nowProvider()
        val offset = learnedOffset
        val learnedAt = learnedOffsetAt
        val corrected = if (offset != null && learnedAt != null &&
            Duration.between(learnedAt, now) <= OFFSET_MAX_AGE
        ) {
            pumpInstant.plus(offset)
        } else {
            pumpInstant
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDateTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
        }
        return if (corrected.isAfter(now.plus(Duration.ofMinutes(5)))) {
            Timber.w("Treatment timestamp en el futuro (%s), se usa la hora actual", corrected)
            now
        } else {
            corrected
        }
    }

    private enum class StatusCategory {
        PUMP_STATUS,
        TREATMENT,
        OTHER
    }

    companion object {
        /** Antiguedad maxima de unos carbohidratos para enviarlos a xDrip (evita volcar el historico). */
        internal val CARB_MAX_AGE: Duration = Duration.ofHours(3)

        /** Duration for basal treatment segments, matching the pump status polling interval. */
        internal const val BASAL_TREATMENT_DURATION_MINUTES = 5

        /** 2008-01-01T00:00:00Z en segundos Unix. */
        private const val PUMP_EPOCH_UNIX_SECONDS = 1199145600L
        private val MIN_READING_AGE: Duration = Duration.ofMinutes(-2)
        private val MIN_LEARN_AGE: Duration = Duration.ofMinutes(-5)
        private val MAX_KEPT_READING_AGE: Duration = Duration.ofMinutes(30)
        private val FUTURE_MARGIN: Duration = Duration.ofMinutes(2)
        private val SLOPE_WINDOW: Duration = Duration.ofMinutes(15)
        private const val MIN_SLOPE_MINUTES = 4.0
        private const val OFFSET_STEP_SECONDS = 15 * 60L
        private const val MAX_PUMP_TREND_MGDL_PER_MIN = 10.0
        private val MAX_PUMP_OFFSET: Duration = Duration.ofHours(14)
        private val OFFSET_MAX_AGE: Duration = Duration.ofHours(1)
    }
}
