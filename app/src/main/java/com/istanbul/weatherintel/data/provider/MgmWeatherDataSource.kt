package com.istanbul.weatherintel.data.provider

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
import com.istanbul.weatherintel.data.model.WarningRecord
import com.istanbul.weatherintel.data.net.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * MGM (Meteoroloji Genel Müdürlüğü) adapter.
 *
 * Verified live against servis.mgm.gov.tr (November 2026):
 *   GET /web/merkezler?il={province}      -> centre + station ids
 *   GET /web/sondurumlar?merkezid={id}    -> current observation
 *   GET /web/tahminler/saatlik?istno={id} -> hourly forecast (3-hour steps, 6 rows)
 *   GET /web/tahminler/gunluk?istno={id}  -> daily forecast (parallel day arrays)
 *   GET /web/istasyonlar                  -> full station catalogue (1930 stations)
 *
 * The service rejects requests without Referer/Origin headers, which HttpClient adds.
 *
 * Units (verified against MGM's own site templates and cross-checked with the
 * embedded METAR): temperature degC, humidity %, pressure hPa, wind km/h,
 * visibility m, precipitation mm, cloud cover oktas, direction degrees.
 * MGM marks absent readings with -9999; those become null - never zero.
 *
 * Station selection (verified live, November 2026):
 *  - Istanbul has exactly ONE MGM centre (merkezId 93401, Bakirkoy) and
 *    therefore exactly ONE observation and daily-forecast source. MGM's own
 *    note says that centre takes its observations from Ataturk Airport.
 *  - Three Istanbul stations publish an hourly forecast: 17060 (ISTANBUL,
 *    Bakirkoy), 17061 (ISTANBUL_KUZEY, Sariyer) and 17064 (ISTANBUL_ANADOLU,
 *    Kartal). The other 42 Istanbul stations have no forecast product.
 *  The user-selected forecast station is therefore used for the hourly
 *  product; when it publishes nothing the provincial centre is used instead
 *  and the run records which station actually produced it.
 */
