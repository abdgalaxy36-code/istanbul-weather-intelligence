package com.istanbul.weatherintel.data.export

import com.istanbul.weatherintel.data.db.WeatherDatabase
import com.istanbul.weatherintel.data.model.formatNumber
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds CSV and JSON exports of collected data.
 * Exports always carry provider, timestamps (UTC + Istanbul local), units,
 * original values and provenance. Credentials are never included.
 */
class ExportManager(
    private val db: WeatherDatabase,
) {
    data class Result(val fileName: String, val content: String)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun build(
        format: com.istanbul.weatherintel.ExportFormat,
        providerCode: String?,
        from: Long?,
        to: Long?,
    ): Result {
        val rows = collectRows(providerCode, from, to)
        return when (format) {
            com.istanbul.weatherintel.ExportFormat.CSV -> Result(
                fileName = fileName("csv"),
                content = toCsv(rows),
            )
            com.istanbul.weatherintel.ExportFormat.JSON -> Result(
                fileName = fileName("json"),
                content = toJson(rows),
            )
        }
    }

    private suspend fun collectRows(
        providerCode: String?,
        from: Long?,
        to: Long?,
    ): List<ExportRow> {
        val rows = mutableListOf<ExportRow>()
        val providers = db.providerDao().observeAll().first()
            .filter { providerCode == null || it.providerCode == providerCode }

        for (provider in providers) {
            // Observations
            db.observationDao().observeHistory(provider.providerCode, 2000).first()
                .filter { inRange(it.observedAt, from, to) }
                .forEach { obs ->
                    val measurements = runCatching {
                        json.decodeFromString<List<MeasurementExport>>(
                            kotlinx.serialization.builtins.ListSerializer(MeasurementExport.serializer()),
                            obs.normalizedJson,
                        )
                    }.getOrDefault(emptyList())
                    measurements.forEach { m ->
                        rows += ExportRow(
                            provider = provider.providerCode,
                            dataType = "observation",
                            locationOrStation = obs.sourceRecordId ?: "",
                            observedAt = obs.observedAt,
                            collectedAt = obs.collectedAt,
                            issuedAt = null,
                            validAt = obs.observedAt,
                            parameterCode = m.code,
                            parameterName = m.name,
                            value = m.value,
                            textValue = m.text,
                            unit = m.unit,
                            originalValue = m.originalValue,
                            category = m.category,
                            availability = m.availability,
                            rawProvenance = "observation:${obs.id}",
                        )
                    }
                }

            // Forecast values (all products, all versions retained)
            for (product in listOf("hourly", "three-hourly", "daily")) {
                val runs = db.forecastDao().runHistory(provider.providerCode, product, 200)
                for (run in runs) {
                    val values = db.forecastDao().values(run.id)
                    values.filter { inRange(it.validAt, from, to) }.forEach { v ->
                        rows += ExportRow(
                            provider = provider.providerCode,
                            dataType = "forecast",
                            locationOrStation = product,
                            observedAt = null,
                            collectedAt = run.collectedAt,
                            issuedAt = run.issuedAt,
                            validAt = v.validAt,
                            parameterCode = v.parameterCode,
                            parameterName = v.parameterName,
                            value = v.numericValue,
                            textValue = v.textValue,
                            unit = v.unit,
                            originalValue = v.originalValueJson,
                            category = v.category,
                            availability = v.availability,
                            rawProvenance = "forecast_run:${run.id}",
                        )
                    }
                }
            }

            // Warnings
            db.warningDao().observeAll().first()
                .filter { it.providerId == provider.id }
                .forEach { w ->
                    rows += ExportRow(
                        provider = provider.providerCode,
                        dataType = "warning",
                        locationOrStation = w.areasJson,
                        observedAt = null,
                        collectedAt = System.currentTimeMillis(),
                        issuedAt = w.issuedAt,
                        validAt = w.validFrom ?: 0L,
                        parameterCode = w.sourceWarningId ?: "warning",
                        parameterName = w.title ?: "Weather warning",
                        value = null,
                        textValue = listOfNotNull(w.severity, w.description).joinToString(" - "),
                        unit = null,
                        originalValue = w.rawData,
                        category = "Warnings",
                        availability = "available",
                        rawProvenance = "warning:${w.id}",
                    )
                }
        }
        return rows
    }

    private fun inRange(value: Long, from: Long?, to: Long?): Boolean {
        if (from != null && value < from) return false
        if (to != null && value > to) return false
        return true
    }

    private fun toCsv(rows: List<ExportRow>): String {
        val sb = StringBuilder()
        sb.append(
            listOf(
                "provider", "data_type", "location_or_station", "observed_at_utc",
                "collected_at_utc", "issued_at_utc", "valid_at_utc",
                "valid_at_istanbul", "parameter_code", "parameter_name", "numeric_value",
                "text_value", "unit", "original_value", "category", "availability", "provenance",
            ).joinToString(",")
        ).append('\n')
        rows.forEach { r ->
            sb.append(
                listOf(
                    r.provider,
                    r.dataType,
                    r.locationOrStation,
                    r.observedAt?.let { isoUtc(it) }.orEmpty(),
                    isoUtc(r.collectedAt),
                    r.issuedAt?.let { isoUtc(it) }.orEmpty(),
                    isoUtc(r.validAt),
                    istanbul(r.validAt),
                    r.parameterCode,
                    r.parameterName,
                    r.value?.let { formatNumber(it) }.orEmpty(),
                    r.textValue.orEmpty(),
                    r.unit.orEmpty(),
                    r.originalValue.orEmpty(),
                    r.category,
                    r.availability,
                    r.rawProvenance,
                ).joinToString(",") { csvEscape(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    private fun toJson(rows: List<ExportRow>): String {
        val recordElements: List<kotlinx.serialization.json.JsonElement> = rows.map { r ->
            buildJsonObject {
                put("provider", r.provider)
                put("data_type", r.dataType)
                put("location_or_station", r.locationOrStation)
                put("observed_at_utc", r.observedAt?.let { isoUtc(it) } ?: "")
                put("collected_at_utc", isoUtc(r.collectedAt))
                put("issued_at_utc", r.issuedAt?.let { isoUtc(it) } ?: "")
                put("valid_at_utc", isoUtc(r.validAt))
                put("valid_at_istanbul", istanbul(r.validAt))
                put("parameter_code", r.parameterCode)
                put("parameter_name", r.parameterName)
                put("numeric_value", r.value)
                put("text_value", r.textValue ?: "")
                put("unit", r.unit ?: "")
                put("original_value", r.originalValue ?: "")
                put("category", r.category)
                put("availability", r.availability)
                put("provenance", r.rawProvenance)
            }
        }
        val payload = buildJsonObject {
            put("exported_at_utc", isoUtc(System.currentTimeMillis()))
            put("timezone", "Europe/Istanbul")
            put("record_count", recordElements.size)
            put("records", kotlinx.serialization.json.JsonArray(recordElements))
        }
        return json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            payload,
        )
    }

    private fun String?.toJsonPrimitiveOrNull(): kotlinx.serialization.json.JsonElement? =
        this?.let { kotlinx.serialization.json.JsonPrimitive(it) }

    private fun Double?.toJsonPrimitiveOrNull(): kotlinx.serialization.json.JsonElement? =
        this?.let { kotlinx.serialization.json.JsonPrimitive(it) }


    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun istanbul(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Europe/Istanbul") }
            .format(Date(millis))

    private fun fileName(extension: String): String =
        "istanbul-weather-export-${System.currentTimeMillis()}.$extension"

    data class ExportRow(
        val provider: String,
        val dataType: String,
        val locationOrStation: String,
        val observedAt: Long?,
        val collectedAt: Long,
        val issuedAt: Long?,
        val validAt: Long,
        val parameterCode: String,
        val parameterName: String,
        val value: Double?,
        val textValue: String?,
        val unit: String?,
        val originalValue: String?,
        val category: String,
        val availability: String,
        val rawProvenance: String,
    )

    @kotlinx.serialization.Serializable
    data class MeasurementExport(
        val code: String,
        val name: String,
        val value: Double? = null,
        val text: String? = null,
        val unit: String? = null,
        val originalValue: String? = null,
        val category: String,
        val availability: String,
    )
}
