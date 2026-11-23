package com.istanbul.weatherintel.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.istanbul.weatherintel.data.db.WeatherDatabase
import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ForecastRow
import com.istanbul.weatherintel.data.model.ForecastRun
import com.istanbul.weatherintel.data.model.LocationRecord
import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ObservationRecord
import com.istanbul.weatherintel.data.model.ProviderCollection
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.model.RawPayload
import com.istanbul.weatherintel.data.model.StationRecord
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
 * Repository tests against an in-memory database.
 *
 * These verify the storage contract: raw payloads are retained, normalized
 * rows are written, missing values stay null and the two providers stay
 * independent.
 */
@RunWith(AndroidJUnit4::class)
class WeatherRepositoryTest {

    private lateinit var db: WeatherDatabase
    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            WeatherDatabase::class.java,
        ).build()
        repository = WeatherRepository(db)
        repository.ensureProviders()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun mgmCollection() = ProviderCollection(
        provider = ProviderId.MGM,
        location = LocationRecord(
            provider = ProviderId.MGM,
            sourceLocationId = "93401",
            name = "Bakirkoy",
            region = "Istanbul",
            latitude = 40.98,
            longitude = 28.86,
        ),
        stations = listOf(
            StationRecord(
                provider = ProviderId.MGM,
                externalId = "17060",
                name = "Bakirkoy",
                latitude = 40.98,
                longitude = 28.86,
                elevation = 30.0,
                metadataJson = """{"city":"Bakirkoy"}""",
            )
        ),
        observations = listOf(
            ObservationRecord(
                provider = ProviderId.MGM,
                locationName = "Bakirkoy",
                stationExternalId = "17060",
                stationName = "Bakirkoy",
                observedAt = 1_700_000_000_000L,
                collectedAt = 1_700_000_060_000L,
                measurements = listOf(
                    Measurement(
                        code = "sicaklik",
                        name = "Temperature",
                        value = 12.5,
                        unit = "degC",
                        originalValue = "12.5",
                        category = "Temperature",
                    ),
                    Measurement(
                        code = "denizSicaklik",
                        name = "Sea temperature",
                        value = null,
                        unit = "degC",
                        originalValue = "-9999.0",
                        category = "Temperature",
                        availability = Availability.NULL_VALUE,
                    ),
                ),
                originalJson = """{"sicaklik":"12.5","denizSicaklik":"-9999.0"}""",
                sourceRecordId = "17060",
            )
        ),
        forecastRuns = listOf(
            ForecastRun(
                provider = ProviderId.MGM,
                product = "hourly",
                issuedAt = 1_699_999_000_000L,
                collectedAt = 1_700_000_060_000L,
                sourceForecastId = "mgm-hourly-1",
                locationName = "Bakirkoy",
                rows = listOf(
                    ForecastRow(
                        validAt = 1_700_002_800_000L,
                        period = "hourly",
                        measurements = listOf(
                            Measurement(
                                code = "sicaklik",
                                name = "Temperature",
                                value = 13.0,
                                unit = "degC",
                                originalValue = "13.0",
                                category = "Temperature",
                            ),
                            Measurement(
                                code = "ruzgarHiz",
                                name = "Wind speed",
                                value = 18.0,
                                unit = "km/h",
                                originalValue = "18.0",
                                category = "Wind",
                            ),
                        ),
                    )
                ),
            )
        ),
        rawPayloads = listOf(
            RawPayload(
                provider = ProviderId.MGM,
                endpoint = "/web/sondurumlar?merkezid=93401",
                retrievedAt = 1_700_000_060_000L,
                httpStatus = 200,
                contentType = "application/json",
                body = """{"sicaklik":"12.5"}""",
            )
        ),
    )

    @Test
    fun providersAreRegistered() = runBlocking {
        assertNotNull(repository.providerEntity(ProviderId.MGM))
        assertNotNull(repository.providerEntity(ProviderId.MET_OFFICE))
        assertEquals(2, repository.observeProviders().first().size)
    }

    @Test
    fun savingAProviderCollectionPersistsEverything() = runBlocking {
        val runId = repository.startRun(
            trigger = "manual",
            intervalMinutes = 360,
            requestedProviders = 2,
            appVersion = "1.0.0",
        )
        val saved = repository.saveProviderCollection(runId, mgmCollection(), received = 3)

        // location + station + raw payload + observation + forecast run + 2 values
        assertEquals(8, saved)

        assertNotNull(repository.observeLatestObservation("MGM").first())
        val raw = db.rawResponseDao().forRun(runId)
        assertEquals(1, raw.size)
        assertTrue("the raw body is retained verbatim", raw[0].responseBody.contains("12.5"))
        assertTrue("the response is hashed for integrity", raw[0].responseHash.isNotBlank())

        assertEquals(1, db.stationDao().forProvider(repository.providerIdOf(ProviderId.MGM)).size)
    }

    @Test
    fun aMissingValueIsStoredAsNullAndNotAsZero() = runBlocking {
        val runId = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(runId, mgmCollection(), received = 3)

        val observation = repository.observeLatestObservation("MGM").first()!!
        assertTrue(
            "the sentinel must not survive as a numeric value",
            observation.normalizedJson.contains("\"value\":null"),
        )
        assertTrue(
            "the original sentinel is still kept for provenance",
            observation.normalizedJson.contains("-9999.0"),
        )
    }

    @Test
    fun forecastValuesAreStoredWithTheirValidTimeAndUnit() = runBlocking {
        val runId = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(runId, mgmCollection(), received = 3)

        val values = db.forecastDao().values(
            db.forecastDao().latestRun("MGM", "hourly")!!.id
        )
        assertEquals(2, values.size)
        val wind = values.first { it.parameterCode == "ruzgarHiz" }
        assertEquals("km/h", wind.unit)
        assertEquals(18.0, wind.numericValue!!, 0.001)
        assertEquals("Wind", wind.category)
        assertEquals("available", wind.availability)
    }

    @Test
    fun providerResultsAreIndependentAndFailuresAreRecorded() = runBlocking {
        val runId = repository.startRun("manual", 360, 2, "1.0.0")
        repository.recordProviderResult(
            runId = runId,
            provider = ProviderId.MGM,
            success = true,
            startedAt = 1L,
            finishedAt = 2L,
            recordsReceived = 3,
            recordsSaved = 8,
            errorCode = null,
            errorMessage = null,
            endpoints = listOf("/web/sondurumlar"),
        )
        repository.recordProviderResult(
            runId = runId,
            provider = ProviderId.MET_OFFICE,
            success = false,
            startedAt = 1L,
            finishedAt = 2L,
            recordsReceived = 0,
            recordsSaved = 0,
            errorCode = "auth",
            errorMessage = "Invalid Credentials",
            endpoints = listOf("/sitespecific/v0/point/hourly"),
        )
        repository.finishRun(runId, successCount = 1, failureCount = 1)

        val run = db.runDao().byId(runId)!!
        assertEquals("partial", run.overallStatus)
        assertEquals(1, run.successCount)
        assertEquals(1, run.failureCount)

        val errors = db.errorDao().observeRecent(10).first()
        assertEquals(1, errors.size)
        assertEquals("auth", errors[0].errorType)
        assertEquals("Invalid Credentials", errors[0].message)
    }

    @Test
    fun forecastRunHistoryKeepsEveryVersion() = runBlocking {
        val runId = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(runId, mgmCollection(), received = 3)
        val secondRunId = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(secondRunId, mgmCollection(), received = 3)

        val history = db.forecastDao().runHistory("MGM", "hourly", 10)
        assertEquals("older forecast versions are never overwritten", 2, history.size)
    }

    @Test
    fun latestObservationIsTheMostRecentlyObserved() = runBlocking {
        val runId = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(runId, mgmCollection(), received = 3)

        val older = mgmCollection().observations.first().copy(
            observedAt = 1_600_000_000_000L,
            sourceRecordId = "older",
        )
        val olderRun = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(
            olderRun,
            mgmCollection().copy(observations = listOf(older)),
            received = 1,
        )

        val latest = repository.observeLatestObservation("MGM").first()!!
        assertEquals("17060", latest.sourceRecordId)
    }

    @Test
    fun metOfficeHasNoObservationUntilItSuppliesOne() = runBlocking {
        val runId = repository.startRun("manual", 360, 2, "1.0.0")
        repository.saveProviderCollection(runId, mgmCollection(), received = 3)
        assertNull(repository.observeLatestObservation("MET_OFFICE").first())
    }

    @Test
    fun rawResponsesAreRetrievablePerRun() = runBlocking {
        val runId = repository.startRun("manual", 360, 1, "1.0.0")
        repository.saveProviderCollection(runId, mgmCollection(), received = 3)
        val raw = db.rawResponseDao().forRun(runId)
        assertEquals(1, raw.size)
        assertTrue(raw[0].productOrEndpoint.contains("sondurumlar"))
        assertEquals(200, raw[0].httpStatus)
    }
}
