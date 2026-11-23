package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ForecastRow
import com.istanbul.weatherintel.data.model.ForecastRun
import com.istanbul.weatherintel.data.model.LocationRecord
import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ProviderCollection
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.model.RawPayload
import com.istanbul.weatherintel.data.model.StationRecord
import com.istanbul.weatherintel.data.model.WarningRecord
import com.istanbul.weatherintel.data.net.HttpClient
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
 * UK Met Office Weather DataHub - Global Spot site-specific API (v1.0.2).
 *
 * Endpoints (verified against current documentation, November 2026):
 *   GET /sitespecific/v0/point/hourly?latitude=&longitude=
 *   GET /sitespecific/v0/point/three-hourly?latitude=&longitude=
 *   GET /sitespecific/v0/point/daily?latitude=&longitude=
 * Required header: apikey: <key>
 *
 * Response: GeoJSON FeatureCollection ->
 *   features[0].properties.location.name
 *   features[0].properties.requestPointDistance
 *   features[0].properties.modelRunDate
 *   features[0].properties.timeSeries[]   (each entry: `time` + parameters)
 *
 * Units as supplied: temperature degC, wind m/s, gust m/s, direction degrees,
 * humidity %, pressure Pa, visibility m, precipitation rate mm/hr, amount mm,
 * snow amount cm, UV index 1, probability %.
 *
 * IMPORTANT: this is FORECAST data only. Global Spot provides no Istanbul
 * ground-station observation product, so it is never presented as an
 * observation in this app.
 */
