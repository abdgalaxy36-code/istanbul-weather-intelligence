package com.istanbul.weatherintel.ui.compare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.istanbul.weatherintel.WeatherUiState
import com.istanbul.weatherintel.data.db.ForecastValueEntity
import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ComparisonRow
import com.istanbul.weatherintel.data.model.ComparisonValue
import com.istanbul.weatherintel.data.model.DataType
import com.istanbul.weatherintel.data.repository.ComparisonBuilder
import com.istanbul.weatherintel.data.repository.ComparisonScope
import com.istanbul.weatherintel.ui.IstanbulTimes
import com.istanbul.weatherintel.ui.WeatherActions
import com.istanbul.weatherintel.ui.WeatherDataReader
import com.istanbul.weatherintel.ui.components.LabelValueRow
import com.istanbul.weatherintel.ui.components.SectionTitle
import com.istanbul.weatherintel.ui.components.StatusPill
import com.istanbul.weatherintel.ui.components.WeatherCard
import com.istanbul.weatherintel.ui.format

/**
 * Compare page: the dedicated full-comparison screen.
 *
 * Everything the enabled providers actually returned is listed, grouped into
 * categories and expandable. There is no fixed row count, no whitelist and no
 * truncation. Differences are shown only where the values are genuinely
 * comparable (same quantity, compatible unit, aligned valid time).
 *
 * MGM and the Met Office use different parameter keys, so parameters are lined
 * up through [ComparisonScope] / the parameter crosswalk. A parameter that only
 * one provider publishes is still listed, in its own column, marked as
 * provider-specific.
 */
