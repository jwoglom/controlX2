package com.jwoglom.controlx2.sync.xdrip

import android.content.Context
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class TimedPumpValue<T>(
    var value: T,
    var receivedAt: Instant
)

private data class LatestPumpSnapshot(
    var batteryPercent: TimedPumpValue<Int>? = null,
    var iobUnits: TimedPumpValue<Double>? = null,
    var cartridgeUnits: TimedPumpValue<Int>? = null,
    var basalUnitsPerHour: TimedPumpValue<Double>? = null,
    var sgvMgdl: TimedPumpValue<Int>? = null,
)

internal sealed class DispatchEvent {
    data class PumpBattery(val percent: Int) : DispatchEvent()
    data class PumpIob(val units: Double) : DispatchEvent()
    data class PumpReservoir(val units: Int) : DispatchEvent()
    data class PumpBasal(val unitsPerHour: Double) : DispatchEvent()
    data class CgmSgv(val mgdl: Int) : DispatchEvent()
    data class TreatmentInitiated(val bolusId: Int, val status: String) : DispatchEvent()
    data class TreatmentStatus(val bolusId: Int, val requestedVolumeMilli: Long, val status: String, val timestamp: Instant) : DispatchEvent()
    data object Other : DispatchEvent()
}

class XdripMessageDispatcher(
    private val broadcaster: XdripBroadcaster,
    private val configProvider: () -> XdripSyncConfig,
    private val nowProvider: () -> Instant = { Instant.now() }
) {
    constructor(context: Context) : this(
        broadcaster = XdripBroadcastSender(context),
        configProvider = { XdripSyncConfig.load(Prefs(context).prefs()) }
    )

    private val latestPumpSnapshot = LatestPumpSnapshot()

    fun onReceiveMessage(message: Message) {
        onEvent(message.toDispatchEvent())
    }

    internal fun onEvent(event: DispatchEvent) {
        val config = configProvider()
        if (!config.enabled) return

        val receivedAt = nowProvider()
        val category = updateSnapshot(event, receivedAt)

        if (config.sendCgmSgv && event is DispatchEvent.CgmSgv) {
            val sgvPayload = JSONArray().put(
                JSONObject().apply {
                    put("sgv", event.mgdl)
                    put("date", receivedAt.toEpochMilli())
                    put("dateString", receivedAt.toString())
                }
            ).toString()
            broadcaster.sendSgv(sgvPayload, config.cgmSgvMinimumIntervalSeconds)
        }

        if (config.sendPumpDeviceStatus && category == StatusCategory.PUMP_STATUS) {
            val deviceStatusPayload = JSONObject().apply {
                put("created_at", receivedAt.toString())
                put("uploaderBattery", latestPumpSnapshot.batteryPercent?.value)
                put("pump", JSONObject().apply {
                    put("battery", JSONObject().apply { put("percent", latestPumpSnapshot.batteryPercent?.value) })
                    put("iob", JSONObject().apply { put("bolusiob", latestPumpSnapshot.iobUnits?.value) })
                    put("reservoir", latestPumpSnapshot.cartridgeUnits?.value)
                    put("basal", latestPumpSnapshot.basalUnitsPerHour?.value)
                })
                put("cgm", JSONObject().apply {
                    put("sgv", latestPumpSnapshot.sgvMgdl?.value)
                })
                put("controlx2ReceivedAt", JSONObject().apply {
                    put("battery", latestPumpSnapshot.batteryPercent?.receivedAt?.toString())
                    put("iob", latestPumpSnapshot.iobUnits?.receivedAt?.toString())
                    put("reservoir", latestPumpSnapshot.cartridgeUnits?.receivedAt?.toString())
                    put("basal", latestPumpSnapshot.basalUnitsPerHour?.receivedAt?.toString())
                    put("sgv", latestPumpSnapshot.sgvMgdl?.receivedAt?.toString())
                })
            }.toString()
            broadcaster.sendDeviceStatus(deviceStatusPayload, config.pumpDeviceStatusMinimumIntervalSeconds)
        }

        if (config.sendTreatments && category == StatusCategory.TREATMENT) {
            val treatmentPayload = when (event) {
                is DispatchEvent.TreatmentInitiated -> JSONArray().put(
                    JSONObject().apply {
                        put("eventType", "Bolus")
                        put("created_at", receivedAt.toString())
                        put("notes", "ControlX2 bolus initiated bolusId=${event.bolusId} status=${event.status}")
                    }
                ).toString()

                is DispatchEvent.TreatmentStatus -> JSONArray().put(
                    JSONObject().apply {
                        put("eventType", "Bolus")
                        put("created_at", event.timestamp.toString())
                        put("insulin", InsulinUnit.from1000To1(event.requestedVolumeMilli))
                        put("notes", "ControlX2 bolus status bolusId=${event.bolusId} status=${event.status}")
                    }
                ).toString()

                else -> null
            }
            if (treatmentPayload != null) {
                broadcaster.sendTreatments(
                    treatmentsJsonString = treatmentPayload,
                    minimumIntervalSeconds = config.treatmentsMinimumIntervalSeconds,
                    alsoSendNewFood = true
                )
            }
        }

        if (config.sendStatusLine && category == StatusCategory.PUMP_STATUS) {
            val statusline = buildString {
                append("Pump")
                latestPumpSnapshot.sgvMgdl?.value?.let { append(" SGV:$it") }
                latestPumpSnapshot.iobUnits?.value?.let { append(" IOB:${twoDecimalPlaces(it)}u") }
                latestPumpSnapshot.cartridgeUnits?.value?.let { append(" Cart:${it}u") }
                latestPumpSnapshot.basalUnitsPerHour?.value?.let { append(" Basal:${twoDecimalPlaces(it)}u/h") }
                latestPumpSnapshot.batteryPercent?.value?.let { append(" Batt:${it}%") }
            }
            broadcaster.sendExternalStatusline(statusline, config.statusLineMinimumIntervalSeconds)
        }
    }

    private fun updateSnapshot(event: DispatchEvent, receivedAt: Instant): StatusCategory {
        return when (event) {
            is DispatchEvent.PumpBattery -> {
                latestPumpSnapshot.batteryPercent = TimedPumpValue(event.percent, receivedAt)
                StatusCategory.PUMP_STATUS
            }
            is DispatchEvent.PumpIob -> {
                latestPumpSnapshot.iobUnits = TimedPumpValue(event.units, receivedAt)
                StatusCategory.PUMP_STATUS
            }
            is DispatchEvent.PumpReservoir -> {
                latestPumpSnapshot.cartridgeUnits = TimedPumpValue(event.units, receivedAt)
                StatusCategory.PUMP_STATUS
            }
            is DispatchEvent.PumpBasal -> {
                latestPumpSnapshot.basalUnitsPerHour = TimedPumpValue(event.unitsPerHour, receivedAt)
                StatusCategory.PUMP_STATUS
            }
            is DispatchEvent.CgmSgv -> {
                latestPumpSnapshot.sgvMgdl = TimedPumpValue(event.mgdl, receivedAt)
                StatusCategory.PUMP_STATUS
            }
            is DispatchEvent.TreatmentInitiated,
            is DispatchEvent.TreatmentStatus -> StatusCategory.TREATMENT
            is DispatchEvent.Other -> StatusCategory.OTHER
        }
    }

    private fun Message.toDispatchEvent(): DispatchEvent {
        return when (this) {
            is CurrentBatteryAbstractResponse -> DispatchEvent.PumpBattery(batteryPercent)
            is ControlIQIOBResponse -> DispatchEvent.PumpIob(InsulinUnit.from1000To1(pumpDisplayedIOB))
            is InsulinStatusResponse -> DispatchEvent.PumpReservoir(currentInsulinAmount)
            is CurrentBasalStatusResponse -> DispatchEvent.PumpBasal(InsulinUnit.from1000To1(currentBasalRate))
            is CurrentEGVGuiDataResponse -> DispatchEvent.CgmSgv(cgmReading)
            is InitiateBolusResponse -> DispatchEvent.TreatmentInitiated(bolusId, statusType.toString())
            is CurrentBolusStatusResponse -> DispatchEvent.TreatmentStatus(
                bolusId = bolusId,
                requestedVolumeMilli = requestedVolume,
                status = status.toString(),
                timestamp = timestampInstant
            )
            else -> DispatchEvent.Other
        }
    }

    private enum class StatusCategory {
        PUMP_STATUS,
        TREATMENT,
        OTHER
    }
}