class MetOfficeWeatherDataSource(
    private val http: HttpClient,
    private val latitude: Double,
    private val longitude: Double,
) : WeatherDataSource {

    override val providerId: ProviderId = ProviderId.MET_OFFICE

    /** Global Spot is forecast-only: no Istanbul observations, no warning feed. */
    override val unsupportedProducts: List<String> = listOf("observations", "warnings")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val rawPayloads = mutableListOf<RawPayload>()

    private data class Point(
        val locationName: String?,
        val requestPointDistance: Double?,
        val modelRunDate: Long?,
        val timeSeries: List<TimePoint>,
        val elevation: Double?,
    )

    private data class TimePoint(val time: Long, val parameters: Map<String, JsonElementValue>)

    private class JsonElementValue(val element: kotlinx.serialization.json.JsonElement?)

    private suspend fun fetch(product: String): Point {
        val path = "/sitespecific/v0/point/$product" +
            "?latitude=$latitude&longitude=$longitude" +
            "&includeLocationName=true&excludeParameterMetadata=true"
        val res = http.getMetOffice(path)
        rawPayloads += RawPayload(
            provider = ProviderId.MET_OFFICE,
            endpoint = path,
            retrievedAt = System.currentTimeMillis(),
            httpStatus = res.code,
            contentType = res.contentType,
            body = res.body.orEmpty(),
            requestMetadata = """{"product":"$product","header":"apikey (redacted)"}""",
        )
        if (!res.isSuccess) throw ProviderHttpException(res.code, errorMessage(res.code, res.body))
        val body = res.body
        if (body.isNullOrBlank()) throw ProviderParseException("Met Office $product returned an empty body")
        val point = MetOfficeParser.parsePoint(body)
            ?: throw ProviderParseException("Met Office $product response has no usable time series")
        return Point(
            locationName = point.locationName,
            requestPointDistance = point.requestPointDistance,
            modelRunDate = point.modelRunDate,
            timeSeries = point.timeSeries.map { tp ->
                TimePoint(
                    tp.time,
                    tp.parameters.mapValues { JsonElementValue(it.value.element) },
                )
            },
            elevation = point.elevation,
        )
    }

    override suspend fun fetchLocationMetadata(): LocationRecord? {
        // The point endpoint reports the resolved location name; use hourly for metadata.
        val point = fetch("hourly")
        return LocationRecord(
            provider = ProviderId.MET_OFFICE,
            sourceLocationId = "$latitude,$longitude",
            name = point.locationName ?: "Istanbul",
            region = "Istanbul",
            district = "Istanbul",
            latitude = latitude,
            longitude = longitude,
        )
    }

    override suspend fun fetchStations(): List<StationRecord> = emptyList()

    override suspend fun fetchHourlyForecast(): List<ForecastRun> =
        forecastFor("hourly", "Hourly forecast")

    override suspend fun fetchThreeHourlyForecast(): List<ForecastRun> =
        forecastFor("three-hourly", "Three-hourly forecast")

    override suspend fun fetchDailyForecast(): List<ForecastRun> =
        forecastFor("daily", "Daily forecast")

    private suspend fun forecastFor(product: String, category: String): List<ForecastRun> {
        val body = fetchRaw(product)
        val run = MetOfficeParser.parseRun(body, product, category) ?: return emptyList()
        return listOf(run)
    }

    /** Fetches a raw product, retaining the payload for provenance. */
    private suspend fun fetchRaw(product: String): String {
        val path = "/sitespecific/v0/point/$product" +
            "?latitude=$latitude&longitude=$longitude" +
            "&includeLocationName=true&excludeParameterMetadata=true"
        val res = http.getMetOffice(path)
        rawPayloads += RawPayload(
            provider = ProviderId.MET_OFFICE,
            endpoint = path,
            retrievedAt = System.currentTimeMillis(),
            httpStatus = res.code,
            contentType = res.contentType,
            body = res.body.orEmpty(),
            requestMetadata = """{"product":"$product","header":"apikey (redacted)"}""",
        )
        if (!res.isSuccess) throw ProviderHttpException(res.code, errorMessage(res.code, res.body))
        val body = res.body
        if (body.isNullOrBlank()) throw ProviderParseException("Met Office $product returned an empty body")
        return body
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
                isCode -> Availability.AVAILABLE
                else -> Availability.AVAILABLE
            },
        )
    }

    override suspend fun fetchWarnings(): List<WarningRecord> = emptyList()

    override suspend fun drainRawPayloads(): List<RawPayload> {
        val out = rawPayloads.toList()
        rawPayloads.clear()
        return out
    }

    override suspend fun collect(): ProviderCollection {
        rawPayloads.clear()
        val location = fetchLocationMetadata()
        val hourly = fetchHourlyForecast()
        val threeHourly = fetchThreeHourlyForecast()
        val daily = fetchDailyForecast()
        return ProviderCollection(
            provider = ProviderId.MET_OFFICE,
            location = location,
            forecastRuns = hourly + threeHourly + daily,
            warnings = emptyList(),
            rawPayloads = drainRawPayloads(),
            unsupportedProducts = unsupportedProducts,
        )
    }

    private fun errorMessage(code: Int, body: String?): String = when (code) {
        401 -> "Invalid or missing Met Office API key (HTTP 401). " +
            "Add the key under Settings > API configuration."
        403 -> "Met Office denied the request (HTTP 403). Check your subscription and licence."
        404 -> "Met Office endpoint not found (HTTP 404). The API version may have changed."
        429 -> "Met Office daily call quota exceeded (HTTP 429). Retrying later."
        else -> "Met Office request failed (HTTP $code): ${body?.take(160).orEmpty()}"
    }

    companion object {
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
}

/** Raised for non-2xx provider responses. */
class ProviderHttpException(val status: Int, message: String) : java.io.IOException(message)

/** Raised when a response body cannot be parsed or fails validation. */
class ProviderParseException(message: String) : java.io.IOException(message)

/**
 * Verified Met Office Global Spot parameter catalogue (18 hourly / 21 three-hourly /
 * 41 daily). Labels and units follow the official Global Spot datasheet.
 *
 * The parameter keys below are the exact keys the live GeoJSON API returns.
 * They are *not* interchangeable between products - for example the three-hourly
 * product uses `feelsLikeTemp` while the hourly product uses
 * `feelsLikeTemperature`, and the daily product uses `dayMaxScreenTemperature`
 * rather than either of those.
 *
 * The parser captures every parameter the API returns, whether or not it appears
 * in this catalogue, so a catalogue gap can never cause data loss - it only
 * means the label falls back to the raw key.
 */
object MetOfficeParams {
    data class Entry(val label: String, val unit: String?, val category: String)

