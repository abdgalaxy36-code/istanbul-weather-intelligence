package com.istanbul.weatherintel.data.repository

import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.provider.MetOfficeParams
import com.istanbul.weatherintel.data.provider.MgmCodes

/**
 * Cross-provider parameter crosswalk.
 *
 * MGM and the Met Office describe the same physical quantities with completely
 * different keys:
 *
 *   | quantity          | MGM              | Met Office                   |
 *   |-------------------|------------------|------------------------------|
 *   | temperature       | `sicaklik`       | `screenTemperature`          |
 *   | feels like        | `hissedilenSicaklik` | `feelsLikeTemperature` / `feelsLikeTemp` |
 *   | humidity          | `nem`            | `screenRelativeHumidity`     |
 *   | sea-level pressure| `denizeIndirgenmisBasinc` | `mslp`               |
 *   | visibility        | `gorus`          | `visibility`                 |
 *   | wind speed        | `ruzgarHiz` / `ruzgarHizi` | `windSpeed10m`       |
 *   | wind gust         | `maksimumRuzgarHizi` | `windGustSpeed10m`       |
 *   | wind direction    | `ruzgarYon` / `ruzgarYonu` | `windDirectionFrom10m` |
 *   | condition         | `hadiseKodu` / `hadise` | `significantWeatherCode` |
 *
 * Without this table the two providers can never be lined up and no difference
 * can be calculated. With it, every parameter that both providers publish is
 * paired, and every parameter that only one provider publishes is still listed -
 * it simply stays in its own column, marked as provider-specific.
 *
 * Nothing is dropped: a parameter with no counterpart is never discarded and its
 * missing counterpart is never shown as zero.
 */

/** Which pair of products is being compared. */
enum class ComparisonScope(val id: String, val label: String) {
    OBSERVATION("observation", "Current conditions"),
    HOURLY("hourly", "Hourly forecast"),
    THREE_HOURLY("three-hourly", "Three-hourly forecast"),
    DAILY("daily", "Daily forecast"),
}

/**
 * A quantity that both providers can express. The [unit] is the *canonical*
 * unit the difference is calculated in - the individual provider values keep
 * their own units and are converted only for the difference.
 */
enum class CanonicalParameter(
    val code: String,
    val label: String,
    val category: String,
    val unit: String?,
    val comparable: Boolean = true,
    val note: String? = null,
) {
    TEMPERATURE("temperature", "Temperature", "Temperature", "degC"),
    FEELS_LIKE(
        "feels_like_temperature", "Feels like temperature", "Temperature", "degC",
    ),
    MAX_TEMPERATURE("max_temperature", "Maximum temperature", "Temperature", "degC"),
    MIN_TEMPERATURE("min_temperature", "Minimum temperature", "Temperature", "degC"),
    RELATIVE_HUMIDITY("relative_humidity", "Relative humidity", "Humidity", "%"),
    MAX_RELATIVE_HUMIDITY(
        "max_relative_humidity", "Maximum relative humidity", "Humidity", "%",
    ),
    MIN_RELATIVE_HUMIDITY(
        "min_relative_humidity", "Minimum relative humidity", "Humidity", "%",
    ),
    SEA_LEVEL_PRESSURE(
        "sea_level_pressure", "Mean sea-level pressure", "Pressure", "hPa",
    ),
    VISIBILITY("visibility", "Visibility", "Visibility", "m"),
    WIND_SPEED("wind_speed", "Wind speed", "Wind", "km/h"),
    WIND_GUST("wind_gust", "Wind gust", "Wind", "km/h"),
    WIND_DIRECTION("wind_direction", "Wind direction", "Wind", "degrees"),
    CONDITION(
        "weather_condition", "Weather condition", "Weather condition", null,
        comparable = false,
        note = "MGM and the Met Office use different weather-code vocabularies. " +
            "Both descriptions are shown, but no numeric difference is calculated.",
    ),
}

/**
 * The crosswalk itself.
 *
 * Only parameters that genuinely have a counterpart in the other provider are
 * listed. Anything else is automatically treated as provider-specific and is
 * still displayed - it is never dropped and never silently paired.
 */
