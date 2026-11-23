package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.model.Availability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests for the Met Office Global Spot site-specific API (v1.0.2).
 * The payload shape follows the official GeoJSON response format documented by
 * the Met Office; no values are invented here.
 */
class MetOfficeParserTest {

    private val hourlyJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [28.9784, 41.0082, 39.0] },
              "properties": {
                "location": { "name": "Istanbul", "country": "TR" },
                "requestPointDistance": 12.3,
                "modelRunDate": "2026-11-22T00:00:00Z",
                "timeSeries": [
                  {
                    "time": "2026-11-22T06:00:00Z",
                    "screenTemperature": 13.2,
                    "feelsLikeTemperature": 10.8,
                    "screenRelativeHumidity": 78,
                    "windSpeed10m": 6.2,
                    "windDirectionFrom10m": 251,
                    "windGustSpeed10m": 10.4,
                    "visibility": 20000,
                    "mslp": 101840,
                    "uvIndex": 1,
                    "significantWeatherCode": 4,
                    "precipitationRate": 0,
                    "totalPrecipAmount": 0,
                    "probOfPrecipitation": 5
                  },
                  {
                    "time": "2026-11-22T09:00:00Z",
                    "screenTemperature": 15.1,
                    "feelsLikeTemperature": 12.4,
                    "screenRelativeHumidity": 72,
                    "windSpeed10m": 5.1,
                    "windDirectionFrom10m": 262,
                    "windGustSpeed10m": 9.9,
                    "visibility": 24000,
                    "mslp": 101900,
                    "uvIndex": 2,
                    "significantWeatherCode": 6,
                    "precipitationRate": null,
                    "totalPrecipAmount": null,
                    "probOfPrecipitation": 20
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    private val dailyJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [28.9784, 41.0082, 39.0] },
              "properties": {
                "location": { "name": "Istanbul" },
                "modelRunDate": "2026-11-22T00:00:00Z",
                "timeSeries": [
                  {
                    "time": "2026-11-22T00:00:00Z",
                    "dayMaxTemp": 16.0,
                    "nightMinTemp": 9.0,
                    "dayMaxUVIndex": 2,
                    "dayProbabilityOfPrecipitation": 20,
                    "daySignificantWeatherCode": 6,
                    "nightSignificantWeatherCode": 3
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `hourly parameters map with official units`() {
        val rows = MetOfficeParser.parse(hourlyJson, "hourly", "Hourly forecast")!!
        assertEquals(2, rows.size)

        val temp = rows.first().measurements.first { it.code == "screenTemperature" }
        assertEquals(13.2, temp.value!!, 0.001)
        assertEquals("degC", temp.unit)

        val wind = rows.first().measurements.first { it.code == "windSpeed10m" }
        assertEquals(6.2, wind.value!!, 0.001)
        assertEquals("m/s", wind.unit)

        val pressure = rows.first().measurements.first { it.code == "mslp" }
        assertEquals(101840.0, pressure.value!!, 0.001)
        assertEquals("Pa", pressure.unit)

        val visibility = rows.first().measurements.first { it.code == "visibility" }
        assertEquals(20000.0, visibility.value!!, 0.001)
        assertEquals("m", visibility.unit)

        val humidity = rows.first().measurements.first { it.code == "screenRelativeHumidity" }
        assertEquals(78.0, humidity.value!!, 0.001)
        assertEquals("%", humidity.unit)
    }

    @Test
    fun `null parameters stay null and are not zero-filled`() {
        val rows = MetOfficeParser.parse(hourlyJson, "hourly", "Hourly forecast")!!
        val second = rows[1]
        val rate = second.measurements.first { it.code == "precipitationRate" }
        val amount = second.measurements.first { it.code == "totalPrecipAmount" }
        assertNull(rate.value)
        assertNull(amount.value)
        assertEquals(Availability.NULL_VALUE, rate.availability)
    }

    @Test
    fun `weather codes are translated but kept as text`() {
        val rows = MetOfficeParser.parse(hourlyJson, "hourly", "Hourly forecast")!!
        val code = rows.first().measurements.first { it.code == "significantWeatherCode" }
        assertEquals("Clear day", code.text)
        assertEquals("4", code.originalValue)
        assertNull("A code is not a numeric measurement", code.value)
    }

    @Test
    fun `every returned parameter is captured with no whitelist`() {
        val rows = MetOfficeParser.parse(hourlyJson, "hourly", "Hourly forecast")!!
        val codes = rows.first().measurements.map { it.code }.toSet()
        assertTrue(codes.contains("screenTemperature"))
        assertTrue(codes.contains("feelsLikeTemperature"))
        assertTrue(codes.contains("screenRelativeHumidity"))
        assertTrue(codes.contains("windSpeed10m"))
        assertTrue(codes.contains("windDirectionFrom10m"))
        assertTrue(codes.contains("windGustSpeed10m"))
        assertTrue(codes.contains("visibility"))
        assertTrue(codes.contains("mslp"))
        assertTrue(codes.contains("uvIndex"))
        assertTrue(codes.contains("significantWeatherCode"))
        assertTrue(codes.contains("precipitationRate"))
        assertTrue(codes.contains("totalPrecipAmount"))
        assertTrue(codes.contains("probOfPrecipitation"))
    }

    @Test
    fun `daily day and night split is preserved`() {
        val rows = MetOfficeParser.parse(dailyJson, "daily", "Daily forecast")!!
        assertEquals(1, rows.size)
        val codes = rows.first().measurements.map { it.code }.toSet()
        assertTrue(codes.contains("dayMaxTemp"))
        assertTrue(codes.contains("nightMinTemp"))
        assertTrue(codes.contains("daySignificantWeatherCode"))
        assertTrue(codes.contains("nightSignificantWeatherCode"))
        assertTrue(codes.contains("dayMaxUVIndex"))
        assertTrue(codes.contains("dayProbabilityOfPrecipitation"))
    }

    @Test
    fun `known weather codes have labels and unknown codes are preserved`() {
        assertEquals("Clear day", MetOfficeWeatherCodes.label("4"))
        assertEquals("Heavy rain (night)", MetOfficeWeatherCodes.label("15"))
        assertEquals("Weather code 99", MetOfficeWeatherCodes.label("99"))
        assertNull(MetOfficeWeatherCodes.label(null))
    }

    @Test
    fun `metadata catalogue covers the verified parameter set`() {
        assertNotNull(MetOfficeParams.all()["screenTemperature"])
        assertNotNull(MetOfficeParams.all()["probOfPrecipitation"])
        assertNotNull(MetOfficeParams.all()["uvIndex"])
        assertNotNull(MetOfficeParams.all()["significantWeatherCode"])
        assertEquals("Temperature", MetOfficeParams.category("screenTemperature"))
        assertEquals("Wind", MetOfficeParams.category("windSpeed10m"))
    }
}