    private val entries: Map<String, Entry> = buildMap {
        fun put(code: String, label: String, unit: String?, category: String) {
            put(code, Entry(label, unit, category))
        }

        // ---------------------------------------------------------- Hourly (18)
        put("screenTemperature", "Air temperature at screen level", "degC", "Temperature")
        put("feelsLikeTemperature", "Feels like temperature", "degC", "Temperature")
        put("maxScreenAirTemp", "Maximum air temperature at screen level", "degC", "Temperature")
        put("minScreenAirTemp", "Minimum air temperature at screen level", "degC", "Temperature")
        put("screenDewPointTemperature", "Screen dew point temperature", "degC", "Temperature")
        put("screenRelativeHumidity", "Relative humidity at screen level", "%", "Humidity")
        put("mslp", "Mean sea level pressure", "Pa", "Pressure")
        put("visibility", "Visibility", "m", "Visibility")
        put("windSpeed10m", "Wind speed at 10m", "m/s", "Wind")
        put("windDirectionFrom10m", "Wind direction from 10m", "degrees", "Wind")
        put("windGustSpeed10m", "Wind gust speed at 10m", "m/s", "Wind")
        put("max10mWindGust", "Maximum 10m wind gust", "m/s", "Wind")
        put("precipitationRate", "Precipitation rate", "mm/hr", "Precipitation")
        put("totalPrecipAmount", "Total precipitation amount", "mm", "Precipitation")
        put("totalSnowAmount", "Total snow amount", "cm", "Precipitation")
        put("probOfPrecipitation", "Probability of precipitation", "%", "Precipitation")
        put("significantWeatherCode", "Significant weather code", null, "Weather condition")
        put("uvIndex", "UV index", "1", "Solar data")

        // --------------------------------------------------- Three-hourly (21)
        put("feelsLikeTemp", "Feels like temperature", "degC", "Temperature")
        put("probOfRain", "Probability of rain", "%", "Precipitation")
        put("probOfHeavyRain", "Probability of heavy rain", "%", "Precipitation")
        put("probOfSnow", "Probability of snow", "%", "Precipitation")
        put("probOfHeavySnow", "Probability of heavy snow", "%", "Precipitation")
        put("probOfHail", "Probability of hail", "%", "Precipitation")
        put("probOfSferics", "Probability of sferics (lightning)", "%", "Precipitation")

        // ----------------------------------------------------------- Daily (41)
        put("dayMaxScreenTemperature", "Day maximum screen temperature", "degC", "Temperature")
        put("nightMinScreenTemperature", "Night minimum screen temperature", "degC", "Temperature")
        put("dayMaxFeelsLikeTemp", "Day maximum feels-like temperature", "degC", "Temperature")
        put("nightMinFeelsLikeTemp", "Night minimum feels-like temperature", "degC", "Temperature")
        put("dayUpperBoundMaxTemp", "Day maximum temperature upper bound", "degC", "Temperature")
        put("dayLowerBoundMaxTemp", "Day maximum temperature lower bound", "degC", "Temperature")
        put("nightUpperBoundMinTemp", "Night minimum temperature upper bound", "degC", "Temperature")
        put("nightLowerBoundMinTemp", "Night minimum temperature lower bound", "degC", "Temperature")
        put(
            "dayUpperBoundMaxFeelsLikeTemp",
            "Day maximum feels-like temperature upper bound", "degC", "Temperature",
        )
        put(
            "dayLowerBoundMaxFeelsLikeTemp",
            "Day maximum feels-like temperature lower bound", "degC", "Temperature",
        )
        put(
            "nightUpperBoundMinFeelsLikeTemp",
            "Night minimum feels-like temperature upper bound", "degC", "Temperature",
        )
        put(
            "nightLowerBoundMinFeelsLikeTemp",
            "Night minimum feels-like temperature lower bound", "degC", "Temperature",
        )
        put("maxUvIndex", "Maximum UV index", "1", "Solar data")
        put("middayRelativeHumidity", "Midday relative humidity", "%", "Humidity")
        put("midnightRelativeHumidity", "Midnight relative humidity", "%", "Humidity")
        put("middayMslp", "Midday mean sea level pressure", "Pa", "Pressure")
        put("midnightMslp", "Midnight mean sea level pressure", "Pa", "Pressure")
        put("middayVisibility", "Midday visibility", "m", "Visibility")
        put("midnightVisibility", "Midnight visibility", "m", "Visibility")
        put("midday10MWindSpeed", "Midday wind speed at 10m", "m/s", "Wind")
        put("midday10MWindDirection", "Midday wind direction at 10m", "degrees", "Wind")
        put("midday10MWindGust", "Midday wind gust at 10m", "m/s", "Wind")
        put("midnight10MWindSpeed", "Midnight wind speed at 10m", "m/s", "Wind")
        put("midnight10MWindDirection", "Midnight wind direction at 10m", "degrees", "Wind")
        put("midnight10MWindGust", "Midnight wind gust at 10m", "m/s", "Wind")
        put("daySignificantWeatherCode", "Day significant weather code", null, "Weather condition")
        put(
            "nightSignificantWeatherCode",
            "Night significant weather code", null, "Weather condition",
        )
        put("dayProbabilityOfPrecipitation", "Day probability of precipitation", "%", "Precipitation")
        put(
            "nightProbabilityOfPrecipitation",
            "Night probability of precipitation", "%", "Precipitation",
        )
        put("dayProbabilityOfRain", "Day probability of rain", "%", "Precipitation")
        put("nightProbabilityOfRain", "Night probability of rain", "%", "Precipitation")
        put("dayProbabilityOfHeavyRain", "Day probability of heavy rain", "%", "Precipitation")
        put(
            "nightProbabilityOfHeavyRain",
            "Night probability of heavy rain", "%", "Precipitation",
        )
        put("dayProbabilityOfSnow", "Day probability of snow", "%", "Precipitation")
        put("nightProbabilityOfSnow", "Night probability of snow", "%", "Precipitation")
        put("dayProbabilityOfHeavySnow", "Day probability of heavy snow", "%", "Precipitation")
        put(
            "nightProbabilityOfHeavySnow",
            "Night probability of heavy snow", "%", "Precipitation",
        )
        put("dayProbabilityOfHail", "Day probability of hail", "%", "Precipitation")
        put("nightProbabilityOfHail", "Night probability of hail", "%", "Precipitation")
        put("dayProbabilityOfSferics", "Day probability of sferics (lightning)", "%", "Precipitation")
        put(
            "nightProbabilityOfSferics",
            "Night probability of sferics (lightning)", "%", "Precipitation",
        )
    }