object ParameterCrosswalk {

    // ---------------------------------------------------------------- MGM ---

    private val mgmObservation = mapOf(
        "sicaklik" to CanonicalParameter.TEMPERATURE,
        "hissedilenSicaklik" to CanonicalParameter.FEELS_LIKE,
        "nem" to CanonicalParameter.RELATIVE_HUMIDITY,
        "denizeIndirgenmisBasinc" to CanonicalParameter.SEA_LEVEL_PRESSURE,
        "gorus" to CanonicalParameter.VISIBILITY,
        "ruzgarHiz" to CanonicalParameter.WIND_SPEED,
        "ruzgarYon" to CanonicalParameter.WIND_DIRECTION,
        "hadiseKodu" to CanonicalParameter.CONDITION,
    )

    /**
     * MGM's `tahminler/saatlik` product. It is named "hourly" by MGM but the
     * returned series has 3-hour steps, so it is compared against the Met
     * Office three-hourly product.
     */
    private val mgmHourly = mapOf(
        "sicaklik" to CanonicalParameter.TEMPERATURE,
        "hissedilenSicaklik" to CanonicalParameter.FEELS_LIKE,
        "nem" to CanonicalParameter.RELATIVE_HUMIDITY,
        "ruzgarHizi" to CanonicalParameter.WIND_SPEED,
        "ruzgarYonu" to CanonicalParameter.WIND_DIRECTION,
        "maksimumRuzgarHizi" to CanonicalParameter.WIND_GUST,
        "hadise" to CanonicalParameter.CONDITION,
    )

    /** MGM daily keys carry a day index suffix (`enDusukGun0` .. `enDusukGun5`). */
    private val mgmDaily = mapOf(
        "enDusukGun" to CanonicalParameter.MIN_TEMPERATURE,
        "enYuksekGun" to CanonicalParameter.MAX_TEMPERATURE,
        "enDusukNemGun" to CanonicalParameter.MIN_RELATIVE_HUMIDITY,
        "enYuksekNemGun" to CanonicalParameter.MAX_RELATIVE_HUMIDITY,
        "ruzgarHizGun" to CanonicalParameter.WIND_SPEED,
        "ruzgarYonGun" to CanonicalParameter.WIND_DIRECTION,
        "hadiseGun" to CanonicalParameter.CONDITION,
    )

    // --------------------------------------------------------- Met Office ---

    private val metOfficeObservation = mapOf(
        "screenTemperature" to CanonicalParameter.TEMPERATURE,
        "feelsLikeTemperature" to CanonicalParameter.FEELS_LIKE,
        "screenRelativeHumidity" to CanonicalParameter.RELATIVE_HUMIDITY,
        "mslp" to CanonicalParameter.SEA_LEVEL_PRESSURE,
        "visibility" to CanonicalParameter.VISIBILITY,
        "windSpeed10m" to CanonicalParameter.WIND_SPEED,
        "windDirectionFrom10m" to CanonicalParameter.WIND_DIRECTION,
        "significantWeatherCode" to CanonicalParameter.CONDITION,
    )

    private val metOfficeHourly = mapOf(
        "screenTemperature" to CanonicalParameter.TEMPERATURE,
        "feelsLikeTemperature" to CanonicalParameter.FEELS_LIKE,
        "screenRelativeHumidity" to CanonicalParameter.RELATIVE_HUMIDITY,
        "mslp" to CanonicalParameter.SEA_LEVEL_PRESSURE,
        "visibility" to CanonicalParameter.VISIBILITY,
        "windSpeed10m" to CanonicalParameter.WIND_SPEED,
        "windGustSpeed10m" to CanonicalParameter.WIND_GUST,
        "windDirectionFrom10m" to CanonicalParameter.WIND_DIRECTION,
        "significantWeatherCode" to CanonicalParameter.CONDITION,
    )

