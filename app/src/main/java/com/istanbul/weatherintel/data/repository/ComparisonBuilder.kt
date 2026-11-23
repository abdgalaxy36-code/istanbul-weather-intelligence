package com.istanbul.weatherintel.data.repository

import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ComparisonRow
import com.istanbul.weatherintel.data.model.ComparisonValue
import com.istanbul.weatherintel.data.model.DataType
import com.istanbul.weatherintel.data.model.Measurement
import com.istanbul.weatherintel.data.model.ProviderId

/**
 * Builds the Compare page data.
 *
 * Rules enforced here (requirements sections 7 and 9):
 *  - Parameters that both providers publish are lined up through
 *    [ParameterCrosswalk], so a real difference can be calculated.
 *  - Observations are only compared with observations; forecasts only with
 *    forecasts, and forecast values must share a valid time.
 *  - Units are converted explicitly to the canonical unit for the difference;
 *    the stored source value is never modified.
 *  - A missing value is never treated as zero.
 *  - Provider-specific fields are always included, even with no counterpart.
 */
object ComparisonBuilder {

    /** A measurement together with the time it is valid for. */
    data class TimedMeasurement(val validAt: Long, val measurement: Measurement)

    /** One forecast valid time with the rows comparing both providers. */
    data class AlignedPeriod(
        val validAt: Long,
        val rows: List<ComparisonRow>,
    )

    /** Groups measurements into comparable rows for two providers. */
    fun build(
        mgm: List<Measurement>,
        metOffice: List<Measurement>,
        scope: ComparisonScope = ComparisonScope.OBSERVATION,
        mgmSource: String = "MGM",
        metOfficeSource: String = "Met Office",
        mgmValidAt: Long? = null,
        metOfficeValidAt: Long? = null,
        mgmCollectedAt: Long = 0,
        metOfficeCollectedAt: Long = 0,
        mgmIssuedAt: Long? = null,
        metOfficeIssuedAt: Long? = null,
        mgmLocation: String? = null,
        metOfficeLocation: String? = null,
        dataType: DataType = DataType.OBSERVATION,
    ): List<ComparisonRow> = buildRows(
        mgm = mgm,
        metOffice = metOffice,
        scope = scope,
        mgmSource = mgmSource,
        metOfficeSource = metOfficeSource,
        mgmValidAt = mgmValidAt,
        metOfficeValidAt = metOfficeValidAt,
        mgmCollectedAt = mgmCollectedAt,
        metOfficeCollectedAt = metOfficeCollectedAt,
        mgmIssuedAt = mgmIssuedAt,
        metOfficeIssuedAt = metOfficeIssuedAt,
        mgmLocation = mgmLocation,
        metOfficeLocation = metOfficeLocation,
        dataType = dataType,
        // Forecast values must share a valid time. Observations are compared on
        // the quantity itself - the two providers simply report at different
        // times, which is shown in the provenance column.
        requireSameValidTime = dataType == DataType.FORECAST,
    )

    /**
     * Builds the comparison for two forecast series. Only valid times that both
     * providers actually published are aligned; a valid time present in only one
     * provider is still reported, with the other side left empty rather than
     * filled with zeros.
     */
    fun buildAligned(
        mgm: List<TimedMeasurement>,
        metOffice: List<TimedMeasurement>,
        scope: ComparisonScope,
        mgmSource: String = "MGM",
        metOfficeSource: String = "Met Office",
        mgmCollectedAt: Long = 0,
        metOfficeCollectedAt: Long = 0,
        mgmIssuedAt: Long? = null,
        metOfficeIssuedAt: Long? = null,
        mgmLocation: String? = null,
        metOfficeLocation: String? = null,
        dataType: DataType = DataType.FORECAST,
    ): List<AlignedPeriod> {
        val mgmByTime = mgm.groupBy { it.validAt }
        val moByTime = metOffice.groupBy { it.validAt }
        val times = (mgmByTime.keys + moByTime.keys).sorted()

        return times.map { validAt ->
            AlignedPeriod(
                validAt = validAt,
                rows = buildRows(
                    mgm = mgmByTime[validAt].orEmpty().map { it.measurement },
                    metOffice = moByTime[validAt].orEmpty().map { it.measurement },
                    scope = scope,
                    mgmSource = mgmSource,
                    metOfficeSource = metOfficeSource,
                    mgmValidAt = validAt,
                    metOfficeValidAt = validAt,
                    mgmCollectedAt = mgmCollectedAt,
                    metOfficeCollectedAt = metOfficeCollectedAt,
                    mgmIssuedAt = mgmIssuedAt,
                    metOfficeIssuedAt = metOfficeIssuedAt,
                    mgmLocation = mgmLocation,
                    metOfficeLocation = metOfficeLocation,
                    dataType = dataType,
                    requireSameValidTime = true,
                ),
            )
        }
    }