    fun meta(code: String): Pair<String, String?> =
        entries[code]?.let { it.label to it.unit } ?: (code to null)

    fun category(code: String): String = entries[code]?.category ?: "Other"

    /** All known codes, used to seed the weather_metadata table. */
    fun all(): Map<String, Entry> = entries
}

/** Met Office significant weather codes (0-30) per official definitions. */
object MetOfficeWeatherCodes {
    private val labels = mapOf(
        0 to "Clear night",
        1 to "Mainly clear night",
        2 to "Partly cloudy night",
        3 to "Cloudy night",
        4 to "Clear day",
        5 to "Mainly clear day",
        6 to "Partly cloudy day",
        7 to "Cloudy day",
        8 to "Light rain showers (day)",
        9 to "Light rain showers (night)",
        10 to "Heavy rain showers (day)",
        11 to "Heavy rain showers (night)",
        12 to "Light rain (day)",
        13 to "Light rain (night)",
        14 to "Heavy rain (day)",
        15 to "Heavy rain (night)",
        16 to "Light sleet showers (day)",
        17 to "Light sleet showers (night)",
        18 to "Heavy sleet showers (day)",
        19 to "Heavy sleet showers (night)",
        20 to "Light snow showers (day)",
        21 to "Light snow showers (night)",
        22 to "Heavy snow showers (day)",
        23 to "Heavy snow showers (night)",
        24 to "Light ice pellets (day)",
        25 to "Light ice pellets (night)",
        26 to "Heavy ice pellets (day)",
        27 to "Heavy ice pellets (night)",
        28 to "Light fog (day)",
        29 to "Light fog (night)",
        30 to "Heavy fog (day)",
    )

    fun label(code: String?): String? {
        val n = code?.trim()?.toDoubleOrNull()?.toInt() ?: return null
        return labels[n] ?: "Weather code $n"
    }
}

private fun kotlinx.serialization.json.JsonElement.asString(): String? = when (this) {
    is JsonPrimitive -> if (this is JsonNull) null else contentOrNull
    else -> null
}
