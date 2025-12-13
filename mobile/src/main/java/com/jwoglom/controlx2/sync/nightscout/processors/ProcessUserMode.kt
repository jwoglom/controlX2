package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process Control-IQ user mode changes for Nightscout upload
 *
 * Converts mode change records (Sleep, Exercise, etc.) to Nightscout treatments
 */
class ProcessUserMode(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.USER_MODE

    override fun supportedTypeIds(): Set<Int> {
        // Try to find user mode-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.UserModeChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.SleepModeActivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.SleepModeDeactivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.ExerciseModeActivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.ExerciseModeDeactivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQModeChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.ProfileChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.PersonalProfileChangeHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("User mode HistoryLog class not found: $className")
                null
            }
        }.toSet()
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
                modeChangeToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert mode change seqId=${item.seqId}")
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

    private fun modeChangeToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val modeClass = parsed.javaClass

        // Extract mode change data using reflection
        val modeData = extractModeData(modeClass, parsed)

        // Choose appropriate event type based on mode
        val eventType = when {
            modeData.isExerciseMode -> "Exercise"
            else -> "Note"
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = eventType,
            timestamp = item.pumpTime,
            seqId = item.seqId,
            duration = modeData.duration,
            reason = modeData.reason,
            notes = modeData.notes
        )
    }

    private data class ModeData(
        val isExerciseMode: Boolean,
        val duration: Int?,
        val reason: String,
        val notes: String
    )

    private fun extractModeData(clazz: Class<*>, obj: Any): ModeData {
        try {
            // Determine mode type from class name
            val className = clazz.simpleName
            val isSleep = className.contains("Sleep", ignoreCase = true)
            val isExercise = className.contains("Exercise", ignoreCase = true)
            val isActivated = className.contains("Activated", ignoreCase = true) ||
                             className.contains("Started", ignoreCase = true)
            val isDeactivated = className.contains("Deactivated", ignoreCase = true) ||
                               className.contains("Stopped", ignoreCase = true) ||
                               className.contains("Ended", ignoreCase = true)

            // Try to get mode value
            val modeValue = tryGetField<Int>(clazz, obj, "mode")
                ?: tryGetField<Int>(clazz, obj, "userMode")
                ?: tryGetField<String>(clazz, obj, "mode")
                ?: tryGetField<String>(clazz, obj, "userMode")

            // Try to get duration
            val durationSeconds = tryGetField<Int>(clazz, obj, "duration")
                ?: tryGetField<Long>(clazz, obj, "duration")?.toInt()
                ?: tryGetField<Int>(clazz, obj, "durationSeconds")
                ?: tryGetField<Long>(clazz, obj, "durationSeconds")?.toInt()

            val duration = durationSeconds?.let { it / 60 } // Convert seconds to minutes

            // Try to get previous/new mode for mode changes
            val previousMode = tryGetField<Int>(clazz, obj, "previousMode")
                ?: tryGetField<String>(clazz, obj, "previousMode")

            val newMode = tryGetField<Int>(clazz, obj, "newMode")
                ?: tryGetField<String>(clazz, obj, "newMode")

            // Determine mode name
            val modeName = when {
                isSleep -> "Sleep Mode"
                isExercise -> "Exercise Mode"
                newMode != null -> "Mode: $newMode"
                modeValue != null -> "Mode: $modeValue"
                else -> "User Mode"
            }

            // Build reason
            val reason = buildString {
                append(modeName)
                when {
                    isActivated -> append(" activated")
                    isDeactivated -> append(" deactivated")
                    previousMode != null && newMode != null -> {
                        append(" changed from $previousMode to $newMode")
                    }
                }
            }

            // Build notes with all available info
            val notes = buildString {
                append(reason)

                duration?.let {
                    append(", duration: ${it} minutes")
                }

                val targetGlucose = tryGetField<Int>(clazz, obj, "targetGlucose")
                    ?: tryGetField<Int>(clazz, obj, "targetBG")
                targetGlucose?.let {
                    append(", target: ${it}mg/dL")
                }

                val profileId = tryGetField<Int>(clazz, obj, "profileId")
                profileId?.let {
                    append(", profile: $it")
                }
            }

            return ModeData(
                isExerciseMode = isExercise,
                duration = duration,
                reason = reason,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting mode data")
            return ModeData(
                isExerciseMode = false,
                duration = null,
                reason = "User mode change",
                notes = "User mode change (details unavailable)"
            )
        }
    }

    private inline fun <reified T> tryGetField(clazz: Class<*>, obj: Any, fieldName: String): T? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(obj)
            if (value is T) value else null
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to access field: $fieldName")
            null
        }
    }
}