    private fun buildRows(
        mgm: List<Measurement>,
        metOffice: List<Measurement>,
        scope: ComparisonScope,
        mgmSource: String,
        metOfficeSource: String,
        mgmValidAt: Long?,
        metOfficeValidAt: Long?,
        mgmCollectedAt: Long,
        metOfficeCollectedAt: Long,
        mgmIssuedAt: Long?,
        metOfficeIssuedAt: Long?,
        mgmLocation: String?,
        metOfficeLocation: String?,
        dataType: DataType,
        requireSameValidTime: Boolean,
    ): List<ComparisonRow> {
        val mgmByKey = LinkedHashMap<String, Measurement>()
        for (m in mgm) {
            mgmByKey[ParameterCrosswalk.groupKey(ProviderId.MGM, scope, m.code)] = m
        }
        val moByKey = LinkedHashMap<String, Measurement>()
        for (m in metOffice) {
            moByKey[ParameterCrosswalk.groupKey(ProviderId.MET_OFFICE, scope, m.code)] = m
        }

        val keys = (mgmByKey.keys + moByKey.keys).sorted()
        val rows = mutableListOf<ComparisonRow>()

        for (key in keys) {
            val a = mgmByKey[key]
            val b = moByKey[key]
            val canonical = ParameterCrosswalk.byCode(key)

            val name = canonical?.label ?: a?.name ?: b?.name ?: key
            val unit = canonical?.unit ?: a?.unit ?: b?.unit
            val category = canonical?.category
                ?: a?.let {
                    ParameterCrosswalk.category(
                        ProviderId.MGM, scope, it.code, it.category,
                    )
                }
                ?: b?.let {
                    ParameterCrosswalk.category(
                        ProviderId.MET_OFFICE, scope, it.code, it.category,
                    )
                }
                ?: "Other"

            val mgmValue = a?.toComparisonValue(
                source = mgmSource,
                validAt = mgmValidAt,
                issuedAt = mgmIssuedAt,
                collectedAt = mgmCollectedAt,
                location = mgmLocation,
                dataType = dataType,
            )
            val moValue = b?.toComparisonValue(
                source = metOfficeSource,
                validAt = metOfficeValidAt,
                issuedAt = metOfficeIssuedAt,
                collectedAt = metOfficeCollectedAt,
                location = metOfficeLocation,
                dataType = dataType,
            )

            val comparable = isComparable(
                a = a,
                b = b,
                canonical = canonical,
                requireSameValidTime = requireSameValidTime,
                mgmValidAt = mgmValidAt,
                metOfficeValidAt = metOfficeValidAt,
            )

            val note = when {
                a == null && b != null -> "Only the Met Office provides this field"
                b == null && a != null -> "Only MGM provides this field"
                a != null && b != null && a.value == null && b.value == null &&
                    a.text.isNullOrBlank() && b.text.isNullOrBlank() ->
                    "Neither provider supplied a value"
                canonical?.note != null && a != null && b != null -> canonical.note
                !comparable && a != null && b != null -> when {
                    dataType == DataType.FORECAST && requireSameValidTime &&
                        mgmValidAt != metOfficeValidAt ->
                        "Different forecast valid times - not directly comparable"
                    canonical != null && canonical.unit != null &&
                        !canConvert(a, b, canonical.unit) ->
                        "Units cannot be reconciled (${a.unit ?: "?"} vs ${b.unit ?: "?"}) - " +
                            "no difference calculated"
                    a.unit != null && b.unit != null && a.unit != b.unit ->
                        "Different units (${a.unit} vs ${b.unit}) - no difference calculated"
                    else -> "Values are not directly comparable"
                }
                else -> null
            }

            rows += ComparisonRow(
                category = category,
                code = key,
                name = name,
                unit = unit,
                mgm = mgmValue,
                metOffice = moValue,
                comparable = comparable,
                differenceUnit = canonical?.unit,
                note = note,
            )
        }
        return rows
    }

    /** Difference between two comparable values; null when not comparable. */
    fun difference(row: ComparisonRow): Double? {
        if (!row.comparable) return null
        val a = row.mgm?.value ?: return null
        val b = row.metOffice?.value ?: return null
        val target = row.differenceUnit ?: return null
        val convertedA = UnitConverter.convert(a, row.mgm?.unit, target) ?: return null
        val convertedB = UnitConverter.convert(b, row.metOffice?.unit, target) ?: return null
        return convertedA - convertedB
    }

