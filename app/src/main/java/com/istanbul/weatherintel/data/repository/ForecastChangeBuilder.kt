package com.istanbul.weatherintel.data.repository

import com.istanbul.weatherintel.data.db.ForecastRunEntity
import com.istanbul.weatherintel.data.db.ForecastValueEntity

/**
 * Forecast change analysis.
 *
 * Compares two stored versions of the same forecast product and reports, for
 * every valid time and every parameter present in BOTH versions, how much the
 * value moved between the earlier issue and the later one.
 *
 * Rules:
 *  - issue time and valid time stay distinct; a change is always reported
 *    against the valid time it applies to,
 *  - only parameters present in both versions are compared,
 *  - a parameter that disappeared or appeared is reported as such rather than
 *    being treated as a change to zero,
 *  - no value is ever invented.
 */
object ForecastChangeBuilder {

    /** One parameter's movement between two forecast versions. */
    data class Delta(
        val validAt: Long,
        val parameterCode: String,
        val parameterName: String,
        val unit: String?,
        val previousValue: Double?,
        val currentValue: Double?,
        val change: Double?,
        val status: Status,
    )

    enum class Status {
        /** The parameter exists in both versions and moved. */
        CHANGED,

        /** The parameter exists in both versions with the same value. */
        UNCHANGED,

        /** The parameter is only in the newer version. */
        ADDED,

        /** The parameter is only in the older version. */
        REMOVED,
    }

    /** The full comparison of two forecast versions. */
    data class Change(
        val provider: String,
        val product: String,
        val previousRun: ForecastRunEntity?,
        val currentRun: ForecastRunEntity?,
        val deltas: List<Delta>,
    ) {
        val hasComparison: Boolean get() = previousRun != null && currentRun != null
    }

    /** Builds the comparison between two stored forecast versions. */
    fun build(
        provider: String,
        product: String,
        previousRun: ForecastRunEntity?,
        currentRun: ForecastRunEntity?,
        previousValues: List<ForecastValueEntity>,
        currentValues: List<ForecastValueEntity>,
    ): Change {
        val previousByKey = previousValues.associateBy { it.validAt to it.parameterCode }
        val currentByKey = currentValues.associateBy { it.validAt to it.parameterCode }

        val keys = (previousByKey.keys + currentByKey.keys).sortedWith(
            compareBy({ it.first }, { it.second }),
        )

        val deltas = keys.map { key ->
            val before = previousByKey[key]
            val after = currentByKey[key]
            val reference = after ?: before!!
            val previousValue = before?.numericValue
            val currentValue = after?.numericValue
            val change = if (before != null && after != null &&
                previousValue != null && currentValue != null
            ) {
                currentValue - previousValue
            } else {
                null
            }
            Delta(
                validAt = key.first,
                parameterCode = key.second,
                parameterName = reference.parameterName,
                unit = reference.unit,
                previousValue = previousValue,
                currentValue = currentValue,
                change = change,
                status = when {
                    before == null -> Status.ADDED
                    after == null -> Status.REMOVED
                    change != null && kotlin.math.abs(change) > 1e-9 -> Status.CHANGED
                    else -> Status.UNCHANGED
                },
            )
        }

        return Change(
            provider = provider,
            product = product,
            previousRun = previousRun,
            currentRun = currentRun,
            deltas = deltas,
        )
    }

    /** Groups deltas by valid time, keeping the order. */
    fun byValidTime(deltas: List<Delta>): Map<Long, List<Delta>> =
        deltas.groupBy { it.validAt }.toSortedMap()
}
