package com.istanbul.weatherintel.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.istanbul.weatherintel.data.db.CollectionErrorEntity
import com.istanbul.weatherintel.data.db.CollectionRunEntity
import com.istanbul.weatherintel.data.db.ForecastRunEntity
import com.istanbul.weatherintel.data.db.ForecastValueEntity
import com.istanbul.weatherintel.data.db.LocationEntity
import com.istanbul.weatherintel.data.db.MetadataDao
import com.istanbul.weatherintel.data.db.ProviderCollectionResultEntity
import com.istanbul.weatherintel.data.db.ProviderEntity

import com.istanbul.weatherintel.data.db.RawResponseEntity
import com.istanbul.weatherintel.data.db.StationEntity
import com.istanbul.weatherintel.data.db.WeatherDatabase
import com.istanbul.weatherintel.data.db.WeatherMetadataEntity
import com.istanbul.weatherintel.data.db.WeatherObservationEntity
import com.istanbul.weatherintel.data.db.WeatherWarningEntity
import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ComparisonRow
import com.istanbul.weatherintel.data.model.ComparisonValue
import com.istanbul.weatherintel.data.model.DataType
import com.istanbul.weatherintel.data.model.ForecastRun
import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ProviderCollection
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.model.RawPayload
import com.istanbul.weatherintel.data.model.StationRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.security.MessageDigest
/**
 * Single access point to local storage. Everything the UI shows comes from here;
 * the UI never touches the network directly.
 */
