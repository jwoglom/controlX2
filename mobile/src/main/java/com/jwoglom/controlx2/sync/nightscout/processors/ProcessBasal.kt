package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import timber.log.Timber

/**
 * Process basal rate deliveries for Nightscout upload
 *
 * Converts basal delivery records to Nightscout "Temp Basal" treatments
 */
class ProcessBasal(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BASAL

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[BasalDeliveryHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[BasalRateChangeHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[TempRateActivatedHistoryLog::class.java]
        )
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Convert history logs to Nightscout treatments
        val treatments = logs.mapNotNull { item ->
            try {
                basalToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert basal seqId=${item.seqId}")
                null
            }
        }

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No treatments to upload")
            return 0
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadTreatments(treatments)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun basalToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        return when (parsed) {
            is BasalDeliveryHistoryLog -> {
                val commandedRate = parsed.commandedRate / 1000.0
                val notes = buildString {
                    append("Basal delivery")
                    append(", commanded rate: ${commandedRate}U/hr")
                    if (parsed.profileBasalRate > 0) {
                        append(", profile: ${parsed.profileBasalRate / 1000.0}U/hr")
                    }
                    if (parsed.algorithmRate > 0) {
                        append(", algorithm: ${parsed.algorithmRate / 1000.0}U/hr")
                    }
                    if (parsed.tempRate > 0) {
                        append(", temp: ${parsed.tempRate / 1000.0}U/hr")
                    }
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Temp Basal",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    rate = commandedRate,
                    absolute = commandedRate,
                    duration = null,
                    notes = notes
                )
            }
            is BasalRateChangeHistoryLog -> {
                val notes = buildString {
                    append("Basal rate change (type: ${parsed.changeTypeId})")
                    append(", new rate: ${parsed.commandBasalRate}U/hr")
                    append(", profile rate: ${parsed.baseBasalRate}U/hr")
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Temp Basal",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    rate = parsed.commandBasalRate.toDouble(),
                    absolute = parsed.commandBasalRate.toDouble(),
                    duration = null,
                    notes = notes
                )
            }
            is TempRateActivatedHistoryLog -> {
                val durationMinutes = (parsed.duration * 60).toInt()
                val notes = "Temp basal ${parsed.percent}% for ${parsed.duration}hr"

                NightscoutTreatment.fromTimestamp(
                    eventType = "Temp Basal",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    rate = null,
                    absolute = null,
                    duration = durationMinutes,
                    notes = notes
                )
            }
            else -> {
                Timber.w("Unexpected basal type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
