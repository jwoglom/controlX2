package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusWizardHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.MealMarkerHistoryLog
import timber.log.Timber

/**
 * Process carb entries for Nightscout upload
 *
 * Converts BolusWizardHistoryLog and MealMarkerHistoryLog to Nightscout "Carb Correction" treatments
 */
class ProcessCarb(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARB

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[BolusWizardHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[MealMarkerHistoryLog::class.java]
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
                carbToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert carb seqId=${item.seqId}")
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

    private fun carbToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        
        return when (parsed) {
            is BolusWizardHistoryLog -> {
                if (parsed.carbAmount <= 0) return null
                
                NightscoutTreatment.fromTimestamp(
                    eventType = "Carb Correction",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    carbs = parsed.carbAmount.toDouble(),
                    notes = "Bolus Wizard"
                )
            }
            is MealMarkerHistoryLog -> {
                if (parsed.carbs <= 0) return null
                
                NightscoutTreatment.fromTimestamp(
                    eventType = "Carb Correction",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    carbs = parsed.carbs.toDouble(),
                    notes = "Meal Marker"
                )
            }
            else -> {
                Timber.w("Unexpected carb type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
