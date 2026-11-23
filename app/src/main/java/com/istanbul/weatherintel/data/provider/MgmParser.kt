package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ForecastRow
import com.istanbul.weatherintel.data.model.ForecastRun
import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ObservationRecord
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
 * Pure MGM response parser. Kept free of network concerns so it can be unit
 * tested against real captured payloads.
 *
 * Verified behaviour:
 *  - MGM marks absent values with -9999 -> those become null (never zero).
 *  - Units: degC, %, hPa, km/h, degrees, m, mm, okta.
 *  - Daily data arrives as parallel `*Gun<n>` arrays; unknown day keys are
 *    captured generically so no provider field is dropped.
 */
object MgmParser {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseObservation(
        body: String,
        stationName: String,
        stationExternalId: String?,
        collectedAt: Long,
    ): ObservationRecord? {
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
            ?: return null
        val obj = arr.firstOrNull()?.jsonObject ?: return null
        val observedAt = obj.str("veriZamani")?.let { parseIso(it) } ?: collectedAt

        val measurements = buildList {
            fun num(key: String, name: String, unit: String, category: String) {
                val raw = obj.numOrNull(key) ?: return
                val value = Sentinels.mgm(raw)
                add(
                    Measurement(
                        code = key,
                        name = name,
                        value = value,
                        text = null,
                        unit = unit,
                        originalValue = raw.toString(),
                        category = category,
                        availability = if (value == null) Availability.NULL_VALUE
                        else Availability.AVAILABLE,
                    )
                )
            }
            fun text(key: String, name: String, category: String, unit: String? = null) {
                val raw = obj.str(key) ?: return
                add(
                    Measurement(
                        code = key,
                        name = name,
                        value = null,
                        text = raw,
                        unit = unit,
                        originalValue = raw,
                        category = category,
                        availability = Availability.AVAILABLE,
                    )
                )
            }

            val C = "Current conditions"
            num("sicaklik", "Temperature", "degC", C)
            num("hissedilenSicaklik", "Feels like temperature", "degC", C)
            num("nem", "Relative humidity", "%", C)
            num("aktuelBasinc", "Pressure (station)", "hPa", C)
            num("denizeIndirgenmisBasinc", "Pressure (sea level)", "hPa", C)
            num("gorus", "Visibility", "m", C)
            num("ruzgarHiz", "Wind speed", "km/h", C)
            num("ruzgarYon", "Wind direction", "degrees", C)
            num("kapalilik", "Cloud cover", "okta", C)
            num("yagis00Now", "Precipitation (current)", "mm", C)
            num("yagis10Dk", "Precipitation (10 min)", "mm", C)
            num("yagis1Saat", "Precipitation (1 hour)", "mm", C)
            num("yagis6Saat", "Precipitation (6 hours)", "mm", C)
            num("yagis12Saat", "Precipitation (12 hours)", "mm", C)
            num("yagis24Saat", "Precipitation (24 hours)", "mm", C)
            num("denizSicaklik", "Sea temperature", "degC", C)
            num("karYukseklik", "Snow depth", "cm", C)
            text("hadiseKodu", "Weather code", C)
            obj.str("hadiseKodu")?.let { code ->
                add(
                    Measurement(
                        code = "hadiseAdi",
                        name = "Condition",
                        value = null,
                        text = MgmCodes.label(code),
                        unit = null,
                        originalValue = code,
                        category = C,
                        availability = Availability.AVAILABLE,
                    )
                )
            }
            text("veriZamani", "Observation time (UTC)", C, "ISO-8601")
            text("rasatMetar", "METAR observation", C)
            text("rasatTaf", "TAF forecast", C)
            text("rasatSinoptik", "Synoptic observation", C)
            text("istNo", "Station number", C)
        }

        return ObservationRecord(
            provider = ProviderId.MGM,
            locationName = stationName,
            stationExternalId = stationExternalId,
            stationName = stationName,
            observedAt = observedAt,
            collectedAt = collectedAt,
            measurements = measurements,
            originalJson = obj.toString(),
            sourceRecordId = obj.str("istNo"),
        )
    }