@Composable
fun CompareScreen(
    uiState: WeatherUiState,
    actions: WeatherActions,
) {
    val mgmObs = WeatherDataReader.readObservation(uiState.mgmObservation)
    val moObs = WeatherDataReader.readObservation(uiState.metOfficeObservation)

    // Current conditions. The Met Office Global Spot product has no Istanbul
    // observation product, so this section normally carries MGM only - which is
    // stated explicitly rather than papered over.
    val observationRows = remember(mgmObs, moObs) {
        ComparisonBuilder.build(
            mgm = mgmObs?.measurements.orEmpty(),
            metOffice = moObs?.measurements.orEmpty(),
            scope = ComparisonScope.OBSERVATION,
            mgmSource = "MGM",
            metOfficeSource = "Met Office",
            mgmValidAt = mgmObs?.observedAt,
            metOfficeValidAt = moObs?.observedAt,
            mgmCollectedAt = mgmObs?.collectedAt ?: 0L,
            metOfficeCollectedAt = moObs?.collectedAt ?: 0L,
            mgmLocation = mgmObs?.stationName,
            metOfficeLocation = moObs?.stationName,
            dataType = DataType.OBSERVATION,
        )
    }

    // MGM's "saatlik" product has 3-hour steps, so it is lined up against the
    // Met Office three-hourly product.
    val threeHourlyPeriods = remember(
        uiState.mgmHourlyValues,
        uiState.metOfficeThreeHourlyValues,
    ) {
        ComparisonBuilder.buildAligned(
            mgm = uiState.mgmHourlyValues.toTimed(),
            metOffice = uiState.metOfficeThreeHourlyValues.toTimed(),
            scope = ComparisonScope.THREE_HOURLY,
            mgmSource = "MGM",
            metOfficeSource = "Met Office",
            mgmIssuedAt = uiState.mgmHourlyRun?.issuedAt,
            metOfficeIssuedAt = uiState.metOfficeThreeHourlyRun?.issuedAt,
            mgmLocation = "Istanbul",
            metOfficeLocation = "Istanbul",
        )
    }

    val dailyPeriods = remember(
        uiState.mgmDailyValues,
        uiState.metOfficeDailyValues,
    ) {
        ComparisonBuilder.buildAligned(
            mgm = uiState.mgmDailyValues.toTimed(),
            metOffice = uiState.metOfficeDailyValues.toTimed(),
            scope = ComparisonScope.DAILY,
            mgmSource = "MGM",
            metOfficeSource = "Met Office",
            mgmIssuedAt = uiState.mgmDailyRun?.issuedAt,
            metOfficeIssuedAt = uiState.metOfficeDailyRun?.issuedAt,
            mgmLocation = "Istanbul",
            metOfficeLocation = "Istanbul",
        )
    }

    // The Met Office hourly product is the only genuinely hourly series
    // available, so it is listed on its own.
    val hourlyPeriods = remember(uiState.metOfficeHourlyValues) {
        ComparisonBuilder.buildAligned(
            mgm = emptyList(),
            metOffice = uiState.metOfficeHourlyValues.toTimed(),
            scope = ComparisonScope.HOURLY,
            mgmSource = "MGM",
            metOfficeSource = "Met Office",
            metOfficeIssuedAt = uiState.metOfficeHourlyRun?.issuedAt,
            metOfficeLocation = "Istanbul",
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("MGM vs Met Office")
                Text(
                    "Every field each provider actually supplied is listed below, " +
                        "including provider-specific fields with no counterpart. " +
                        "Parameters that both providers publish are lined up and " +
                        "aligned on their valid time. Missing values are shown as " +
                        "\"Not provided\" - never as zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ProviderHealthCard(uiState)
        }

        if (observationRows.isEmpty()) {
            item {
                WeatherCard {
                    Text(
                        "No observations stored yet. Use Collect Now to fetch data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                ProvenanceRow(mgmObs, moObs)
            }
            item {
                WeatherCard {
                    SectionTitle("Current conditions")
                    Text(
                        "MGM publishes Istanbul station observations. The Met Office " +
                            "Global Spot product is forecast-only and publishes no " +
                            "Istanbul observation product, so no measured Met Office " +
                            "value exists to compare against.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            groupedRows(observationRows, "Current conditions (observations)")
        }

        item {
            SectionHeader(
                title = "Three-hourly forecast",
                subtitle = "MGM's saatlik product (3-hour steps) against the Met Office " +
                    "three-hourly product, aligned on valid time.",
                left = "MGM ${uiState.mgmHourlyRun?.rowCount ?: 0} periods · issued ${IstanbulTimes.format(uiState.mgmHourlyRun?.issuedAt)} · collected ${IstanbulTimes.format(uiState.mgmHourlyRun?.collectedAt)}",
                right = "Met Office ${uiState.metOfficeThreeHourlyRun?.rowCount ?: 0} periods · issued ${IstanbulTimes.format(uiState.metOfficeThreeHourlyRun?.issuedAt)} · collected ${IstanbulTimes.format(uiState.metOfficeThreeHourlyRun?.collectedAt)}",
            )
        }
        periodItems(threeHourlyPeriods, "three-hourly")

        item {
            SectionHeader(
                title = "Daily forecast",
                subtitle = "MGM daily against Met Office daily, aligned on valid time.",
                left = "MGM ${uiState.mgmDailyRun?.rowCount ?: 0} days · issued ${IstanbulTimes.format(uiState.mgmDailyRun?.issuedAt)} · collected ${IstanbulTimes.format(uiState.mgmDailyRun?.collectedAt)}",
                right = "Met Office ${uiState.metOfficeDailyRun?.rowCount ?: 0} days · issued ${IstanbulTimes.format(uiState.metOfficeDailyRun?.issuedAt)} · collected ${IstanbulTimes.format(uiState.metOfficeDailyRun?.collectedAt)}",
            )
        }
        periodItems(dailyPeriods, "daily")

        item {
            SectionHeader(
                title = "Hourly forecast",
                subtitle = "The Met Office hourly series. MGM publishes no hourly-resolution " +
                    "forecast - its saatlik product has 3-hour steps and is listed above.",
                left = "MGM not available",
                right = "Met Office ${uiState.metOfficeHourlyRun?.rowCount ?: 0} periods · issued ${IstanbulTimes.format(uiState.metOfficeHourlyRun?.issuedAt)} · collected ${IstanbulTimes.format(uiState.metOfficeHourlyRun?.collectedAt)}",
            )
        }
        periodItems(hourlyPeriods, "hourly")

        item {
            WarningsSection(uiState)
        }
    }
}

/** Converts stored forecast rows into timed measurements. */
private fun List<ForecastValueEntity>.toTimed(): List<ComparisonBuilder.TimedMeasurement> =
    map { ComparisonBuilder.TimedMeasurement(it.validAt, WeatherDataReader.toMeasurement(it)) }

/** A section title with provider counts. */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    left: String,
    right: String,
) {
    WeatherCard {
        SectionTitle(title)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LabelValueRow("MGM", left)
        LabelValueRow("Met Office", right)
    }
}

/** Emits one expandable group per category. */
private fun LazyListScope.groupedRows(
    rows: List<ComparisonRow>,
    defaultTitle: String,
) {
    val byCategory = rows.groupBy { it.category }.toList().sortedBy { it.first }
    byCategory.forEach { (category, categoryRows) ->
        item(key = "cat-$category-$defaultTitle") {
            ExpandableCategory(
                title = category.ifBlank { defaultTitle },
                rows = categoryRows,
                initiallyExpanded = true,
            )
        }
    }
}

/** Emits one card per aligned valid time. */
private fun LazyListScope.periodItems(
    periods: List<ComparisonBuilder.AlignedPeriod>,
    product: String,
) {
    if (periods.isEmpty()) {
        item(key = "empty-$product") {
            WeatherCard {
                Text(
                    if (product == "hourly") {
                        "No Met Office hourly forecast stored yet. A Met Office API key " +
                            "is required."
                    } else {
                        "No $product forecast stored yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    items(periods, key = { "$product-${it.validAt}" }) { period ->
        val paired = period.rows.count { it.mgm != null && it.metOffice != null }
        WeatherCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(IstanbulTimes.format(period.validAt), Modifier.weight(1f))
                StatusPill(
                    if (paired > 0) "$paired paired" else "single provider",
                    if (paired > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Valid time (UTC): ${IstanbulTimes.utc(period.validAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            period.rows.groupBy { it.category }.toList().sortedBy { it.first }
                .forEach { (category, categoryRows) ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    categoryRows.forEach { row -> ComparisonRowView(row) }
                }
        }
    }
}

@Composable
private fun ProviderHealthCard(uiState: WeatherUiState) {
    val mgm = uiState.providerResults.firstOrNull { it.providerId == 1L }
    val metOffice = uiState.providerResults.firstOrNull { it.providerId == 2L }
    WeatherCard {
        SectionTitle("Provider collection status")
        LabelValueRow("MGM", mgm?.let { "${it.status} · ${IstanbulTimes.format(it.finishedAt)}" } ?: "No result yet")
        mgm?.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        LabelValueRow("Met Office", metOffice?.let { "${it.status} · ${IstanbulTimes.format(it.finishedAt)}" } ?: "No result yet")
        metOffice?.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProvenanceRow(
    mgmObs: WeatherDataReader.ObservedView?,
    moObs: WeatherDataReader.ObservedView?,
) {
    WeatherCard {
        SectionTitle("Provenance")
        LabelValueRow(
            "MGM observed",
            mgmObs?.let { IstanbulTimes.format(it.observedAt) } ?: "Not available",
        )
        LabelValueRow(
            "MGM collected",
            mgmObs?.let { IstanbulTimes.format(it.collectedAt) } ?: "Not available",
        )
        LabelValueRow(
            "Met Office valid",
            moObs?.let { IstanbulTimes.format(it.observedAt) } ?: "Not available",
        )
        LabelValueRow(
            "Met Office collected",
            moObs?.let { IstanbulTimes.format(it.collectedAt) } ?: "Not available",
        )
    }
}

/** One expandable category with every field in it. */
@Composable
private fun ExpandableCategory(
    title: String,
    rows: List<ComparisonRow>,
    initiallyExpanded: Boolean,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    WeatherCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(title, Modifier.weight(1f))
            StatusPill(
                "${rows.size} field${if (rows.size == 1) "" else "s"}",
                MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                rows.forEach { row ->
                    ComparisonRowView(row)
                }
            }
        }
    }
}

@Composable
private fun ComparisonRowView(row: ComparisonRow) {
    var expanded by remember(row) { mutableStateOf(false) }
    val difference = ComparisonBuilder.difference(row)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.unit?.let { "Normalized unit: $it" } ?: "Provider-specific or non-numeric field",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse ${row.name} details" else "Expand ${row.name} details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        ValueColumn(label = "MGM", value = row.mgm)
        Spacer(Modifier.height(6.dp))
        ValueColumn(label = "Met Office", value = row.metOffice)

        if (difference != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Difference (MGM − Met Office): ${format(difference)}" +
                    (row.differenceUnit?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    "Provider detail",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                ExpandedValueDetails("MGM", row.mgm)
                ExpandedValueDetails("Met Office", row.metOffice)
                row.note?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Comparability: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }
}

@Composable
private fun ValueColumn(label: String, value: ComparisonValue?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        if (value == null) {
            Text(
                text = "Not provided",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = value.display().ifBlank { "Not provided" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.availability == Availability.AVAILABLE) {
                    MaterialTheme.colorScheme.onSurface
                } else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            value.unit?.let {
                Text(
                    text = "Source unit: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpandedValueDetails(label: String, value: ComparisonValue?) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (value == null) {
            Text(
                "Value: Not provided",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        LabelValueRow("Original provider value", value.originalValue ?: "Not provided")
        LabelValueRow("Displayed / normalized value", value.display().ifBlank { "Not provided" })
        LabelValueRow("Original unit", value.unit ?: "Not provided")
        LabelValueRow("Valid time", value.validAt?.let { IstanbulTimes.format(it) } ?: "Not provided")
        LabelValueRow("Availability", value.availability.name.replace('_', ' '))
    }
}

@Composable
private fun WarningsSection(uiState: WeatherUiState) {
    WeatherCard {
        SectionTitle("Warnings")
        if (uiState.warnings.isEmpty()) {
            Text(
                "No warnings available. MGM publishes no accessible warning feed " +
                    "and Met Office Global Spot does not include warnings for Istanbul. " +
                    "No warnings are invented.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@WeatherCard
        }
        uiState.warnings.forEach { w ->
            LabelValueRow(w.title ?: "Warning", w.severity ?: "")
            LabelValueRow("Source", "MGM")
            LabelValueRow("Issued", IstanbulTimes.format(w.issuedAt))
            LabelValueRow(
                "Valid",
                "${IstanbulTimes.format(w.validFrom)} - ${IstanbulTimes.format(w.validTo)}",
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