class MgmWeatherDataSource(
    private val http: HttpClient,
    private val province: String = "istanbul",
    private val forecastStationId: Int? = null,
) : WeatherDataSource {

    override val providerId: ProviderId = ProviderId.MGM

    /** No verified MGM three-hourly product: /web/tahminler/saatlik returns 3-hour steps
     *  within its own hourly product, and no separate 3-hourly endpoint exists (404). */
    override val unsupportedProducts: List<String> = listOf("three-hourly", "warnings")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val rawPayloads = mutableListOf<RawPayload>()

    private data class Center(
        val merkezId: Int,
        val sondurumIstNo: Int,
        val saatlikTahminIstNo: Int,
        val gunlukTahminIstNo: Int,
        val il: String?,
        val ilce: String?,
        val enlem: Double?,
        val boylam: Double?,
        val yukseklik: Double?,
    )

    private suspend fun fetch(endpoint: String): JsonElement {
        val res = http.getMgm(endpoint)
        rawPayloads += RawPayload(
            provider = ProviderId.MGM,
            endpoint = endpoint,
            retrievedAt = System.currentTimeMillis(),
            httpStatus = res.code,
            contentType = res.contentType,
            body = res.body.orEmpty(),
            requestMetadata = """{"headers":"Referer+Origin required","method":"GET"}""",
        )
        if (!res.isSuccess) throw ProviderHttpException(res.code, "MGM $endpoint -> HTTP ${res.code}")
        val body = res.body
        if (body.isNullOrBlank()) throw ProviderParseException("MGM $endpoint returned an empty body")
        return try {
            json.parseToJsonElement(body)
        } catch (t: Throwable) {
            throw ProviderParseException("MGM $endpoint returned invalid JSON: ${t.message?.take(120)}")
        }
    }

    private suspend fun centers(): List<Center> {
        val arr = fetch("/web/merkezler?il=$province").jsonArray
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val merkez = o.int("merkezId") ?: return@mapNotNull null
            Center(
                merkezId = merkez,
                sondurumIstNo = o.int("sondurumIstNo") ?: merkez,
                saatlikTahminIstNo = o.int("saatlikTahminIstNo") ?: merkez,
                gunlukTahminIstNo = o.int("gunlukTahminIstNo") ?: merkez,
                il = o.str("il"),
                ilce = o.str("ilce"),
                enlem = o.num("enlem"),
                boylam = o.num("boylam"),
                yukseklik = o.num("yukseklik"),
            )
        }
    }

    override suspend fun fetchLocationMetadata(): LocationRecord? {
        val center = centers().firstOrNull() ?: return null
        return LocationRecord(
            provider = ProviderId.MGM,
            sourceLocationId = center.merkezId.toString(),
            name = listOfNotNull(center.ilce, center.il).joinToString(", ").ifBlank { "Istanbul" },
            region = center.il,
            district = center.ilce,
            latitude = center.enlem,
            longitude = center.boylam,
        )
    }

    override suspend fun fetchStations(): List<StationRecord> {
        val arr = fetch("/web/istasyonlar").jsonArray
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val id = o.int("istNo") ?: return@mapNotNull null
            StationRecord(
                provider = ProviderId.MGM,
                externalId = id.toString(),
                name = o.str("istAd") ?: "Station $id",
                latitude = o.num("enlem"),
                longitude = o.num("boylam"),
                elevation = o.num("yukseklik"),
                metadataJson = o.toString(),
                region = o.str("il"),
                district = o.str("ilce"),
            )
        }
    }

    override suspend fun fetchCurrentObservations(): List<ObservationRecord> {
        val center = centers().firstOrNull()
            ?: throw ProviderParseException("No MGM centre found for province '$province'")
        val body = fetchRaw("/web/sondurumlar?merkezid=${center.merkezId}")
        val stationName = listOfNotNull(center.ilce, center.il).joinToString(", ")
            .ifBlank { "Istanbul" }
        val observation = MgmParser.parseObservation(
            body = body,
            stationName = stationName,
            stationExternalId = center.sondurumIstNo.toString(),
            collectedAt = System.currentTimeMillis(),
        ) ?: return emptyList()
        return listOf(observation)
    }

    override suspend fun fetchHourlyForecast(): List<ForecastRun> {
        val center = centers().firstOrNull() ?: return emptyList()

        // The user-selected station is tried first; the provincial centre is
        // the fallback when that station publishes no hourly product.
        val selected = forecastStationId?.takeIf { it != center.saatlikTahminIstNo }
        if (selected != null) {
            val body = fetchRaw("/web/tahminler/saatlik?istno=$selected")
            val run = MgmParser.parseHourly(
                body = body,
                locationName = "Istanbul forecast station $selected",
                sourceForecastId = "station-$selected",
            )
            if (run != null) return listOf(run)
        }

        val body = fetchRaw("/web/tahminler/saatlik?istno=${center.saatlikTahminIstNo}")
        val locationName = "${center.ilce ?: center.il ?: "Istanbul"} (${center.saatlikTahminIstNo})"
        val run = MgmParser.parseHourly(
            body = body,
            locationName = locationName,
            sourceForecastId = "station-${center.saatlikTahminIstNo}",
        ) ?: return emptyList()
        return listOf(run)
    }

    override suspend fun fetchDailyForecast(): List<ForecastRun> {
        val center = centers().firstOrNull() ?: return emptyList()
        val body = fetchRaw("/web/tahminler/gunluk?istno=${center.gunlukTahminIstNo}")
        val locationName = "${center.ilce ?: center.il ?: "Istanbul"} (${center.gunlukTahminIstNo})"
        val run = MgmParser.parseDaily(body, locationName) ?: return emptyList()
        return listOf(run)
    }

    /** Fetches a raw endpoint, retaining the payload for provenance. */
    private suspend fun fetchRaw(endpoint: String): String {
        val res = http.getMgm(endpoint)
        rawPayloads += RawPayload(
            provider = ProviderId.MGM,
            endpoint = endpoint,
            retrievedAt = System.currentTimeMillis(),
            httpStatus = res.code,
            contentType = res.contentType,
            body = res.body.orEmpty(),
            requestMetadata = """{"headers":"Referer+Origin required","method":"GET"}""",
        )
        if (!res.isSuccess) throw ProviderHttpException(res.code, "MGM $endpoint -> HTTP ${res.code}")
        val body = res.body
        if (body.isNullOrBlank()) throw ProviderParseException("MGM $endpoint returned an empty body")
        return body
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
        val observations = fetchCurrentObservations()
        val hourly = fetchHourlyForecast()
        val daily = fetchDailyForecast()
        return ProviderCollection(
            provider = ProviderId.MGM,
            location = location,
            observations = observations,
            forecastRuns = hourly + daily,
            warnings = emptyList(),
            rawPayloads = drainRawPayloads(),
            unsupportedProducts = unsupportedProducts,
        )
    }

    companion object {
        fun parseIso(s: String): Long? = try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (_: Throwable) {
            try {
                java.time.LocalDateTime.parse(s.replace(Regex("(\\.\\d{3})\\d+"), "$1"))
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/** Official MGM condition codes, extracted from MGM's own convertHadise() mapping. */
object MgmCodes {
    private val table = mapOf(
        "A" to "Açık",
        "AB" to "Az Bulutlu",
        "PB" to "Parçalı Bulutlu",
        "CB" to "Çok Bulutlu",
        "HY" to "Hafif Yağmurlu",
        "Y" to "Yağmurlu",
        "KY" to "Kuvvetli Yağmurlu",
        "KKY" to "Karla Karışık Yağmurlu",
        "HKY" to "Hafif Kar Yağışlı",
        "K" to "Kar Yağışlı",
        "YKY" to "Yoğun Kar Yağışlı",
        "HSY" to "Hafif Sağanak Yağışlı",
        "SY" to "Sağanak Yağışlı",
        "KSY" to "Kuvvetli Sağanak Yağışlı",
        "MSY" to "Mevzi Sağanak Yağışlı",
        "DY" to "Dolu",
        "GSY" to "Gökgürültülü Sağanak Yağışlı",
        "KGY" to "Kuvvetli Gökgürültülü Sağanak Yağışlı",
        "SIS" to "Sisli",
        "PUS" to "Puslu",
        "DMN" to "Dumanlı",
        "KF" to "Kum veya Toz Taşınımı",
        "R" to "Rüzgarlı",
        "GKR" to "Güneyli Kuvvetli Rüzgar",
        "KKR" to "Kuzeyli Kuvvetli Rüzgar",
        "KKK" to "Kuzeyden Kuvvetli Kar Yağışlı",
        "GKK" to "Güneyden Kuvvetli Kar Yağışlı",
        "T" to "Tipi",
        "KK" to "Kar Fırtınası",
        "YKK" to "Yoğun Kar Yağışlı",
    )

    fun label(code: String): String = table[code] ?: code

    fun dailyLabel(key: String): String = when {
        key.startsWith("enDusukNem") -> "Minimum humidity"
        key.startsWith("enYuksekNem") -> "Maximum humidity"
        key.startsWith("enDusuk") -> "Minimum temperature"
        key.startsWith("enYuksek") -> "Maximum temperature"
        key.startsWith("ruzgarHiz") -> "Wind speed"
        key.startsWith("ruzgarYon") -> "Wind direction"
        key.startsWith("hadise") -> "Weather code"
        key.startsWith("tarih") -> "Forecast date"
        else -> key.replaceFirstChar { it.uppercaseChar() }
    }

    fun dailyUnit(key: String): String? = when {
        key.startsWith("enDusukNem") || key.startsWith("enYuksekNem") -> "%"
        key.startsWith("enDusuk") || key.startsWith("enYuksek") -> "degC"
        key.startsWith("ruzgarHiz") -> "km/h"
        key.startsWith("ruzgarYon") -> "degrees"
        else -> null
    }

    /**
     * Semantic category for an MGM parameter key, used to group the Compare
     * screen. Daily keys carry a day index suffix, which is ignored.
     */
    fun category(key: String): String {
        val base = key.replace(Regex("(\\d+)$"), "")
        return when {
            base == "sicaklik" || base == "hissedilenSicaklik" ||
                base == "denizSicaklik" || base == "enDusukGun" ||
                base == "enYuksekGun" -> "Temperature"
            base == "nem" || base == "enDusukNemGun" || base == "enYuksekNemGun" -> "Humidity"
            base == "aktuelBasinc" || base == "denizeIndirgenmisBasinc" -> "Pressure"
            base == "gorus" -> "Visibility"
            base.startsWith("ruzgar") -> "Wind"
            base == "kapalilik" -> "Cloud cover"
            base.startsWith("yagis") -> "Precipitation"
            base == "karYukseklik" -> "Precipitation"
            base.startsWith("hadise") -> "Weather condition"
            base.startsWith("rasat") -> "Station reports"
            base == "istNo" -> "Station"
            base.startsWith("veriZamani") || base.startsWith("denizVeriZamani") -> "Timestamps"
            base.startsWith("tarih") -> "Timestamps"
            else -> "Other"
        }
    }
}

/** Sentinel handling shared by both providers. */
object Sentinels {
    const val NO_DATA = -9999.0

    fun mgm(value: Double?): Double? = when {
        value == null -> null
        value.isNaN() -> null
        value <= -9998.0 -> null
        else -> value
    }
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

internal fun JsonObject.numOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

internal fun JsonObject.num(key: String): Double? = numOrNull(key)