    fun parseHourly(
        body: String,
        locationName: String,
        sourceForecastId: String? = null,
    ): ForecastRun? {        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        val root = arr.firstOrNull()?.jsonObject ?: return null
        val tahmin = root["tahmin"] as? JsonArray ?: return null
        val issuedAt = root.str("baslangicZamani")?.let { parseIso(it) }
        val stationNo = root.str("istNo")
        val centreLabel = root.str("merkez")

        val rows = tahmin.mapNotNull { el ->
            val o = el.jsonObject
            val validAt = o.str("tarih")?.let { parseIso(it) } ?: return@mapNotNull null
            val measurements = buildList {
                o.str("hadise")?.let { code ->
                    add(
                        Measurement(
                            code = "hadise", name = "Weather code", value = null, text = code,
                            unit = null, originalValue = code, category = "Hourly forecast",
                            availability = Availability.AVAILABLE,
                        )
                    )
                    add(
                        Measurement(
                            code = "hadiseAdi", name = "Condition", value = null,
                            text = MgmCodes.label(code), unit = null, originalValue = code,
                            category = "Hourly forecast", availability = Availability.AVAILABLE,
                        )
                    )
                }
                fun num(key: String, name: String, unit: String) {
                    val raw = o.numOrNull(key) ?: return
                    val value = Sentinels.mgm(raw)
                    add(
                        Measurement(
                            code = key, name = name, value = value, text = null, unit = unit,
                            originalValue = raw.toString(), category = "Hourly forecast",
                            availability = if (value == null) Availability.NULL_VALUE
                            else Availability.AVAILABLE,
                        )
                    )
                }
                num("sicaklik", "Temperature", "degC")
                num("hissedilenSicaklik", "Feels like temperature", "degC")
                num("nem", "Relative humidity", "%")
                num("ruzgarYonu", "Wind direction", "degrees")
                num("ruzgarHizi", "Wind speed", "km/h")
                num("maksimumRuzgarHizi", "Wind gust", "km/h")
            }
            ForecastRow(validAt = validAt, period = "hourly", measurements = measurements)
        }
        if (rows.isEmpty()) return null
        val merged = mergeByValidTime(rows)
        return ForecastRun(
            provider = ProviderId.MGM,
            product = "hourly",
            issuedAt = issuedAt,
            collectedAt = System.currentTimeMillis(),
            // The provider's own forecast identifier: the station the forecast
            // was issued for and MGM's centre label for it.
            sourceForecastId = sourceForecastId
                ?: listOfNotNull(stationNo, centreLabel).joinToString("/").ifBlank { null }
                ?: issuedAt?.toString(),
            locationName = locationName,
            rows = merged,
        )
    }

    /**
     * Merges forecast rows that describe the same valid time.
     *
     * MGM's daily payload can list the same calendar date under two day
     * indices. Verified live on 22 November 2026: `tarihGun0` and `tarihGun1`
     * both carried `2026-11-22T00:00:00.000Z` with identical values. Without
     * this the Daily screen would show the same day twice and the period count
     * would be wrong.
     *
     * The merge keeps one row per valid time. For each parameter the first
     * row that actually carries a value wins; if a duplicated day carries two
     * *different* values for the same parameter, both are kept - the second
     * one is labelled with its day index so the discrepancy stays visible.
     * Nothing is silently discarded.
     */
    private fun mergeByValidTime(rows: List<ForecastRow>): List<ForecastRow> {
        if (rows.size < 2) return rows
        val byValidAt = rows.groupBy { it.validAt }
        if (byValidAt.size == rows.size) return rows

        return byValidAt.entries.sortedBy { it.key }.map { (validAt, sameDay) ->
            if (sameDay.size == 1) {
                sameDay.first()
            } else {
                mergeSameDay(validAt, sameDay)
            }
        }
    }

    /** Strips the day index from an MGM daily parameter code. */
    private val dayIndexInCode = Regex("Gun\\d+")

