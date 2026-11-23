package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.db.StationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The MGM station catalogue.
 *
 * MGM's `/web/istasyonlar` endpoint returns every station in Turkey (1930 at
 * the time of writing). The spec asks for "available Istanbul stations" and for
 * station selection to be explicit, so this helper filters the national
 * catalogue down to the Istanbul province and makes the stations searchable.
 *
 * The `stations` table keeps the provider's own metadata verbatim in
 * `metadataJson` (the spec's column list has no separate province/district
 * columns), so the province and district are read back from that payload.
 */
object MgmStationCatalogue {

    private val json = Json { ignoreUnknownKeys = true }

    /** A station as shown in the picker. */
    data class Entry(
        val id: Long,
        val externalId: String,
        val name: String,
        val province: String?,
        val district: String?,
        val latitude: Double?,
        val longitude: Double?,
        val elevation: Double?,
        val isSelected: Boolean,
    ) {
        val subtitle: String
            get() = listOfNotNull(district, province)
                .distinct()
                .joinToString(", ")
                .ifBlank { "Istanbul" }
    }

    /** Reads the provider metadata of one stored station. */
    fun parse(station: StationEntity): Entry {
        val obj = runCatching {
            json.parseToJsonElement(station.metadataJson).jsonObject
        }.getOrNull()
        return Entry(
            id = station.id,
            externalId = station.externalStationId,
            name = station.stationName,
            province = obj?.string("il"),
            district = obj?.string("ilce"),
            latitude = station.latitude,
            longitude = station.longitude,
            elevation = station.elevation,
            isSelected = station.isSelected,
        )
    }

    /**
     * The stations inside the Istanbul province.
     *
     * The province name is compared diacritic-insensitively because MGM uses
     * the Turkish spelling "İstanbul". A station without a province in its
     * metadata is kept when its coordinates fall inside the Istanbul bounding
     * box, so nothing is silently dropped.
     */
    fun istanbul(stations: List<StationEntity>): List<Entry> =
        stations.asSequence()
            .map(::parse)
            .filter { entry ->
                val province = entry.province?.let(::normalise)
                when {
                    province != null -> province.contains("istanbul")
                    else -> inIstanbulBoundingBox(entry.latitude, entry.longitude)
                }
            }
            .sortedBy { it.name }
            .toList()

    /** Filters the Istanbul stations by a free-text query (name or district). */
    fun search(stations: List<StationEntity>, query: String): List<Entry> {
        val all = istanbul(stations)
        val q = normalise(query.trim())
        if (q.isEmpty()) return all
        return all.filter { entry ->
            normalise(entry.name).contains(q) ||
                normalise(entry.district.orEmpty()).contains(q) ||
                normalise(entry.subtitle).contains(q) ||
                entry.externalId.contains(q)
        }
    }

    /** Lowercases and strips the Turkish diacritics so searches behave predictably. */
    fun normalise(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            when (ch) {
                'ı' -> sb.append('i')
                'ğ' -> sb.append('g')
                'ü' -> sb.append('u')
                'ş' -> sb.append('s')
                'ö' -> sb.append('o')
                'ç' -> sb.append('c')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("i̇", "i")
    }

    /** Approximate bounding box of Istanbul province. */
    private fun inIstanbulBoundingBox(latitude: Double?, longitude: Double?): Boolean =
        latitude != null && longitude != null &&
            latitude in 40.75..41.95 && longitude in 27.85..29.95

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
}