class WeatherRepository(
    private val db: WeatherDatabase,
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // ------------------------------------------------------------- providers

    suspend fun ensureProviders() {
        upsertProvider(
            ProviderId.MGM,
            displayName = "MGM (Turkish State Met Service)",
            licence = "MGM public web service. Not an officially documented public API; " +
                "subject to change. Used read-only for personal weather analysis.",
            attribution = "Data source: Meteoroloji Genel Müdürlüğü (MGM)",
            docs = "https://www.mgm.gov.tr/",
            notes = "Requires Referer/Origin headers. No verified warning feed.",
        )
        upsertProvider(
            ProviderId.MET_OFFICE,
            displayName = "UK Met Office Weather DataHub",
            licence = "Met Office Weather DataHub licence. Free Global Spot plan: " +
                "up to 360 calls/day. Requires a user-supplied API key.",
            attribution = "Powered by Met Office data",
            docs = "https://datahub.metoffice.gov.uk/",
            notes = "Global Spot v1.0.2. Forecast only - no Istanbul ground observations.",
        )
    }

    private suspend fun upsertProvider(
        id: ProviderId,
        displayName: String,
        licence: String,
        attribution: String,
        docs: String,
        notes: String,
    ) {
        val dao = db.providerDao()
        val existing = dao.byCode(id.code)
        dao.upsert(
            ProviderEntity(
                id = existing?.id ?: 0,
                providerCode = id.code,
                displayName = displayName,
                enabled = existing?.enabled ?: true,
                licence = licence,
                attribution = attribution,
                documentationUrl = docs,
                notes = notes,
            )
        )
    }

    suspend fun providerIdOf(code: ProviderId): Long =
        db.providerDao().byCode(code.code)?.id
            ?: error("Provider ${code.code} is not registered")

    suspend fun providerEntity(code: ProviderId): ProviderEntity? =
        db.providerDao().byCode(code.code)

    suspend fun setProviderEnabled(code: ProviderId, enabled: Boolean) {
        db.providerDao().setEnabled(code.code, enabled)
    }

    fun observeProviders(): Flow<List<ProviderEntity>> = db.providerDao().observeAll()

    // ------------------------------------------------------------ collection

    /**
     * Persists one provider's collection inside a single transaction, so a
     * failed transaction never reports success.
     */
    suspend fun saveProviderCollection(
        runId: Long,
        collection: ProviderCollection,
        received: Int,
    ): Int = db.withTransaction {
        val providerId = providerIdOf(collection.provider)
        var saved = 0

        collection.location?.let { loc ->
            val locationDao = db.locationDao()
            val existing = loc.sourceLocationId?.let {
                locationDao.bySource(providerId, it)
            }
            val id = locationDao.upsert(
                LocationEntity(
                    id = existing?.id ?: 0,
                    providerId = providerId,
                    name = loc.name,
                    country = loc.country,
                    region = loc.region,
                    district = loc.district,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    timezone = loc.timezone,
                    sourceLocationId = loc.sourceLocationId,
                )
            )
            saved++
        }

        if (collection.stations.isNotEmpty()) {
            val providerId = providerIdOf(collection.provider)
            db.stationDao().upsertAll(
                collection.stations.map { s ->
                    StationEntity(
                        providerId = providerId,
                        externalStationId = s.externalId,
                        stationName = s.name,
                        latitude = s.latitude,
                        longitude = s.longitude,
                        elevation = s.elevation,
                        metadataJson = s.metadataJson,
                    )
                }
            )
            saved += collection.stations.size
        }

        // Raw payloads first - provenance is preserved even if parsing changes.
        collection.rawPayloads.forEach { raw ->
            db.rawResponseDao().insert(
                RawResponseEntity(
                    providerId = providerId,
                    collectionRunId = runId,
                    productOrEndpoint = raw.endpoint,
                    retrievedAt = raw.retrievedAt,
                    httpStatus = raw.httpStatus,
                    contentType = raw.contentType,
                    responseBody = raw.body,
                    responseHash = sha256(raw.body),
                    requestMetadata = raw.requestMetadata,
                )
            )
            saved++
        }

        collection.observations.forEach { obs ->
            db.observationDao().insert(
                WeatherObservationEntity(
                    providerId = providerId,
                    locationId = null,
                    stationId = null,
                    sourceRecordId = obs.sourceRecordId,
                    observedAt = obs.observedAt,
                    collectedAt = obs.collectedAt,
                    normalizedJson = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(MeasurementJson.serializer()),
                        obs.measurements.map { MeasurementJson.from(it) }
                    ),
                    providerSpecificJson = obs.originalJson,
                    dataType = DataType.OBSERVATION.name.lowercase(),
                    collectionRunId = runId,
                )
            )
            saved++
            saveMetadata(providerId, obs.measurements, DataType.OBSERVATION)
        }

        collection.forecastRuns.forEach { run ->
            val forecastRunId = db.forecastDao().insert(
                ForecastRunEntity(
                    providerId = providerId,
                    locationId = null,
                    productName = run.product,
                    issuedAt = run.issuedAt,
                    collectedAt = run.collectedAt,
                    sourceForecastId = run.sourceForecastId,
                    rawResponseId = null,
                    collectionRunId = runId,
                    rowCount = run.rows.size,
                )
            )
            saved++
            val values = run.rows.flatMap { row ->
                row.measurements.map { m ->
                    ForecastValueEntity(
                        forecastRunId = forecastRunId,
                        validAt = row.validAt,
                        forecastPeriod = row.period,
                        parameterCode = m.code,
                        parameterName = m.name,
                        numericValue = m.value,
                        textValue = m.text,
                        unit = m.unit,
                        originalValueJson = m.originalValue,
                        category = m.category,
                        availability = m.availability.name.lowercase(),
                        dataType = DataType.FORECAST.name.lowercase(),
                    )
                }
            }
            if (values.isNotEmpty()) db.forecastDao().insertValues(values)
            saved += values.size
            saveMetadata(providerId, run.rows.flatMap { it.measurements }, DataType.FORECAST)
        }

        collection.warnings.forEach { w ->
            db.warningDao().insertAll(
                listOf(
                    WeatherWarningEntity(
                        providerId = providerId,                        locationId = null,
                        sourceWarningId = w.sourceWarningId,
                        issuedAt = w.issuedAt,
                        validFrom = w.validFrom,
                        validTo = w.validTo,
                        severity = w.severity,
                        title = w.title,
                        description = w.description,
                        areasJson = json.encodeToString(
                            ListSerializer(String.serializer()),
                            w.areas,
                        ),
                        rawData = w.raw,
                        collectionRunId = runId,
                    )
                )
            )
            saved++
        }

        saved
    }

    private suspend fun saveMetadata(
        providerId: Long,
        measurements: List<Measurement>,
        dataType: DataType,
    ) {
        val distinct = measurements.distinctBy { it.code }
        if (distinct.isEmpty()) return
        db.metadataDao().upsertAll(
            distinct.map { m ->
                WeatherMetadataEntity(
                    providerId = providerId,
                    parameterCode = m.code,
                    parameterName = m.name,
                    unit = m.unit,
                    description = null,
                    mappingMetadata = """{"category":"${m.category}","originalValue":${m.originalValue?.let { "\"$it\"" } ?: "null"}}""",
                    category = m.category,
                    dataType = dataType.name.lowercase(),
                )
            }
        )
    }

    suspend fun startRun(
        trigger: String,
        intervalMinutes: Int,
        requestedProviders: Int,
        appVersion: String,
    ): Long = db.runDao().insert(
        CollectionRunEntity(
            startedAt = System.currentTimeMillis(),
            configuredIntervalMinutes = intervalMinutes,
            overallStatus = "running",
            appVersion = appVersion,
            requestedProviderCount = requestedProviders,
            trigger = trigger,
        )
    )

    suspend fun finishRun(
        runId: Long,
        successCount: Int,
        failureCount: Int,
    ) {
        val status = when {
            failureCount == 0 && successCount > 0 -> "success"
            successCount == 0 -> "failed"
            else -> "partial"
        }
        db.runDao().finish(runId, System.currentTimeMillis(), status, successCount, failureCount)
    }

    suspend fun recordProviderResult(
        runId: Long,
        provider: ProviderId,
        success: Boolean,
        startedAt: Long,
        finishedAt: Long,
        recordsReceived: Int,
        recordsSaved: Int,
        errorCode: String?,
        errorMessage: String?,
        endpoints: List<String>,
    ) {
        val providerId = providerIdOf(provider)
        db.providerResultDao().insert(
            ProviderCollectionResultEntity(
                collectionRunId = runId,
                providerId = providerId,
                status = if (success) "success" else "failed",
                startedAt = startedAt,
                finishedAt = finishedAt,
                recordsReceived = recordsReceived,
                recordsSaved = recordsSaved,
                errorCode = errorCode,
                errorMessage = errorMessage,
                endpoints = json.encodeToString(
                    ListSerializer(String.serializer()),
                    endpoints,
                ),
            )
        )
        if (!success) {
            db.errorDao().insert(
                CollectionErrorEntity(
                    providerId = providerId,
                    collectionRunId = runId,
                    occurredAt = finishedAt,
                    errorType = errorCode ?: "unknown",
                    message = errorMessage ?: "Unknown provider failure",
                    retryable = errorCode in setOf("network", "http_429", "http_5xx"),
                    endpoint = endpoints.firstOrNull(),
                    httpStatus = errorCode?.removePrefix("http_")?.toIntOrNull(),
                )
            )        }
    }

    // ------------------------------------------------------------ read paths

    fun observeLatestObservation(code: String): Flow<WeatherObservationEntity?> =
        db.observationDao().observeLatest(code)

    fun observeObservations(code: String, limit: Int = 200): Flow<List<WeatherObservationEntity>> =
        db.observationDao().observeHistory(code, limit)

    fun observeLatestForecastRun(code: String, product: String): Flow<ForecastRunEntity?> =
        db.forecastDao().observeLatestRun(code, product)

    fun observeForecastValues(forecastRunId: Long): Flow<List<ForecastValueEntity>> =
        db.forecastDao().observeValues(forecastRunId)

    fun observeForecastRunHistory(code: String, product: String, limit: Int = 20): Flow<List<ForecastRunEntity>> =
        db.forecastDao().observeRunHistory(code, product, limit)

    fun observeRuns(limit: Int = 100): Flow<List<CollectionRunEntity>> =
        db.runDao().observeRuns(limit)

    fun observeProviderResults(limit: Int = 100): Flow<List<ProviderCollectionResultEntity>> =
        db.providerResultDao().observeRecent(limit)

    fun observeLastProviderResult(code: String): Flow<ProviderCollectionResultEntity?> =
        db.providerResultDao().observeLastForProvider(code)

    fun observeErrors(limit: Int = 50): Flow<List<CollectionErrorEntity>> =
        db.errorDao().observeRecent(limit)

    /** Raw payloads from every provider, newest first. */
    fun observeRawResponses(limit: Int = 50): Flow<List<RawResponseEntity>> =
        db.rawResponseDao().observeAll(limit)

    /** Raw payloads from one provider, newest first. */
    fun observeRawResponsesFor(providerCode: String, limit: Int = 50): Flow<List<RawResponseEntity>> =
        db.rawResponseDao().observeRecentForProvider(providerCode, limit)

    fun observeWarnings(): Flow<List<WeatherWarningEntity>> = db.warningDao().observeAll()

    fun observeMetadata(): Flow<List<WeatherMetadataEntity>> = db.metadataDao().observeAll()

    fun observeStations(providerId: Long): Flow<List<StationEntity>> =
        db.stationDao().observeForProvider(providerId)

    /** Every stored station, from every provider. */
    fun observeAllStations(): Flow<List<StationEntity>> = db.stationDao().observeAll()

    /** Full database statistics for the Settings screen. */
    fun observeStorageStats(): Flow<StorageStats> = combine(
        combine(
            db.observationDao().observeCount(),
            db.forecastDao().observeValueCount(),
        ) { obs, forecast -> obs to forecast },
        combine(
            db.rawResponseDao().observeCount(),
            db.runDao().observeCount(),
        ) { raw, runs -> raw to runs },
        combine(
            db.errorDao().observeCount(),
            db.warningDao().observeCount(),
        ) { errors, warnings -> errors to warnings },
    ) { counts, history, diagnostics ->
        StorageStats(
            observations = counts.first,
            forecastValues = counts.second,
            rawResponses = history.first,
            runs = history.second,
            errors = diagnostics.first,
            warnings = diagnostics.second,
        )
    }

    data class StorageStats(
        val observations: Int,
        val forecastValues: Int,
        val rawResponses: Int,
        val runs: Int,
        val errors: Int,
        val warnings: Int,
    )

    /** Raw payload bodies for export. */
    suspend fun allRawResponses(providerCode: String, limit: Int = 1000): List<RawResponseEntity> =
        db.rawResponseDao().observeRecentForProvider(providerCode, limit).first()

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** JSON projection of a normalized measurement (for the observation payload). */
    @kotlinx.serialization.Serializable
    data class MeasurementJson(
        val code: String,
        val name: String,
        val value: Double? = null,
        val text: String? = null,
        val unit: String? = null,
        val originalValue: String? = null,
        val category: String,
        val availability: String,
    ) {
        companion object {
            fun from(m: Measurement) = MeasurementJson(
                code = m.code,
                name = m.name,
                value = m.value,
                text = m.text,
                unit = m.unit,
                originalValue = m.originalValue,
                category = m.category,
                availability = m.availability.name.lowercase(),
            )
        }
    }
}
