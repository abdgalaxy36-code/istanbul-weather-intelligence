package com.istanbul.weatherintel.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All display timestamps use Europe/Istanbul, as required by the spec. */
object IstanbulTimes {
    val zone: ZoneId = ZoneId.of("Europe/Istanbul")

    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)
    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val dayOnly = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'", Locale.UK)

    fun format(millis: Long?): String {
        if (millis == null || millis <= 0L) return "Not available"
        return Instant.ofEpochMilli(millis).atZone(zone).format(dateTime)
    }

    fun time(millis: Long?): String {
        if (millis == null || millis <= 0L) return "--"
        return Instant.ofEpochMilli(millis).atZone(zone).format(timeOnly)
    }

    fun day(millis: Long?): String {
        if (millis == null || millis <= 0L) return "--"
        return Instant.ofEpochMilli(millis).atZone(zone).format(dayOnly)
    }

    fun utc(millis: Long?): String {
        if (millis == null || millis <= 0L) return "--"
        return Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).format(iso)
    }

    fun relative(millis: Long?): String {
        if (millis == null || millis <= 0L) return "never"
        val diff = System.currentTimeMillis() - millis
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60} h ago"
            else -> "${minutes / 1440} d ago"
        }
    }
}

