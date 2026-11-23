package com.istanbul.weatherintel.data.solar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sunrise / sunset calculation tests.
 *
 * Reference values are the published local times for Istanbul (UTC+3, no
 * daylight saving) taken from an independent ephemeris:
 *
 *   22 Nov 2026  sunrise 07:59, sunset 17:40, daylight 9 h 40 min 58 s
 *   30 Nov 2026  sunrise 08:08, sunset 17:36, daylight 9 h 28 min 30 s
 *   21 Dec 2026  sunrise 08:25, sunset 17:38, daylight 9 h 13 min 22 s
 *
 * The simplified NOAA sunrise equation used here is accurate to roughly a
 * minute (it omits nutation and the higher-order refraction corrections), so
 * the assertions allow a two-minute tolerance.
 *
 * The calculation is intentionally a *local* computation: neither provider
 * publishes sunrise or sunset, so the app must never present these as
 * provider observations.
 */
class SunTimesTest {

    private val istanbulLat = 41.0082
    private val istanbulLon = 28.9784

    /** Tolerance for the simplified sunrise equation. */
    private val toleranceMs = 2 * 60_000L

    private val local = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK)
        .withZone(ZoneId.of("Europe/Istanbul"))

    private fun localText(millis: Long?): String =
        millis?.let { local.format(Instant.ofEpochMilli(it)) } ?: "--"

    /** Asserts the computed instant is within [toleranceMs] of the published one. */
    private fun assertCloseToPublished(
        actual: Long?,
        publishedLocal: String,
        label: String,
    ) {
        assertNotNull("$label must be calculable", actual)
        val expected = Instant.parse(publishedLocal).toEpochMilli()
        val delta = Math.abs(actual!! - expected)
        assertTrue(
            "$label: computed ${localText(actual)} differs from published " +
                "$publishedLocal by ${delta / 1000}s",
            delta <= toleranceMs,
        )
    }

    @Test
    fun `sunrise for 22 November 2026 matches the published local time`() {
        val sunrise = SunTimes.sunriseUtc(
            LocalDate.of(2026, 11, 22), istanbulLat, istanbulLon,
        )
        assertCloseToPublished(sunrise, "2026-11-22T04:59:00Z", "sunrise")
    }

    @Test
    fun `sunset for 22 November 2026 matches the published local time`() {
        val sunset = SunTimes.sunsetUtc(
            LocalDate.of(2026, 11, 22), istanbulLat, istanbulLon,
        )
        assertCloseToPublished(sunset, "2026-11-22T14:40:00Z", "sunset")
    }

    @Test
    fun `daylight length for 22 November 2026 is 9 hours 41 minutes`() {
        val minutes = SunTimes.daylightMinutes(
            LocalDate.of(2026, 11, 22), istanbulLat, istanbulLon,
        )
        assertNotNull(minutes)
        assertEquals(581, minutes)
    }

    @Test
    fun `sunrise and sunset for 30 November 2026 match the published times`() {
        val date = LocalDate.of(2026, 11, 30)
        assertCloseToPublished(
            SunTimes.sunriseUtc(date, istanbulLat, istanbulLon),
            "2026-11-30T05:08:00Z", "sunrise",
        )
        assertCloseToPublished(
            SunTimes.sunsetUtc(date, istanbulLat, istanbulLon),
            "2026-11-30T14:36:00Z", "sunset",
        )
    }

    @Test
    fun `sunrise and sunset around the winter solstice match the published times`() {
        val date = LocalDate.of(2026, 12, 21)
        assertCloseToPublished(
            SunTimes.sunriseUtc(date, istanbulLat, istanbulLon),
            "2026-12-21T05:25:00Z", "sunrise",
        )
        assertCloseToPublished(
            SunTimes.sunsetUtc(date, istanbulLat, istanbulLon),
            "2026-12-21T14:38:00Z", "sunset",
        )
    }

    @Test
    fun `solar noon falls between sunrise and sunset`() {
        val date = LocalDate.of(2026, 11, 22)
        val rise = SunTimes.sunriseUtc(date, istanbulLat, istanbulLon)!!
        val noon = SunTimes.solarNoonUtc(date, istanbulLat, istanbulLon)!!
        val set = SunTimes.sunsetUtc(date, istanbulLat, istanbulLon)!!
        assertTrue(noon > rise)
        assertTrue(set > noon)
    }

    @Test
    fun `the sun does not rise at the pole`() {
        // Midsummer at the north pole: the sun stays up, so there is no sunrise.
        assertNull(SunTimes.sunriseUtc(LocalDate.of(2026, 6, 21), 89.9, 0.0))
        assertNull(SunTimes.sunsetUtc(LocalDate.of(2026, 6, 21), 89.9, 0.0))
    }

    @Test
    fun `epoch millis are returned in UTC`() {
        val sunrise = SunTimes.sunriseUtc(
            LocalDate.of(2026, 11, 22), istanbulLat, istanbulLon,
        )!!
        val utc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.UK)
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(sunrise))
        assertEquals("2026-11-22 04:59", utc)
        // And the local Istanbul rendering is the published 07:59.
        assertEquals("22 Nov 07:59", localText(sunrise))
    }
}
