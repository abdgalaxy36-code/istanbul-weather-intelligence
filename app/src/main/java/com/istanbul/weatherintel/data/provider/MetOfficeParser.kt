package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ForecastRow
import com.istanbul.weatherintel.data.model.ForecastRun
import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ProviderId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Pure parser for the Met Office Global Spot site-specific GeoJSON response.
 *
 * Response shape (official documentation, v1.0.2):
 *   features[0].properties.location.name
 *   features[0].properties.requestPointDistance
 *   features[0].properties.modelRunDate
 *   features[0].properties.timeSeries[] -> each entry has `time` + parameters
 *
 * Units are preserved exactly as supplied by the provider (degC, m/s, %, Pa, m,
 * mm, mm/hr, cm, 1, degrees).
 */
object MetOfficeParser {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Point(
        val locationName: String?,
        val requestPointDistance: Double?,
        val modelRunDate: Long?,
        val timeSeries: List<TimePoint>,
        val elevation: Double?,
    )

    data class TimePoint(val time: Long, val parameters: Map<String, JsonElementValue>)

    class JsonElementValue(val element: kotlinx.serialization.json.JsonElement?)

    /** Parses a full GeoJSON document. Returns null when the shape is invalid. */
    fun parsePoint(body: String): Point? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        val features = root["features"] as? JsonArray ?: return null
        val feature = features.firstOrNull()?.jsonObject ?: return null
        val properties = feature["properties"] as? JsonObject ?: return null
        val location = properties["location"] as? JsonObject
        val series = properties["timeSeries"] as? JsonArray ?: JsonArray(emptyList())

        val points = series.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val time = obj["time"]?.asString()?.let { parseTime(it) } ?: return@mapNotNull null
            val params = LinkedHashMap<String, JsonElementValue>()
            obj.forEach { (key, value) -> if (key != "time") params[key] = JsonElementValue(value) }
            TimePoint(time, params)
        }
        val elevation = (feature["geometry"] as? JsonObject)
            ?.get("coordinates")?.jsonArray?.getOrNull(2)?.let { element ->
                (element as? JsonPrimitive)?.doubleOrNull
            }

        return Point(
            locationName = location?.get("name")?.asString(),
            requestPointDistance = (properties["requestPointDistance"] as? JsonPrimitive)?.doubleOrNull,
            modelRunDate = properties["modelRunDate"]?.asString()?.let { parseTime(it) },
            timeSeries = points,
            elevation = elevation,
        )
    }

    /** Converts a parsed point into normalized forecast rows. */
    fun parse(body: String, product: String, category: String): List<ForecastRow>? {
        val point = parsePoint(body) ?: return null
        return point.timeSeries.map { tp ->
            val measurements = tp.parameters.entries
                .sortedBy { it.key }
                .map { (key, holder) -> measurement(key, holder.element, category) }
                .filter { it.availability != Availability.UNSUPPORTED }
            ForecastRow(validAt = tp.time, period = product, measurements = measurements)
        }
    }

    /** Builds a full forecast run from a GeoJSON body. */
    fun parseRun(body: String, product: String, category: String): ForecastRun? {
        val point = parsePoint(body) ?: return null
        if (point.timeSeries.isEmpty()) return null
        val rows = point.timeSeries.map { tp ->
            val measurements = tp.parameters.entries
                .sortedBy { it.key }
                .map { (key, holder) -> measurement(key, holder.element, category) }
                .filter { it.availability != Availability.UNSUPPORTED }
            ForecastRow(validAt = tp.time, period = product, measurements = measurements)
        }
        return ForecastRun(
            provider = ProviderId.MET_OFFICE,
            product = product,
            issuedAt = point.modelRunDate,
            collectedAt = System.currentTimeMillis(),
            sourceForecastId = point.modelRunDate?.toString(),
            locationName = point.locationName,
            rows = rows,
        )
    }

    private fun measurement(
        key: String,
        value: kotlinx.serialization.json.JsonElement?,
        category: String,
    ): Measurement {
        val meta = MetOfficeParams.meta(key)
        val primitive = value as? JsonPrimitive
        val text = primitive?.takeIf { it !is JsonNull }?.contentOrNull
        val numeric = primitive?.takeIf { it !is JsonNull }?.doubleOrNull
        val isCode = key.contains("WeatherCode", ignoreCase = true) ||
            key.contains("DayNightIndicator", ignoreCase = true)

        val codeLabel = if (isCode) MetOfficeWeatherCodes.label(text) else null

        return Measurement(
            code = key,
            name = meta.first,
            value = if (isCode) null else numeric,
            text = codeLabel ?: text,
            unit = meta.second,
            originalValue = text,
            category = category,
            availability = when {
                value == null || value is JsonNull -> Availability.NULL_VALUE
                else -> Availability.AVAILABLE
            },
        )
    }

    fun parseTime(raw: String): Long? = try {
        java.time.Instant.parse(raw).toEpochMilli()
    } catch (_: Throwable) {
        try {
            java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (_: Throwable) {
            null
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.asString(): String? = when (this) {
    is JsonPrimitive -> if (this is JsonNull) null else contentOrNull
    else -> null
}
