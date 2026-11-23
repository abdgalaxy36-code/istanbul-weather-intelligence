package com.istanbul.weatherintel.data.repository

import com.istanbul.weatherintel.data.db.ForecastRunEntity
import com.istanbul.weatherintel.data.db.ForecastValueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forecast change analysis rules:
 *  - issue time and valid time stay distinct,
 *  - only parameters present in both versions are compared,
 *  - a parameter that appears or disappears is reported, not zeroed,
 *  - no value is ever invented.
 */
class ForecastChangeBuilderTest {

    private fun run(id: Long, collectedAt: Long, issuedAt: Long?) = ForecastRunEntity(
        id = id,
        providerId = 1L,
        locationId = null,
        productName = "hourly",
        issuedAt = issuedAt,
        collectedAt = collectedAt,
        sourceForecastId = "run-$id",
        rawResponseId = null,
        collectionRunId = 1L,
        rowCount = 2,
    )

    private fun value(
        runId: Long,
        validAt: Long,
        code: String,
        numeric: Double?,
        name: String = code,
        unit: String? = "degC",
    ) = ForecastValueEntity(
        forecastRunId = runId,
        validAt = validAt,
        forecastPeriod = "hourly",
        parameterCode = code,
        parameterName = name,
        numericValue = numeric,
        textValue = null,
        unit = unit,
        originalValueJson = numeric?.toString(),
        category = "Temperature",
        availability = if (numeric == null) "null" else "available",
    )

    @Test
    fun `a changed value is reported with its movement`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = listOf(value(1L, 5000L, "sicaklik", 13.0)),
            currentValues = listOf(value(2L, 5000L, "sicaklik", 15.5)),
        )
        assertTrue(change.hasComparison)
        assertEquals(1, change.deltas.size)
        val delta = change.deltas.first()
        assertEquals(ForecastChangeBuilder.Status.CHANGED, delta.status)
        assertEquals(13.0, delta.previousValue!!, 0.001)
        assertEquals(15.5, delta.currentValue!!, 0.001)
        assertEquals(2.5, delta.change!!, 0.001)
        assertEquals(5000L, delta.validAt)
    }

    @Test
    fun `an unchanged value is reported as unchanged`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = listOf(value(1L, 5000L, "sicaklik", 13.0)),
            currentValues = listOf(value(2L, 5000L, "sicaklik", 13.0)),
        )
        assertEquals(ForecastChangeBuilder.Status.UNCHANGED, change.deltas.first().status)
        assertEquals(0.0, change.deltas.first().change!!, 0.0001)
    }

    @Test
    fun `a parameter only in the newer version is reported as added and not zeroed`() {
        val change = ForecastChangeBuilder.build(
            provider = "MET_OFFICE",
            product = "three-hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = emptyList(),
            currentValues = listOf(value(2L, 5000L, "probOfRain", 40.0, unit = "%")),
        )
        val delta = change.deltas.first()
        assertEquals(ForecastChangeBuilder.Status.ADDED, delta.status)
        assertNull("a missing previous value must stay null", delta.previousValue)
        assertNull("an added parameter has no change", delta.change)
        assertEquals(40.0, delta.currentValue!!, 0.001)
    }

    @Test
    fun `a parameter only in the older version is reported as removed`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = listOf(value(1L, 5000L, "sicaklik", 13.0)),
            currentValues = emptyList(),
        )
        val delta = change.deltas.first()
        assertEquals(ForecastChangeBuilder.Status.REMOVED, delta.status)
        assertNull(delta.currentValue)
        assertNull(delta.change)
    }

    @Test
    fun `different valid times are kept apart`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = listOf(value(1L, 5000L, "sicaklik", 13.0)),
            currentValues = listOf(value(2L, 6000L, "sicaklik", 15.0)),
        )
        assertEquals(2, change.deltas.size)
        assertTrue(change.deltas.all { it.status == ForecastChangeBuilder.Status.ADDED ||
            it.status == ForecastChangeBuilder.Status.REMOVED })
    }

    @Test
    fun `a null numeric value is never treated as zero`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = listOf(value(1L, 5000L, "sicaklik", 13.0)),
            currentValues = listOf(value(2L, 5000L, "sicaklik", null)),
        )
        val delta = change.deltas.first()
        assertNull(delta.currentValue)
        assertNull("no change can be computed against a missing value", delta.change)
        assertNotNull(delta.status)
    }

    @Test
    fun `deltas are grouped by valid time in order`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = run(1L, 1000L, 900L),
            currentRun = run(2L, 2000L, 1900L),
            previousValues = listOf(
                value(1L, 6000L, "sicaklik", 13.0),
                value(1L, 6000L, "nem", 70.0, unit = "%"),
                value(1L, 5000L, "sicaklik", 12.0),
                value(1L, 5000L, "nem", 75.0, unit = "%"),
            ),
            currentValues = listOf(
                value(2L, 5000L, "sicaklik", 12.5),
                value(2L, 5000L, "nem", 72.0, unit = "%"),
                value(2L, 6000L, "sicaklik", 13.5),
                value(2L, 6000L, "nem", 68.0, unit = "%"),
            ),
        )
        val grouped = ForecastChangeBuilder.byValidTime(change.deltas)
        assertEquals(listOf(5000L, 6000L), grouped.keys.toList())
        assertEquals("both parameters of the earlier valid time", 2, grouped.getValue(5000L).size)
        assertEquals("both parameters of the later valid time", 2, grouped.getValue(6000L).size)
        assertEquals(4, change.deltas.size)
    }

    @Test
    fun `without two runs there is no comparison`() {
        val change = ForecastChangeBuilder.build(
            provider = "MGM",
            product = "hourly",
            previousRun = null,
            currentRun = run(2L, 2000L, 1900L),
            previousValues = emptyList(),
            currentValues = emptyList(),
        )
        assertTrue(!change.hasComparison)
        assertTrue(change.deltas.isEmpty())
    }
}