    private fun mergeSameDay(validAt: Long, sameDay: List<ForecastRow>): ForecastRow {
        val primary = sameDay.first()
        val byBase = LinkedHashMap<String, Measurement>()
        val conflicting = mutableListOf<Measurement>()

        for (row in sameDay) {
            for (m in row.measurements) {
                val base = m.code.replace(dayIndexInCode, "Gun")
                val existing = byBase[base]
                when {
                    existing == null -> byBase[base] = m
                    !hasValue(existing) && hasValue(m) -> byBase[base] = m
                    hasValue(existing) && hasValue(m) && existing.value != m.value ->
                        conflicting += m.copy(
                            name = "${m.name} (duplicate day index ${dayIndexOf(m.code)})",
                        )
                }
            }
        }
        return primary.copy(measurements = byBase.values.toList() + conflicting)
    }

    private fun dayIndexOf(code: String): String =
        dayIndexInCode.find(code)?.value?.removePrefix("Gun") ?: "?"

    private fun hasValue(m: Measurement): Boolean =
        m.value != null || !m.text.isNullOrBlank()

    fun parseDaily(body: String, locationName: String): ForecastRun? {
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        val root = arr.firstOrNull()?.jsonObject ?: return null

        val dayCount = (0..15).count { root.containsKey("tarihGun$it") }
        if (dayCount == 0) return null

        val rows = (0 until dayCount).mapNotNull { day ->
            val validAt = root.str("tarihGun$day")?.let { parseIso(it) } ?: return@mapNotNull null
            val measurements = buildList {
                root.str("hadiseGun$day")?.let { code ->
                    add(
                        Measurement(
                            code = "hadiseGun$day", name = "Weather code", value = null, text = code,
                            unit = null, originalValue = code, category = "Daily forecast",
                            availability = Availability.AVAILABLE,
                        )
                    )
                    add(
                        Measurement(
                            code = "hadiseGun${day}Adi", name = "Condition", value = null,
                            text = MgmCodes.label(code), unit = null, originalValue = code,
                            category = "Daily forecast", availability = Availability.AVAILABLE,
                        )
                    )
                }
                fun num(key: String, name: String, unit: String) {
                    val raw = root.numOrNull(key) ?: return
                    val value = Sentinels.mgm(raw)
                    add(
                        Measurement(
                            code = key, name = name, value = value, text = null, unit = unit,
                            originalValue = raw.toString(), category = "Daily forecast",
                            availability = if (value == null) Availability.NULL_VALUE
                            else Availability.AVAILABLE,
                        )
                    )
                }
                num("enDusukGun$day", "Minimum temperature", "degC")
                num("enYuksekGun$day", "Maximum temperature", "degC")
                num("enDusukNemGun$day", "Minimum humidity", "%")
                num("enYuksekNemGun$day", "Maximum humidity", "%")
                num("ruzgarHizGun$day", "Wind speed", "km/h")
                num("ruzgarYonGun$day", "Wind direction", "degrees")

                // Generic capture: every remaining *Gun<day> field.
                root.forEach { (key, value) ->
                    if (key.endsWith("Gun$day") && this.none { it.code == key }) {
                        val prim = value as? JsonPrimitive ?: return@forEach
                        if (prim is JsonNull) return@forEach
                        val numVal = prim.doubleOrNull
                        add(
                            Measurement(
                                code = key,
                                name = MgmCodes.dailyLabel(key),
                                value = numVal?.let { Sentinels.mgm(it) },
                                text = if (numVal == null) prim.contentOrNull else null,
                                unit = MgmCodes.dailyUnit(key),
                                originalValue = prim.contentOrNull,
                                category = "Daily forecast",
                                availability = Availability.AVAILABLE,
                            )
                        )
                    }
                }
            }
            ForecastRow(validAt = validAt, period = "daily", measurements = measurements)
        }
        if (rows.isEmpty()) return null
        return ForecastRun(
            provider = ProviderId.MGM,
            product = "daily",
            issuedAt = rows.first().validAt,
            collectedAt = System.currentTimeMillis(),
            sourceForecastId = rows.first().validAt.toString(),
            locationName = locationName,
            rows = rows,
        )
    }

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
