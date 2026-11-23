package com.istanbul.weatherintel.data.solar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Astronomical sunrise / sunset for a fixed coordinate.
 *
 * IMPORTANT: neither provider publishes sunrise or sunset. The verified Met
 * Office Global Spot parameter sets (18 hourly / 21 three-hourly / 41 daily)
 * contain no such field, and MGM's payloads have none either. The times below
 * are therefore COMPUTED locally with the NOAA sunrise equation and must always
 * be presented as computed - never as a provider observation.
 *
 * Reference: NOAA Solar Calculator / "sunrise equation", with the standard
 * -0.833 degree solar-disc correction (refraction + solar radius).
 */
object SunTimes {

    private const val DEG = PI / 180.0

    /** Sunrise for the given date at the coordinate, in epoch millis UTC. */
    fun sunriseUtc(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): Long? = event(date, latitude, longitude, rising = true)

    /** Sunset for the given date at the coordinate, in epoch millis UTC. */
    fun sunsetUtc(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): Long? = event(date, latitude, longitude, rising = false)

    /** Local solar noon for the given date, in epoch millis UTC. */
    fun solarNoonUtc(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): Long? {
        val jd = julianDate(date)
        val jStar = julianCycle(jd, longitude)
        val meanAnomaly = (357.5291 + 0.98560028 * jStar).mod(360.0)
        val center = 1.9148 * sin(meanAnomaly * DEG) +
            0.02 * sin(2 * meanAnomaly * DEG) +
            0.0003 * sin(3 * meanAnomaly * DEG)
        val eclipticLongitude = (meanAnomaly + center + 180.0 + 102.9372).mod(360.0)
        val transit = 2451545.0 + jStar +
            0.0053 * sin(meanAnomaly * DEG) -
            0.0069 * sin(2 * eclipticLongitude * DEG)
        return julianToEpochMillis(transit)
    }

    /** Length of daylight in minutes, or null when the sun never rises/sets. */
    fun daylightMinutes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): Int? {
        val rise = sunriseUtc(date, latitude, longitude) ?: return null
        val set = sunsetUtc(date, latitude, longitude) ?: return null
        return ((set - rise) / 60_000L).toInt()
    }

    private fun event(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        rising: Boolean,
    ): Long? {
        val jd = julianDate(date)
        val jStar = julianCycle(jd, longitude)

        // Mean anomaly and equation of centre.
        val meanAnomaly = (357.5291 + 0.98560028 * jStar).mod(360.0)
        val center = 1.9148 * sin(meanAnomaly * DEG) +
            0.02 * sin(2 * meanAnomaly * DEG) +
            0.0003 * sin(3 * meanAnomaly * DEG)

        // True ecliptic longitude and declination.
        val eclipticLongitude = (meanAnomaly + center + 180.0 + 102.9372).mod(360.0)
        val sinDeclination = sin(eclipticLongitude * DEG) * sin(23.4397 * DEG)
        val cosDeclination = sqrt(1.0 - sinDeclination * sinDeclination)

        // Hour angle at sunrise/sunset. When the ratio is outside [-1, 1] the
        // sun does not cross the horizon on that day (polar day / polar night).
        val numerator = sin(-0.833 * DEG) - sin(latitude * DEG) * sinDeclination
        val denominator = cos(latitude * DEG) * cosDeclination
        if (denominator == 0.0) return null
        val cosHourAngle = numerator / denominator
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
        val hourAngle = acos(cosHourAngle) / DEG

        val transit = 2451545.0 + jStar +
            0.0053 * sin(meanAnomaly * DEG) -
            0.0069 * sin(2 * eclipticLongitude * DEG)

        val julianEvent = if (rising) {
            transit - hourAngle / 360.0
        } else {
            transit + hourAngle / 360.0
        }
        return julianToEpochMillis(julianEvent)
    }

    /** Julian date at 00:00 UTC for a calendar date. */
    private fun julianDate(date: LocalDate): Double {
        var year = date.year
        val month = date.monthValue
        if (month <= 2) {
            year -= 1
        }
        val adjustedMonth = if (month > 2) month else month + 12
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) +
            floor(30.6001 * (adjustedMonth + 1)) +
            date.dayOfMonth + b - 1524.5
    }

    /**
     * Julian cycle and mean solar time.
     *
     * The cycle number is rounded UP to the next whole day, which is what
     * anchors the calculation to the requested calendar date. Using a floor
     * instead shifts every event back by one day.
     */
    private fun julianCycle(jd: Double, longitude: Double): Double {
        val n = ceil(jd - 2451545.0 + 0.0008)
        return n - longitude / 360.0
    }

    /** Converts a Julian date to epoch millis UTC. */
    private fun julianToEpochMillis(jd: Double): Long {
        val z = floor(jd + 0.5)
        val f = (jd + 0.5) - z
        val alpha = floor((z - 1867216.25) / 36524.25)
        val a = z + 1 + alpha - floor(alpha / 4.0)
        val b = a + 1524
        val c = floor((b - 122.1) / 365.25)
        val d = floor(365.25 * c)
        val e = floor((b - d) / 30.6001)

        val day = b - d - floor(30.6001 * e)
        val month = if (e < 14) e - 1 else e - 13
        val year = if (month > 2) c - 4716 else c - 4715

        val date = LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        val seconds = (f * 86_400.0).roundToLong()
        return LocalDateTime.of(date, java.time.LocalTime.MIDNIGHT)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli() + seconds * 1000L
    }
}
