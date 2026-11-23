package com.istanbul.weatherintel.data.model

/**
 * Provider-independent domain models.
 *
 * Every provider adapter maps its own payloads into these types while keeping
 * the original provider payload in [ProviderPayload.originalJson], so no
 * provider-specific field is ever lost.
 */

enum class ProviderId(val code: String, val display: String) {
    MGM("MGM", "MGM (Turkish State Met Service)"),
    MET_OFFICE("MET_OFFICE", "UK Met Office Weather DataHub");

    companion object {
        fun fromCode(code: String): ProviderId =
            entries.firstOrNull { it.code == code } ?: MGM
    }
}

/** One normalized measurement. [value] is null when the provider gave nothing. */
data class Measurement(
    val code: String,                 // stable machine key
    val name: String,                 // display label
    val value: Double?,               // null = not supplied (never zero-filled)
    val text: String? = null,         // textual/coded value (weather code, etc.)
    val unit: String?,                // unit as supplied by the provider
    val originalValue: String?,       // verbatim provider value
    val category: String,             // grouping key for Compare screen
    val availability: Availability = Availability.AVAILABLE,
) {
    val hasValue: Boolean get() = value != null || !text.isNullOrBlank()
}

enum class Availability { AVAILABLE, NULL_VALUE, UNSUPPORTED }

enum class DataType { OBSERVATION, FORECAST, WARNING, CLIMATOLOGY, OTHER }

/** A single observed set of measurements at one location/station. */
data class ObservationRecord(
    val provider: ProviderId,
    val locationName: String,
    val stationExternalId: String?,
    val stationName: String?,
    val observedAt: Long,
    val collectedAt: Long,
    val measurements: List<Measurement>,
    val originalJson: String,
    val sourceRecordId: String?,
)

/** One forecast product (hourly / three-hourly / daily) as returned by a provider. */
data class ForecastRun(
    val provider: ProviderId,
    val product: String,              // "hourly" | "three-hourly" | "daily"
    val issuedAt: Long?,
    val collectedAt: Long,
    val sourceForecastId: String?,
    val locationName: String?,
    val rows: List<ForecastRow>,
)

data class ForecastRow(
    val validAt: Long,
    val period: String,               // hourly | three-hourly | daily | day | night
    val measurements: List<Measurement>,
)

data class WarningRecord(
    val provider: ProviderId,
    val sourceWarningId: String?,
    val issuedAt: Long?,
    val validFrom: Long?,
    val validTo: Long?,
    val severity: String?,
    val title: String?,
    val description: String?,
    val areas: List<String>,
    val raw: String,
)

/** Verbatim HTTP response retained for provenance and export. */
data class RawPayload(
    val provider: ProviderId,
    val endpoint: String,
    val retrievedAt: Long,
    val httpStatus: Int?,
    val contentType: String?,
    val body: String,
    val requestMetadata: String = "{}",
)

/** Location / station metadata for a provider. */
data class LocationRecord(
    val provider: ProviderId,
    val sourceLocationId: String?,
    val name: String,
    val country: String = "Turkey",
    val region: String? = null,
    val district: String? = null,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String = "Europe/Istanbul",
)

data class StationRecord(
    val provider: ProviderId,
    val externalId: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
    val metadataJson: String = "{}",
    val region: String? = null,
    val district: String? = null,
)

/** Everything one provider produced during one collection attempt. */
data class ProviderCollection(
    val provider: ProviderId,
    val location: LocationRecord?,
    val stations: List<StationRecord> = emptyList(),
    val observations: List<ObservationRecord> = emptyList(),
    val forecastRuns: List<ForecastRun> = emptyList(),
    val warnings: List<WarningRecord> = emptyList(),
    val rawPayloads: List<RawPayload> = emptyList(),
    val unsupportedProducts: List<String> = emptyList(),
) {
    val recordCount: Int
        get() = observations.size +
            forecastRuns.sumOf { it.rows.size } +
            warnings.size
}

/** Outcome of collecting a single provider (success or failure). */
data class ProviderOutcome(
    val provider: ProviderId,
    val success: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val recordsReceived: Int,
    val recordsSaved: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val endpoints: List<String>,
    val retryable: Boolean,
)

/** Outcome of a full collection run across all enabled providers. */
data class CollectionOutcome(
    val runId: Long,
    val providerOutcomes: List<ProviderOutcome>,
) {
    val successCount: Int get() = providerOutcomes.count { it.success }
    val failureCount: Int get() = providerOutcomes.count { !it.success }
    val overallStatus: String
        get() = when {
            providerOutcomes.isEmpty() -> "failed"
            failureCount == 0 -> "success"
            successCount == 0 -> "failed"
            else -> "partial"
        }
}

/** Comparison of one parameter across the two providers. */
data class ComparisonRow(
    val category: String,
    val code: String,                 // canonical code when paired, else "<provider>:<rawCode>"
    val name: String,
    val unit: String?,                // canonical unit for the row (null for text fields)
    val mgm: ComparisonValue?,
    val metOffice: ComparisonValue?,
    val comparable: Boolean,
    val differenceUnit: String?,      // unit the difference is expressed in
    val note: String?,
)

data class ComparisonValue(
    val value: Double?,
    val text: String?,
    val unit: String?,
    val originalValue: String?,
    val validAt: Long?,
    val issuedAt: Long?,
    val collectedAt: Long,
    val source: String,
    val dataType: DataType,
    val location: String?,
    val availability: Availability,
) {
    fun display(): String = when {
        value != null -> formatNumber(value)
        !text.isNullOrBlank() -> text
        availability == Availability.UNSUPPORTED -> "Unsupported"
        else -> "Not provided"
    }
}

fun formatNumber(v: Double): String {
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", v)
}
