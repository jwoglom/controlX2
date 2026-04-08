package com.jwoglom.controlx2.sync.nightscout

import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Conversion policy for Nightscout timestamps:
 * - [HistoryLogItem.pumpTime] is treated as pump-local wall time in [pumpTimeZone].
 * - Nightscout outbound payloads must include timezone-aware RFC3339 strings (UTC `Z`).
 * - Epoch millis, ISO string, and utcOffset must all come from the same [Instant].
 */
object NightscoutTimestampPolicy {
    /**
     * Authoritative zone for converting [HistoryLogItem.pumpTime] to an absolute instant.
     *
     * We persist pumpTime as [LocalDateTime] in the app's default zone when parsing history logs,
     * so we use that same zone here to recover the original instant consistently.
     */
    val pumpTimeZone: ZoneId
        get() = ZoneId.systemDefault()

    private val sampleLogged = AtomicBoolean(false)

    fun resetSampleLoggingForSyncRun() {
        sampleLogged.set(false)
    }

    fun fromPumpTime(
        pumpTime: LocalDateTime,
        sampleContext: String? = null
    ): NightscoutTimestamp {
        return toNightscoutTimestamp(
            timestamp = pumpTime,
            zoneId = pumpTimeZone,
            sampleContext = sampleContext
        )
    }

    internal fun logSampleIfNeeded(sampleContext: String?, value: NightscoutTimestamp) {
        if (sampleContext != null && sampleLogged.compareAndSet(false, true)) {
            Timber.i(
                "Nightscout timestamp sample (%s): epoch=%d iso=%s utcOffsetMin=%d zone=%s",
                sampleContext,
                value.epochMillis,
                value.isoInstant,
                value.utcOffsetMinutes,
                value.zoneId.id
            )
        }
    }
}

/**
 * Nightscout-ready timestamp fields derived from one absolute instant.
 */
data class NightscoutTimestamp(
    val instant: Instant,
    val epochMillis: Long,
    val isoInstant: String,
    val utcOffsetMinutes: Int,
    val zoneId: ZoneId
)

fun toNightscoutTimestamp(
    timestamp: LocalDateTime,
    zoneId: ZoneId = NightscoutTimestampPolicy.pumpTimeZone,
    sampleContext: String? = null
): NightscoutTimestamp {
    val zonedDateTime = timestamp.atZone(zoneId)
    val instant = zonedDateTime.toInstant()
    val nightscoutTimestamp = NightscoutTimestamp(
        instant = instant,
        epochMillis = instant.toEpochMilli(),
        isoInstant = DateTimeFormatter.ISO_INSTANT.format(instant),
        utcOffsetMinutes = zonedDateTime.offset.totalSeconds / 60,
        zoneId = zoneId
    )
    NightscoutTimestampPolicy.logSampleIfNeeded(sampleContext, nightscoutTimestamp)
    return nightscoutTimestamp
}
