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
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import timber.log.Timber

/**
 * Process basal rate deliveries for Nightscout upload
 *
 * Correlates TempRateActivated and TempRateCompleted events to produce
 * accurate basal segments with correct durations, including early cancellations.
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
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[TempRateActivatedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[TempRateCompletedHistoryLog::class.java]
        )
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        val treatments = mutableListOf<NightscoutTreatment>()

        // Separate logs by type for temp basal correlation
        val completionsByTempRateId = mutableMapOf<Int, Pair<HistoryLogItem, TempRateCompletedHistoryLog>>()
        val activations = mutableListOf<Pair<HistoryLogItem, TempRateActivatedHistoryLog>>()
        val otherLogs = mutableListOf<HistoryLogItem>()

        for (item in logs) {
            try {
                val parsed = item.parse()
                when (parsed) {
                    is TempRateActivatedHistoryLog -> activations.add(item to parsed)
                    is TempRateCompletedHistoryLog -> completionsByTempRateId[parsed.tempRateId] = item to parsed
                    else -> otherLogs.add(item)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse basal log seqId=${item.seqId}")
            }
        }

        // Process temp rate activations with correlated completions
        for ((item, activated) in activations) {
            val completion = completionsByTempRateId[activated.tempRateId]
            val originalDurationMinutes = (activated.duration * 60).toInt()

            val actualDurationMinutes = if (completion != null) {
                val (_, completed) = completion
                val timeLeftMinutes = (completed.timeLeft * 60).toInt()
                maxOf(originalDurationMinutes - timeLeftMinutes, 0)
            } else {
                originalDurationMinutes
            }

            val notes = buildString {
                append("Temp basal ${activated.percent}%")
                if (completion != null && completion.second.timeLeft > 0) {
                    append(" (canceled early, ran ${actualDurationMinutes}min of ${originalDurationMinutes}min)")
                } else {
                    append(" for ${originalDurationMinutes}min")
                }
            }

            treatments.add(NightscoutTreatment.fromTimestamp(
                eventType = "Temp Basal",
                timestamp = item.pumpTime,
                seqId = item.seqId,
                rate = null,
                absolute = null,
                duration = actualDurationMinutes,
                notes = notes
            ))
        }

        // Process BasalDelivery and BasalRateChange logs
        for (item in otherLogs) {
            try {
                basalToNightscoutTreatment(item)?.let { treatments.add(it) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert basal seqId=${item.seqId}")
            }
        }

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No treatments to upload")
            return 0
        }

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
            else -> {
                Timber.w("Unexpected basal type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
