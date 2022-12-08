package com.jwoglom.wearx2.shared.util

import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.temporal.TemporalField
import java.time.temporal.TemporalUnit


fun shortTimeAgo(time: Instant): String {
    val now = Instant.now()
    val diff = Duration.between(time, now)
    var ret = ""
    if (diff.toDays() > 0) {
        ret += "${diff.toDays()}d"
    }
    if (diff.toHours() % 24 > 0) {
        ret += "${diff.toHours()%24}h"
    }
    if (diff.toMinutes() % 60 > 0 && diff.toDays() == 0L) {
        ret += "${String.format("%02d", diff.toMinutes()%60)}m"
    }
    if (diff.getSeconds() < 60 && diff.getSeconds() > -60) {
        return "now"
    } else if (time.isBefore(now)) {
        return "$ret ago"
    } else {
        return "in $ret"
    }
}

fun shortTime(time: Instant): String {
    val zoned = time.atZone(UTC) // UTC, not the system timezone, for some reason. Perhaps TZ information is already encoded in the Tandem timestamps?
    val hr = when(zoned.hour) {
        0 -> 12
        else -> zoned.hour % 12
    }
    val ampm = when(zoned.hour < 12) {
        true -> "am"
        false -> "pm"
    }
    return "${hr}:${String.format("%02d", zoned.minute)}${ampm}"
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