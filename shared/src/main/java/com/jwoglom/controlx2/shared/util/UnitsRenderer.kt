package com.jwoglom.controlx2.shared.util

import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoField
import java.time.temporal.TemporalField
import java.util.*
import kotlin.math.abs
import kotlin.time.DurationUnit

fun pumpTimeToLocalTz(time: Instant): Instant {
    val offsetSeconds = TimeZone.getDefault().getOffset(Date().time) / 1000
    return time.minusSeconds(offsetSeconds.toLong())
}

fun shortTimeAgo(
    time: Instant,
    prefix: String = "in",
    suffix: String = "ago",
    nowThresholdSeconds: Int = 60,
): String {
    val now = Instant.now()
    val diff = Duration.between(time, now)
    var ret = ""
    if (diff.toDays() != 0L) {
        ret += "${abs(diff.toDays())}d"
    }
    if (diff.toHours() % 24 != 0L) {
        ret += "${abs(diff.toHours()%24)}h"
    }
    if (diff.toMinutes() % 60 != 0L && diff.toDays() == 0L) {
        ret += "${String.format("%d", abs(diff.toMinutes())%60)}m"
    }
    if (diff.seconds < nowThresholdSeconds && diff.seconds > (-1 * nowThresholdSeconds)) {
        return "now"
    } else if (diff.toMinutes() == 0L) {
        ret += "${String.format("%d", abs(diff.seconds))}s"
    }

    return if (time.isBefore(now)) {
        "$ret $suffix"
    } else {
        "$prefix $ret"
    }
}

fun shortTime(time: Instant): String {
//    val zoned = time.atZone(UTC) // UTC, not the system timezone, for some reason. Perhaps TZ information is already encoded in the Tandem timestamps?
//
//    val offsetSeconds = TimeZone.getDefault().getOffset(Date().time) / 1000
//    val now = Instant.now().plusSeconds(offsetSeconds.toLong())
    val zoned = time.atZone(ZoneId.systemDefault())
    val now = Instant.now()
    val diff = Duration.between(time, now)

    val date = when (diff.toDays() >= 1 || diff.toDays() <= -1) {
        true -> "${zoned.month.value}/${zoned.dayOfMonth} "
        else -> ""
    }

    val m = when (date.length) {
        0 -> "m"
        else -> ""
    }

    val hr = when (zoned.hour) {
        0, 12 -> 12
        else -> zoned.hour % 12
    }
    val ampm = when (zoned.hour < 12) {
        true -> "a${m}"
        false -> "p${m}"
    }
    return "${date}${hr}:${String.format("%02d", zoned.minute)}${ampm}"
}

fun twoDecimalPlaces(decimal: Double): String {
    return String.format("%.2f", decimal)
}

fun oneDecimalPlace(decimal: Double): String {
    return String.format("%.1f", decimal)
}

fun twoDecimalPlaces1000Unit(insulin1000Units: Long): String {
    return twoDecimalPlaces(InsulinUnit.from1000To1(insulin1000Units))
}

fun oneDecimalPlace1000Unit(insulin1000Units: Long): String {
    return oneDecimalPlace(InsulinUnit.from1000To1(insulin1000Units))
}

fun snakeCaseToSpace(str: String): String {
    return str.replace("_", " ")
}

fun firstLetterCapitalized(str: String): String {
    return "${str.substring(0, 1).uppercase()}${str.substring(1)}"
}