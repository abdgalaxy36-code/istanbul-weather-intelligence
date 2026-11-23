package com.istanbul.weatherintel.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room DAO tests against an in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseDaoTest {

    private lateinit var db: WeatherDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            WeatherDatabase::class.java,
        ).build()
        db.providerDao().upsert(mgmProvider())
        db.providerDao().upsert(metOfficeProvider())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun mgmProvider() = ProviderEntity(
        id = 1L,
        providerCode = "MGM",
        displayName = "MGM (Turkish State Met Service)",
        attribution = "Data source: Meteoroloji Genel Müdürlüğü (MGM)",
        documentationUrl = "https://www.mgm.gov.tr/",
        enabled = true,
    )

    private fun metOfficeProvider() = ProviderEntity(
        id = 2L,
        providerCode = "MET_OFFICE",
        displayName = "UK Met Office Weather DataHub",
        attribution = "Powered by Met Office data",
        documentationUrl = "https://datahub.metoffice.gov.uk/",
        enabled = true,
    )

    private suspend fun newRun(trigger: String = "periodic"): Long =
        db.runDao().insert(
            CollectionRunEntity(
                startedAt = 1_000L,
                configuredIntervalMinutes = 360,
                overallStatus = "running",
                appVersion = "1.0.0",
                requestedProviderCount = 2,
                trigger = trigger,
            )
        )

    @Test
    fun providersRoundTrip() = runBlocking {
        val mgm = db.providerDao().byCode("MGM")
        assertNotNull(mgm)
        assertEquals("MGM", mgm!!.providerCode)
        assertNull(db.providerDao().byCode("NOT_A_PROVIDER"))
        assertEquals(2, db.providerDao().observeAll().first().size)
    }

    @Test
    fun collectionRunRecordsCountsAndStatus() = runBlocking {
        val runId = newRun(trigger = "manual")
        db.runDao().finish(runId, 5_000L, "success", 2, 0)
        val run = db.runDao().byId(runId)
        assertNotNull(run)
        assertEquals("success", run!!.overallStatus)
        assertEquals(2, run.successCount)
        assertEquals(0, run.failureCount)
        assertEquals(5_000L, run.finishedAt)
    }

    @Test
    fun providerResultsAreIndependentPerProvider() = runBlocking {
        val runId = newRun()
        db.providerResultDao().insert(
            ProviderCollectionResultEntity(
                collectionRunId = runId,
                providerId = 1L,
                status = "success",
                startedAt = 1_000L,
                finishedAt = 2_000L,
                recordsReceived = 1,
                recordsSaved = 1,
                errorCode = null,
                errorMessage = null,
                endpoints = "[]",
            )
        )
        db.providerResultDao().insert(
            ProviderCollectionResultEntity(
                collectionRunId = runId,
                providerId = 2L,
                status = "failed",
                startedAt = 1_000L,
                finishedAt = 2_000L,
                recordsReceived = 0,
                recordsSaved = 0,
                errorCode = "auth",
                errorMessage = "Invalid Credentials",
                endpoints = "[]",
            )
        )
        val results = db.providerResultDao().forRun(runId)
        assertEquals(2, results.size)
        assertTrue(results.any { it.status == "success" && it.providerId == 1L })
        assertTrue(results.any { it.status == "failed" && it.providerId == 2L })

        val last = db.providerResultDao().observeLastForProvider("MET_OFFICE").first()
        assertNotNull(last)
        assertEquals("failed", last!!.status)
    }

    @Test
    fun forecastValuesAreStoredPerValidTime() = runBlocking {
        val runId = newRun()
        val forecastRunId = db.forecastDao().insert(
            ForecastRunEntity(
                providerId = 1L,
                locationId = null,
                productName = "hourly",
                issuedAt = 900L,
                collectedAt = 1_000L,
                sourceForecastId = "run-1",
                rawResponseId = null,
                collectionRunId = runId,
                rowCount = 2,
            )
        )
        db.forecastDao().insertValues(
            listOf(
                ForecastValueEntity(
                    forecastRunId = forecastRunId,
                    validAt = 5_000L,
                    forecastPeriod = "hourly",
                    parameterCode = "sicaklik",
                    parameterName = "Temperature",
                    numericValue = 13.0,
                    textValue = null,
                    unit = "degC",
                    originalValueJson = "13.0",
                    category = "Temperature",
                ),
                ForecastValueEntity(
                    forecastRunId = forecastRunId,
                    validAt = 5_000L,
                    forecastPeriod = "hourly",
                    parameterCode = "nem",
                    parameterName = "Relative humidity",
                    numericValue = 80.0,
                    textValue = null,
                    unit = "%",
                    originalValueJson = "80.0",
                    category = "Humidity",
                ),
            )
        )
        val values = db.forecastDao().values(forecastRunId)
        assertEquals(2, values.size)
        assertEquals(1, values.groupBy { it.validAt }.size)
        assertEquals(2, db.forecastDao().observeValueCount().first())
    }

    @Test
    fun aNullValueStaysNullAndIsNotStoredAsZero() = runBlocking {
        val runId = newRun()
        val forecastRunId = db.forecastDao().insert(
            ForecastRunEntity(
                providerId = 1L,
                locationId = null,
                productName = "hourly",
                issuedAt = null,
                collectedAt = 1_000L,
                sourceForecastId = null,
                rawResponseId = null,
                collectionRunId = runId,
                rowCount = 1,
            )
        )
        db.forecastDao().insertValues(
            listOf(
                ForecastValueEntity(
                    forecastRunId = forecastRunId,
                    validAt = 5_000L,
                    forecastPeriod = "hourly",
                    parameterCode = "denizSicaklik",
                    parameterName = "Sea temperature",
                    numericValue = null,
                    textValue = null,
                    unit = "degC",
                    originalValueJson = "-9999.0",
                    category = "Temperature",
                    availability = "null",
                )
            )
        )
        val stored = db.forecastDao().values(forecastRunId).first()
        assertNull("a missing value must stay null", stored.numericValue)
        assertEquals("null", stored.availability)
        assertEquals("-9999.0", stored.originalValueJson)
    }

    @Test
    fun deletingARunCascadesToItsResults() = runBlocking {
        val runId = newRun()
        db.providerResultDao().insert(
            ProviderCollectionResultEntity(
                collectionRunId = runId,
                providerId = 1L,
                status = "success",
                startedAt = 1L,
                finishedAt = 2L,
                recordsReceived = 0,
                recordsSaved = 0,
                errorCode = null,
                errorMessage = null,
                endpoints = "[]",
            )
        )
        assertEquals(1, db.providerResultDao().forRun(runId).size)
        db.runDao().deleteById(runId)
        assertEquals(0, db.providerResultDao().forRun(runId).size)
    }

    @Test
    fun observationsKeepTheirNormalizedPayloadVerbatim() = runBlocking {
        val runId = newRun()
        db.observationDao().insert(
            WeatherObservationEntity(
                providerId = 1L,
                locationId = null,
                stationId = null,
                sourceRecordId = "17060",
                observedAt = 2_000L,
                collectedAt = 3_000L,
                normalizedJson = "[{\"code\":\"sicaklik\",\"value\":12.5}]",
                providerSpecificJson = "{\"sicaklik\":\"12.5\"}",
                collectionRunId = runId,
            )
        )
        val latest = db.observationDao().latest("MGM")
        assertNotNull(latest)
        assertEquals("17060", latest!!.sourceRecordId)
        assertTrue(latest.normalizedJson.contains("sicaklik"))
        assertTrue(latest.providerSpecificJson.contains("12.5"))
    }

    @Test
    fun forecastRunHistoryIsOrderedNewestFirst() = runBlocking {
        val runId = newRun()
        listOf(1_000L, 3_000L, 2_000L).forEachIndexed { index, collectedAt ->
            db.forecastDao().insert(
                ForecastRunEntity(
                    providerId = 1L,
                    locationId = null,
                    productName = "hourly",
                    issuedAt = collectedAt,
                    collectedAt = collectedAt,
                    sourceForecastId = "run-$index",
                    rawResponseId = null,
                    collectionRunId = runId,
                    rowCount = 1,
                )
            )
        }
        val history = db.forecastDao().runHistory("MGM", "hourly", 2)
        assertEquals(2, history.size)
        assertEquals(3_000L, history[0].collectedAt)
        assertEquals(2_000L, history[1].collectedAt)
    }

    @Test
    fun stationsCanBeSelectedAndCleared() = runBlocking {
        db.stationDao().upsertAll(
            listOf(
                StationEntity(
                    providerId = 1L,
                    externalStationId = "17060",
                    stationName = "Bakirkoy",
                    latitude = 40.98,
                    longitude = 28.86,
                ),
                StationEntity(
                    providerId = 1L,
                    externalStationId = "17061",
                    stationName = "Florya",
                    latitude = 40.98,
                    longitude = 28.78,
                ),
            )
        )
        assertEquals(2, db.stationDao().forProvider(1L).size)
        db.stationDao().select(1L, "17060")
        assertEquals(1, db.stationDao().forProvider(1L).count { it.isSelected })
        db.stationDao().clearSelection()
        assertEquals(0, db.stationDao().forProvider(1L).count { it.isSelected })
    }
}
