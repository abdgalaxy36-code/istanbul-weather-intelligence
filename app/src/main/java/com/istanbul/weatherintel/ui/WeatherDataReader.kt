package com.istanbul.weatherintel.ui

import com.istanbul.weatherintel.data.db.ForecastValueEntity
import com.istanbul.weatherintel.data.db.WeatherObservationEntity
import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.DataType
import com.istanbul.weatherintel.data.model.Measurement
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Helpers that turn stored rows into UI-friendly models. */
object WeatherDataReader {

    private val json = Json { ignoreUnknownKeys = true }

    /** Read-only view over a stored observation. */
    class ObservedView(
        val provider: String,
        val observedAt: Long,
        val collectedAt: Long,
        val stationName: String?,
        val measurements: List<Measurement>,
        val providerSpecificJson: String,
        val dataType: DataType = DataType.OBSERVATION,
    ) {
        fun value(code: String): Double? =
            measurements.firstOrNull { it.code.equals(code, ignoreCase = true) }?.value

        fun text(code: String): String? =
            measurements.firstOrNull {
                it.code.equals(code, ignoreCase = true) ||
                    it.code.equals(code + "Adi", ignoreCase = true)
            }?.text
    }

    fun readObservation(entity: WeatherObservationEntity?): ObservedView? {
        if (entity == null) return null
        val measurements = runCatching {
            json.decodeFromString(
                ListSerializer(StoredMeasurement.serializer()),
                entity.normalizedJson,
            ).map { it.toMeasurement() }
        }.getOrDefault(emptyList())
        return ObservedView(
            provider = if (entity.providerId == 1L) "MGM" else "Met Office",
            observedAt = entity.observedAt,
            collectedAt = entity.collectedAt,
            stationName = entity.sourceRecordId,
            measurements = measurements,
            providerSpecificJson = entity.providerSpecificJson,
            dataType = DataType.OBSERVATION,
        )
    }

    /** Groups stored forecast values by valid time. */
    fun groupByValidTime(values: List<ForecastValueEntity>): Map<Long, List<ForecastValueEntity>> =
        values.groupBy { it.validAt }.toSortedMap()

    /**
     * A single time series extracted from stored observations.
     *
     * Only real stored values are included - an observation that did not carry
     * the parameter contributes no point rather than a zero.
     */
    data class Series(
        val label: String,
        val unit: String?,
        val points: List<Pair<Long, Double>>,
    )

    /**
     * Builds a time series for one parameter code from stored observations.
     * The first code in [codes] that an observation actually carries is used.
     */
    fun observationSeries(
        observations: List<WeatherObservationEntity>,
        label: String,
        unit: String?,
        codes: List<String>,
    ): Series {
        val points = observations.mapNotNull { entity ->
            val measurements = runCatching {
                json.decodeFromString(
                    ListSerializer(StoredMeasurement.serializer()),
                    entity.normalizedJson,
                )
            }.getOrDefault(emptyList())
            val match = codes.firstNotNullOfOrNull { code ->
                measurements.firstOrNull { it.code.equals(code, ignoreCase = true) }
            }
            val value = match?.value ?: return@mapNotNull null
            entity.observedAt to value
        }.sortedBy { it.first }
        return Series(label = label, unit = unit, points = points)
    }

    fun toMeasurement(value: ForecastValueEntity): Measurement = Measurement(
        code = value.parameterCode,
        name = value.parameterName,
        value = value.numericValue,
        text = value.textValue,
        unit = value.unit,
        originalValue = value.originalValueJson,
        category = value.category,
        availability = when (value.availability) {
            "available" -> Availability.AVAILABLE
            "unsupported" -> Availability.UNSUPPORTED
            else -> Availability.NULL_VALUE
        },
    )

    @kotlinx.serialization.Serializable
    data class StoredMeasurement(
        val code: String,
        val name: String,
        val value: Double? = null,
        val text: String? = null,
        val unit: String? = null,
        val originalValue: String? = null,
        val category: String,
        val availability: String,
    ) {
        fun toMeasurement() = Measurement(
            code = code,
            name = name,
            value = value,
            text = text,
            unit = unit,
            originalValue = originalValue,
            category = category,
            availability = when (availability) {
                "available" -> Availability.AVAILABLE
                "unsupported" -> Availability.UNSUPPORTED
                else -> Availability.NULL_VALUE
            },
        )
    }
}

