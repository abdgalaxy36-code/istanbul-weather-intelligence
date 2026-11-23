package com.istanbul.weatherintel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities mirror the requirement spec (section 6) table-for-table:
 *  1 providers            5 provider_collection_results  9 forecast_values
 *  2 locations            6 raw_responses                10 weather_warnings
 *  3 stations             7 weather_observations         11 weather_metadata
 *  4 collection_runs      8 forecast_runs                12 collection_errors
 *  13 app_settings -> DataStore (SettingsRepository), not Room.
 *
 * Rules enforced everywhere:
 *  - Nullable columns where a provider may not supply a value.
 *  - Never fabricate: missing stays NULL, never 0.
 *  - Timestamps are UTC epoch millis.
 *  - Original provider value + unit always retained next to normalized value.
 *  - Historical rows are never overwritten (no UPSERT on forecast history).
 */

@Entity(
    tableName = "providers",
    indices = [Index(value = ["providerCode"], unique = true)]
)
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerCode: String,             // MGM / MET_OFFICE
    val displayName: String,
    val enabled: Boolean = true,
    val licence: String? = null,
    val attribution: String? = null,
    val documentationUrl: String? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["providerId", "sourceLocationId"]),
        Index("latitude"),
    ]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,                 // FK -> providers.id
    val name: String,
    val country: String = "Turkey",
    val region: String?,                  // province
    val district: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String = "Europe/Istanbul",
    val sourceLocationId: String?,        // provider's own id for the point
)

@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["providerId", "externalStationId"], unique = true),
        Index("providerId"),
        Index("latitude"),
    ]
)
data class StationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,                 // FK -> providers.id
    val externalStationId: String,
    val stationName: String,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double? = null,
    val metadataJson: String = "{}",      // full provider metadata, verbatim
    val locationId: Long? = null,
    val isSelected: Boolean = false,
)

@Entity(
    tableName = "collection_runs",
    indices = [Index("startedAt"), Index("overallStatus")]
)
data class CollectionRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val configuredIntervalMinutes: Int,
    val overallStatus: String,            // success | partial | failed
    val appVersion: String,
    val requestedProviderCount: Int,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val trigger: String = "periodic",     // periodic | manual | app_open
)

@Entity(
    tableName = "provider_collection_results",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionRunId"), Index("providerId"), Index("status")]
)
data class ProviderCollectionResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionRunId: Long,
    val providerId: Long,                 // FK -> providers.id
    val status: String,                   // success | failed
    val startedAt: Long,
    val finishedAt: Long,
    val recordsReceived: Int = 0,
    val recordsSaved: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val endpoints: String = "[]",         // non-secret endpoint list
)

@Entity(
    tableName = "raw_responses",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionRunId"), Index("providerId"), Index("retrievedAt")]
)
data class RawResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val collectionRunId: Long,
    val productOrEndpoint: String,
    val retrievedAt: Long,
    val httpStatus: Int?,
    val contentType: String?,
    val responseBody: String,             // verbatim payload (never secrets)
    val responseHash: String,             // sha-256 of body
    val requestMetadata: String = "{}",   // non-secret request info only
)

@Entity(
    tableName = "weather_observations",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("providerId"),
        Index("observedAt"),
        Index("collectedAt"),
        Index(value = ["providerId", "observedAt"]),
        Index("locationId"),
        Index("sourceRecordId"),
    ]
)
data class WeatherObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val locationId: Long?,
    val stationId: Long?,
    val sourceRecordId: String?,          // provider's own record id
    val observedAt: Long,                 // observation valid time (UTC)
    val collectedAt: Long,                // collection time (UTC)
    val normalizedJson: String,           // common normalized fields
    val providerSpecificJson: String,     // original provider fields, verbatim
    val dataType: String = "observation", // observation | forecast | warning | climatology
    val collectionRunId: Long,
    val providerNotes: String? = null,      // added in schema v2 (free-form provider note)
)

@Entity(
    tableName = "forecast_runs",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("providerId"),
        Index("issuedAt"),
        Index("collectedAt"),
        Index("productName"),
        Index("locationId"),
    ]
)
data class ForecastRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val locationId: Long?,
    val productName: String,              // hourly | three-hourly | daily
    val issuedAt: Long?,                  // model run / issue time (nullable)
    val collectedAt: Long,
    val sourceForecastId: String?,        // provider run identifier
    val rawResponseId: Long?,
    val collectionRunId: Long,
    val rowCount: Int = 0,
)

@Entity(
    tableName = "forecast_values",
    foreignKeys = [
        ForeignKey(
            entity = ForecastRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["forecastRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("forecastRunId"),
        Index("validAt"),
        Index("parameterCode"),
        Index(value = ["forecastRunId", "validAt"]),
    ]
)
data class ForecastValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forecastRunId: Long,
    val validAt: Long,                    // forecast valid time (UTC)
    val forecastPeriod: String,           // hourly | three-hourly | daily | day | night
    val parameterCode: String,            // stable machine key
    val parameterName: String,            // display label
    val numericValue: Double?,            // NULL when provider gave none
    val textValue: String?,
    val unit: String?,                    // original unit as supplied
    val originalValueJson: String?,       // original raw value, verbatim
    val category: String,                 // grouping for comparison UI
    val availability: String = "available", // available | null | unsupported
    val dataType: String = "forecast",
)

@Entity(
    tableName = "weather_warnings",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId"), Index("issuedAt"), Index("validFrom"), Index("locationId")]
)
data class WeatherWarningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val locationId: Long?,
    val sourceWarningId: String?,
    val issuedAt: Long?,
    val validFrom: Long?,
    val validTo: Long?,
    val severity: String?,
    val title: String?,
    val description: String?,
    val areasJson: String = "[]",
    val rawData: String = "{}",
    val collectionRunId: Long,
)

@Entity(
    tableName = "weather_metadata",
    indices = [
        Index(value = ["providerId", "parameterCode"], unique = true),
        Index("parameterCode"),
    ]
)
data class WeatherMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val parameterCode: String,
    val parameterName: String,
    val unit: String?,
    val description: String?,
    val mappingMetadata: String = "{}",   // how source field maps to normalized field
    val category: String,
    val dataType: String,                 // observation | forecast | warning
)

@Entity(
    tableName = "collection_errors",
    foreignKeys = [
        ForeignKey(
            entity = CollectionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionRunId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId"), Index("occurredAt"), Index("collectionRunId")]
)
data class CollectionErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val collectionRunId: Long?,
    val occurredAt: Long,
    val errorType: String,                // network | http | parse | auth | validation
    val message: String,
    val retryable: Boolean,
    val endpoint: String?,
    val httpStatus: Int?,
)
