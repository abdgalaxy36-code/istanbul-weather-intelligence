package com.istanbul.weatherintel.data.repository

import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.DataType
import com.istanbul.weatherintel.data.model.Measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparison rules (requirement sections 7 and 9):
 *  - only compatible parameters with compatible units are compared,
 *  - forecast values must share a valid time,
 *  - observations are never compared with forecasts,
 *  - a missing value is never treated as zero,
 *  - provider-specific fields are never dropped.
 */
class ComparisonBuilderTest {

    private fun m(
        code: String,
        value: Double? = null,
        unit: String? = "degC",
        category: String = "Temperature",
        availability: Availability = Availability.AVAILABLE,
        text: String? = null,
    ) = Measurement(
        code = code,
        name = code,
        value = value,
        text = text,
        unit = unit,
        originalValue = value?.toString(),
        category = category,
        availability = availability,
    )

    // ------------------------------------------------- Cross-provider pairing ---

    @Test
    fun `MGM and Met Office temperature are lined up despite different keys`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("sicaklik", 13.0, unit = "degC")),
            metOffice = listOf(m("screenTemperature", 15.0, unit = "degC")),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertEquals(1, rows.size)
        assertTrue("MGM sicaklik must pair with Met Office screenTemperature", rows.first().comparable)
        assertEquals(-2.0, ComparisonBuilder.difference(rows.first())!!, 0.001)
        assertEquals("temperature", rows.first().code)
        assertEquals("Temperature", rows.first().name)
    }

    @Test
    fun `wind speed is converted from metres per second to kilometres per hour`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("ruzgarHiz", 20.0, unit = "km/h", category = "Wind")),
            metOffice = listOf(m("windSpeed10m", 5.0, unit = "m/s", category = "Wind")),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.first().comparable)
        // 20 km/h - 18 km/h (5 m/s) = 2 km/h
        assertEquals(2.0, ComparisonBuilder.difference(rows.first())!!, 0.001)
        assertEquals("km/h", rows.first().differenceUnit)
        // The stored source values are untouched.
        assertEquals(20.0, rows.first().mgm!!.value!!, 0.001)
        assertEquals("km/h", rows.first().mgm!!.unit)
        assertEquals(5.0, rows.first().metOffice!!.value!!, 0.001)
        assertEquals("m/s", rows.first().metOffice!!.unit)
    }

    @Test
    fun `pressure is converted from pascals to hectopascals`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("denizeIndirgenmisBasinc", 1015.0, unit = "hPa", category = "Pressure")),
            metOffice = listOf(m("mslp", 101840.0, unit = "Pa", category = "Pressure")),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertTrue(rows.first().comparable)
        assertEquals(-3.4, ComparisonBuilder.difference(rows.first())!!, 0.05)
    }

    @Test
    fun `daily MGM day-indexed keys pair with Met Office daily keys`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("enYuksekGun0", 16.0, unit = "degC")),
            metOffice = listOf(m("dayMaxScreenTemperature", 17.0, unit = "degC")),
            scope = ComparisonScope.DAILY,
            dataType = DataType.FORECAST,
            mgmValidAt = 1000L,
            metOfficeValidAt = 1000L,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.first().comparable)
        assertEquals(-1.0, ComparisonBuilder.difference(rows.first())!!, 0.001)
    }

    @Test
    fun `weather condition codes are shown but not numerically compared`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("hadiseKodu", null, unit = null, category = "Weather condition", text = "PB")),
            metOffice = listOf(
                m(
                    "significantWeatherCode", null, unit = null,
                    category = "Weather condition", text = "Partly cloudy day",
                )
            ),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertEquals(1, rows.size)
        assertFalse(rows.first().comparable)
        assertNull(ComparisonBuilder.difference(rows.first()))
        assertNotNull(rows.first().note)
        assertEquals("PB", rows.first().mgm!!.text)
    }

    @Test
    fun `the three-hourly product pairs MGM feels-like with the Met Office key`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("hissedilenSicaklik", 11.0)),
            metOffice = listOf(m("feelsLikeTemp", 12.0)),
            scope = ComparisonScope.THREE_HOURLY,
            dataType = DataType.FORECAST,
            mgmValidAt = 1000L,
            metOfficeValidAt = 1000L,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.first().comparable)
        assertEquals(-1.0, ComparisonBuilder.difference(rows.first())!!, 0.001)
    }

    // ------------------------------------------------------ Comparability -----

    @Test
    fun `different units prevent a difference from being calculated`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("sicaklik", 13.0, unit = "degC")),
            metOffice = listOf(m("screenTemperature", 15.0, unit = "kelvin")),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertFalse(rows.first().comparable)
        assertNull(ComparisonBuilder.difference(rows.first()))
        assertNotNull(rows.first().note)
    }

    @Test
    fun `missing value is not treated as zero`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("sicaklik", 13.0)),
            metOffice = listOf(
                m("screenTemperature", null, availability = Availability.NULL_VALUE)
            ),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertFalse(rows.first().comparable)
        assertNull(ComparisonBuilder.difference(rows.first()))
        assertEquals("Not provided", rows.first().metOffice?.display())
    }

    @Test
    fun `provider specific field is kept even without a counterpart`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("denizSicaklik", 14.0, category = "Temperature")),
            metOffice = emptyList(),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertEquals(1, rows.size)
        assertNotNull(rows.first().mgm)
        assertNull(rows.first().metOffice)
        assertFalse(rows.first().comparable)
        assertTrue(rows.first().note!!.contains("Only MGM"))
    }

    @Test
    fun `forecasts with different valid times are not compared`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("sicaklik", 13.0)),
            metOffice = listOf(m("screenTemperature", 15.0)),
            scope = ComparisonScope.THREE_HOURLY,
            dataType = DataType.FORECAST,
            mgmValidAt = 1000L,
            metOfficeValidAt = 2000L,
        )
        assertFalse(rows.first().comparable)
        assertNull(ComparisonBuilder.difference(rows.first()))
    }

    @Test
    fun `forecasts with the same valid time are compared`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("sicaklik", 13.0)),
            metOffice = listOf(m("screenTemperature", 15.0)),
            scope = ComparisonScope.THREE_HOURLY,
            dataType = DataType.FORECAST,
            mgmValidAt = 1000L,
            metOfficeValidAt = 1000L,
        )
        assertTrue(rows.first().comparable)
        assertEquals(-2.0, ComparisonBuilder.difference(rows.first())!!, 0.001)
    }

    @Test
    fun `every field from both providers appears in the output`() {
        val rows = ComparisonBuilder.build(
            mgm = listOf(m("a", 1.0), m("b", 2.0), m("c", 3.0)),
            metOffice = listOf(m("d", 4.0), m("e", 5.0)),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertEquals(5, rows.size)
        assertEquals(
            setOf("mgm:a", "mgm:b", "mgm:c", "mo:d", "mo:e"),
            rows.map { it.code }.toSet(),
        )
    }

    @Test
    fun `no fixed row limit is applied`() {
        val many = (1..500).map { m("field$it", it.toDouble()) }
        val rows = ComparisonBuilder.build(
            mgm = many,
            metOffice = emptyList(),
            scope = ComparisonScope.OBSERVATION,
            dataType = DataType.OBSERVATION,
        )
        assertEquals(500, rows.size)
    }

    // ------------------------------------------------------ Aligned forecasts ---

    @Test
    fun `aligned forecasts only pair matching valid times`() {
        val periods = ComparisonBuilder.buildAligned(
            mgm = listOf(
                ComparisonBuilder.TimedMeasurement(1000L, m("sicaklik", 13.0)),
                ComparisonBuilder.TimedMeasurement(2000L, m("sicaklik", 14.0)),
            ),
            metOffice = listOf(
                ComparisonBuilder.TimedMeasurement(1000L, m("screenTemperature", 15.0)),
            ),
            scope = ComparisonScope.THREE_HOURLY,
        )
        assertEquals(2, periods.size)
        val first = periods.first { it.validAt == 1000L }
        assertTrue(first.rows.first().comparable)
        val second = periods.first { it.validAt == 2000L }
        assertFalse(second.rows.first().comparable)
        assertNull(second.rows.first().metOffice)
    }

    // ------------------------------------------------------- Unit conversion ---

    @Test
    fun `unit conversions are explicit and leave the source value untouched`() {
        assertEquals(36.0, UnitConverter.windToKmh(10.0, "m/s")!!, 0.001)
        assertEquals(10.0, UnitConverter.windToKmh(10.0, "km/h")!!, 0.001)
        assertEquals(18.52, UnitConverter.windToKmh(10.0, "knot")!!, 0.001)
        assertNull(UnitConverter.windToKmh(10.0, "furlongs per fortnight"))

        assertEquals(1018.4, UnitConverter.pressureToHpa(101840.0, "Pa")!!, 0.001)
        assertEquals(1015.0, UnitConverter.pressureToHpa(1015.0, "hPa")!!, 0.001)
        assertNull(UnitConverter.pressureToHpa(1015.0, "psi"))
    }

    @Test
    fun `generic conversion handles wind and pressure both ways`() {
        assertEquals(10.0, UnitConverter.convert(10.0, "km/h", "km/h")!!, 0.001)
        assertEquals(36.0, UnitConverter.convert(10.0, "m/s", "km/h")!!, 0.001)
        assertEquals(10.0, UnitConverter.convert(36.0, "km/h", "m/s")!!, 0.001)
        assertEquals(1018.4, UnitConverter.convert(101840.0, "Pa", "hPa")!!, 0.001)
        assertEquals(101840.0, UnitConverter.convert(1018.4, "hPa", "Pa")!!, 0.001)
        assertNull(UnitConverter.convert(1.0, "degC", "km/h"))
        assertNull(UnitConverter.convert(1.0, null, "km/h"))
        assertNull(UnitConverter.convert(1.0, "km/h", null))
    }
}