    /** The three-hourly product uses `feelsLikeTemp`, not `feelsLikeTemperature`. */
    private val metOfficeThreeHourly = mapOf(
        "screenTemperature" to CanonicalParameter.TEMPERATURE,
        "feelsLikeTemp" to CanonicalParameter.FEELS_LIKE,
        "screenRelativeHumidity" to CanonicalParameter.RELATIVE_HUMIDITY,
        "mslp" to CanonicalParameter.SEA_LEVEL_PRESSURE,
        "visibility" to CanonicalParameter.VISIBILITY,
        "windSpeed10m" to CanonicalParameter.WIND_SPEED,
        "windGustSpeed10m" to CanonicalParameter.WIND_GUST,
        "windDirectionFrom10m" to CanonicalParameter.WIND_DIRECTION,
        "significantWeatherCode" to CanonicalParameter.CONDITION,
    )

    private val metOfficeDaily = mapOf(
        "dayMaxScreenTemperature" to CanonicalParameter.MAX_TEMPERATURE,
        "nightMinScreenTemperature" to CanonicalParameter.MIN_TEMPERATURE,
        "middayRelativeHumidity" to CanonicalParameter.MAX_RELATIVE_HUMIDITY,
        "midnightRelativeHumidity" to CanonicalParameter.MIN_RELATIVE_HUMIDITY,
        "middayMslp" to CanonicalParameter.SEA_LEVEL_PRESSURE,
        "middayVisibility" to CanonicalParameter.VISIBILITY,
        "midday10MWindSpeed" to CanonicalParameter.WIND_SPEED,
        "midday10MWindGust" to CanonicalParameter.WIND_GUST,
        "midday10MWindDirection" to CanonicalParameter.WIND_DIRECTION,
        "daySignificantWeatherCode" to CanonicalParameter.CONDITION,
    )

    // ------------------------------------------------------------ Lookup ---

    private val dayIndex = Regex("(\\d+)$")

    /** The canonical parameter for a raw provider key, or null if it has none. */
    fun canonical(
        provider: ProviderId,
        scope: ComparisonScope,
        rawCode: String,
    ): CanonicalParameter? {
        val table = when (provider) {
            ProviderId.MGM -> when (scope) {
                ComparisonScope.OBSERVATION -> mgmObservation
                ComparisonScope.HOURLY, ComparisonScope.THREE_HOURLY -> mgmHourly
                ComparisonScope.DAILY -> mgmDaily
            }
            ProviderId.MET_OFFICE -> when (scope) {
                ComparisonScope.OBSERVATION -> metOfficeObservation
                ComparisonScope.HOURLY -> metOfficeHourly
                ComparisonScope.THREE_HOURLY -> metOfficeThreeHourly
                ComparisonScope.DAILY -> metOfficeDaily
            }
        }
        table[rawCode]?.let { return it }
        // MGM daily keys are suffixed with the day index.
        if (provider == ProviderId.MGM && scope == ComparisonScope.DAILY) {
            return table[rawCode.replace(dayIndex, "")]
        }
        return null
    }

    /** All canonical parameters, by code. */
    fun byCode(code: String): CanonicalParameter? =
        CanonicalParameter.entries.firstOrNull { it.code == code }

    /**
     * The group key a measurement belongs to. Paired parameters share a key;
     * provider-specific parameters get their own key so they stay visible in
     * their own column.
     */
    fun groupKey(
        provider: ProviderId,
        scope: ComparisonScope,
        rawCode: String,
    ): String =
        canonical(provider, scope, rawCode)?.code
            ?: "${provider.groupPrefix()}:$rawCode"

    /** The semantic category a parameter belongs to. */
    fun category(
        provider: ProviderId,
        scope: ComparisonScope,
        rawCode: String,
        fallback: String,
    ): String =
        canonical(provider, scope, rawCode)?.category
            ?: when (provider) {
                ProviderId.MET_OFFICE -> MetOfficeParams.category(rawCode)
                ProviderId.MGM -> MgmCodes.category(rawCode)
            }.takeIf { it.isNotBlank() && it != "Other" }
            ?: fallback

    private fun ProviderId.groupPrefix(): String = when (this) {
        ProviderId.MGM -> "mgm"
        ProviderId.MET_OFFICE -> "mo"
    }
}