    private fun isComparable(
        a: Measurement?,
        b: Measurement?,
        canonical: CanonicalParameter?,
        requireSameValidTime: Boolean,
        mgmValidAt: Long?,
        metOfficeValidAt: Long?,
    ): Boolean {
        if (a == null || b == null) return false
        if (a.value == null || b.value == null) return false
        if (a.availability != Availability.AVAILABLE || b.availability != Availability.AVAILABLE) {
            return false
        }
        // A canonical parameter must declare itself comparable (condition codes
        // are text and use different vocabularies).
        if (canonical?.comparable != true) return false
        val target = canonical.unit ?: return false
        if (!canConvert(a, b, target)) return false
        // Forecasts must refer to the same valid time.
        if (requireSameValidTime) {
            if (mgmValidAt == null || metOfficeValidAt == null) return false
            if (mgmValidAt != metOfficeValidAt) return false
        }
        return true
    }

    private fun canConvert(a: Measurement, b: Measurement, target: String): Boolean {
        val aValue = a.value ?: return false
        val bValue = b.value ?: return false
        return UnitConverter.convert(aValue, a.unit, target) != null &&
            UnitConverter.convert(bValue, b.unit, target) != null
    }

    private fun Measurement.toComparisonValue(
        source: String,
        validAt: Long?,
        issuedAt: Long?,
        collectedAt: Long,
        location: String?,
        dataType: DataType,
    ) = ComparisonValue(
        value = value,
        text = text,
        unit = unit,
        originalValue = originalValue,
        validAt = validAt,
        issuedAt = issuedAt,
        collectedAt = collectedAt,
        source = source,
        dataType = dataType,
        location = location,
        availability = availability,
    )
}

/**
 * Unit conversion helpers. Conversions are explicit, tested, and never applied
 * to the stored source value - they only feed the labelled comparison column.
 */
object UnitConverter {
    private const val KMH_PER_MS = 3.6
    private const val HPA_PER_PA = 1.0 / 100.0

    /** Wind speed to km/h. */
    fun windToKmh(value: Double, unit: String?): Double? = when (unit?.lowercase()) {
        "m/s", "ms", "mps" -> value * KMH_PER_MS
        "km/h", "kmh", "kph" -> value
        "knot", "kt", "knots" -> value * 1.852
        else -> null
    }

    /** Pressure to hPa. */
    fun pressureToHpa(value: Double, unit: String?): Double? = when (unit?.lowercase()) {
        "pa" -> value * HPA_PER_PA
        "hpa", "mbar" -> value
        "mb" -> value
        else -> null
    }

    /**
     * Precipitation rate to mm/h. An accumulated amount (mm) is not a rate and
     * is therefore not convertible.
     */
    fun precipitationToMmPerHour(value: Double, unit: String?): Double? = when (unit?.lowercase()) {
        "mm/hr", "mm/h" -> value
        else -> null
    }

    /**
     * Converts [value] from one unit to another. Returns null when the two
     * units cannot be reconciled - the caller must then show no difference
     * rather than an invented one.
     */
    fun convert(value: Double, from: String?, to: String?): Double? {
        if (from == null || to == null) return null
        val f = from.lowercase().trim()
        val t = to.lowercase().trim()
        if (f.isEmpty() || t.isEmpty()) return null
        if (f == t) return value

        // Wind: convert through km/h.
        val windKmh = when (f) {
            "m/s", "ms", "mps" -> value * KMH_PER_MS
            "km/h", "kmh", "kph" -> value
            "knot", "kt", "knots" -> value * 1.852
            else -> null
        }
        val windTarget = when (t) {
            "m/s", "ms", "mps" -> 1.0 / KMH_PER_MS
            "km/h", "kmh", "kph" -> 1.0
            "knot", "kt", "knots" -> 1.0 / 1.852
            else -> null
        }
        if (windKmh != null && windTarget != null) return windKmh * windTarget

        // Pressure: convert through hPa.
        val hpa = when (f) {
            "pa" -> value * HPA_PER_PA
            "hpa", "mbar", "mb" -> value
            else -> null
        }
        val pressureTarget = when (t) {
            "pa" -> 1.0 / HPA_PER_PA
            "hpa", "mbar", "mb" -> 1.0
            else -> null
        }
        if (hpa != null && pressureTarget != null) return hpa * pressureTarget

        // Same dimension, same unit string after normalisation.
        if (f.replace(" ", "") == t.replace(" ", "")) return value

        return null
    }
}
