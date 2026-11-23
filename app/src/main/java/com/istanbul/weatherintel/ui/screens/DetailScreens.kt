package com.istanbul.weatherintel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.istanbul.weatherintel.ui.IstanbulTimes
import com.istanbul.weatherintel.ui.WeatherDataReader
import com.istanbul.weatherintel.ui.components.LabelValueRow
import com.istanbul.weatherintel.ui.components.SectionTitle
import com.istanbul.weatherintel.ui.components.StatusPill
import com.istanbul.weatherintel.ui.components.WeatherCard
import com.istanbul.weatherintel.ui.format

/**
 * Detail screens required by the specification (sections A-L).
 *
 * They are opened from entry points on the Home page. The three top navigation
 * buttons (Home / Compare / Settings) remain visible at all times - there is no
 * bottom bar and no drawer.
 */

/** Every detail screen the app can open. */
enum class AppSection(val title: String) {
    DASHBOARD("Dashboard"),
    CURRENT("Current Weather"),
    HOURLY("Hourly Forecast"),
    THREE_HOURLY("Three-hourly Forecast"),
    DAILY("Daily Forecast"),
    HISTORICAL("Historical Data"),
    FORECAST_CHANGE("Forecast Change Analysis"),
    WARNINGS("Weather Warnings"),
    COLLECTION_HISTORY("Collection History"),
    DATA_EXPORT("Data Export"),
}

/** Top bar for a detail screen with a back button. */
@Composable
fun DetailHeader(
    section: AppSection,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Screen A - Dashboard. */
@Composable
fun DashboardScreen(uiState: WeatherUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("Istanbul")
                LabelValueRow("Timezone", "Europe/Istanbul (UTC+3)")
                LabelValueRow(
                    "Latest MGM observation",
                    uiState.mgmObservation?.let { IstanbulTimes.format(it.observedAt) }
                        ?: "No observation stored",
                )
                LabelValueRow(
                    "Latest Met Office data",
                    uiState.metOfficeObservation?.let { IstanbulTimes.format(it.observedAt) }
                        ?: "No data stored",
                )
                LabelValueRow(
                    "Automatic collection",
                    if (uiState.settings?.autoCollectEnabled == true) "Enabled" else "Disabled",
                )
                LabelValueRow(
                    "Collection interval",
                    "${uiState.settings?.collectionIntervalMinutes ?: 360} minutes",
                )
                LabelValueRow(
                    "Next expected collection",
                    "Estimate - Android may delay it",
                )
            }
        }
        item {
            WeatherCard {
                SectionTitle("Data freshness")
                LabelValueRow(
                    "MGM data age",
                    IstanbulTimes.relative(uiState.mgmObservation?.collectedAt),
                )
                LabelValueRow(
                    "Met Office data age",
                    IstanbulTimes.relative(uiState.metOfficeObservation?.collectedAt),
                )
                val failed = uiState.providerResults.count { it.status != "success" }
                if (failed > 0) {
                    Text(
                        text = "$failed provider collection(s) failed recently. " +
                            "Open Collection History for details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Screen B - Current Weather (separate MGM and Met Office views). */
@Composable
fun CurrentWeatherScreen(uiState: WeatherUiState) {
    val mgm = WeatherDataReader.readObservation(uiState.mgmObservation)
    val mo = WeatherDataReader.readObservation(uiState.metOfficeObservation)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("MGM - observed conditions")
                if (mgm == null) {
                    Text(
                        "No MGM observation stored yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LabelValueRow("Data type", "Observation (measured)")
                    LabelValueRow("Valid time", IstanbulTimes.format(mgm.observedAt))
                    LabelValueRow("Collected", IstanbulTimes.format(mgm.collectedAt))
                    LabelValueRow("Station", mgm.stationName ?: "Not provided")
                    Spacer(Modifier.height(6.dp))
                    mgm.measurements.filter { it.value != null || !it.text.isNullOrBlank() }
                        .forEach { m ->
                            LabelValueRow(
                                label = m.name,
                                value = m.value?.let { format(it) + (m.unit?.let { " $it" } ?: "") }
                                    ?: m.text.orEmpty(),
                            )
                        }
                }
            }
        }
        item {
            WeatherCard {
                SectionTitle("Met Office - forecast")
                if (mo == null) {
                    Text(
                        "No Met Office data stored yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LabelValueRow("Data type", "Forecast (not an observation)")
                    LabelValueRow("Valid time", IstanbulTimes.format(mo.observedAt))
                    LabelValueRow("Collected", IstanbulTimes.format(mo.collectedAt))
                    LabelValueRow("Point", mo.stationName ?: "Istanbul coordinates")
                    Spacer(Modifier.height(6.dp))
                    mo.measurements.filter { it.value != null || !it.text.isNullOrBlank() }
                        .forEach { m ->
                            LabelValueRow(
                                label = m.name,
                                value = m.value?.let { format(it) + (m.unit?.let { " $it" } ?: "") }
                                    ?: m.text.orEmpty(),
                            )
                        }
                }
            }
        }
    }
}

/** Screens C/D/E - forecast screens for hourly, three-hourly and daily. */
@Composable
fun ForecastScreen(
    uiState: WeatherUiState,
    product: String,
    title: String,
    mgmValues: List<ForecastValueEntity>,
    metOfficeValues: List<ForecastValueEntity>,
    mgmRunAt: Long?,
    metOfficeRunAt: Long?,
    metOfficeRowCount: Int,
) {
    var provider by remember { mutableStateOf("MGM") }
    val activeValues = if (provider == "MGM") mgmValues else metOfficeValues
    val grouped = remember(activeValues) {
        WeatherDataReader.groupByValidTime(activeValues)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle(title)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip("MGM", provider == "MGM") { provider = "MGM" }
                    ProviderChip("Met Office", provider == "Met Office") { provider = "Met Office" }
                }
                Spacer(Modifier.height(8.dp))
                if (provider == "MGM") {
                    LabelValueRow("Forecast issued", IstanbulTimes.format(mgmRunAt))
                    LabelValueRow("Periods stored", "${grouped.size}")
                } else {
                    LabelValueRow("Forecast issued", IstanbulTimes.format(metOfficeRunAt))
                    LabelValueRow("Periods stored", "${grouped.size} of $metOfficeRowCount")
                }
            }
        }

        if (activeValues.isEmpty()) {
            item {
                WeatherCard {
                    Text(
                        text = if (provider == "MGM") {
                            "No MGM data stored for this product."
                        } else {
                            "No Met Office data stored for this product. " +
                                "A Met Office API key is required."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            grouped.forEach { (validAt, values) ->
                item(key = "period-$validAt") {
                    WeatherCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(IstanbulTimes.format(validAt), Modifier.weight(1f))
                            StatusPill(
                                product,
                                MaterialTheme.colorScheme.primary,
                            )
                        }
                        values.forEach { v ->
                            LabelValueRow(
                                label = v.parameterName,
                                value = v.numericValue?.let {
                                    format(it) + (v.unit?.let { " $it" } ?: "")
                                } ?: v.textValue ?: "Not provided",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Screen G - Historical Data. */
@Composable
fun HistoricalScreen(uiState: WeatherUiState) {
    val mgm = WeatherDataReader.readObservation(uiState.mgmObservation)
    val mo = WeatherDataReader.readObservation(uiState.metOfficeObservation)
    val history = uiState.observationHistory

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("Collected records")
                LabelValueRow("Observations stored", "${uiState.storageStats?.observations ?: 0}")
                LabelValueRow("Forecast values stored", "${uiState.storageStats?.forecastValues ?: 0}")
                LabelValueRow("Raw responses stored", "${uiState.storageStats?.rawResponses ?: 0}")
                LabelValueRow("Collection runs", "${uiState.storageStats?.runs ?: 0}")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Historical collection begins when this app starts collecting. " +
                        "No licensed archive is imported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            WeatherCard {
                SectionTitle("Latest MGM observation - provenance")
                if (mgm == null) {
                    Text(
                        "No MGM observation stored yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LabelValueRow("Observed at", IstanbulTimes.format(mgm.observedAt))
                    LabelValueRow("Observed at (UTC)", IstanbulTimes.utc(mgm.observedAt))
                    LabelValueRow("Collected at", IstanbulTimes.format(mgm.collectedAt))
                    LabelValueRow("Provider", mgm.provider)
                    LabelValueRow("Data type", "Observation")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Original provider payload is retained in the database " +
                            "(provider-specific JSON) and is included in exports.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            WeatherCard {
                SectionTitle("Latest Met Office forecast - provenance")
                if (mo == null) {
                    Text(
                        "No Met Office data stored yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LabelValueRow("Valid at", IstanbulTimes.format(mo.observedAt))
                    LabelValueRow("Valid at (UTC)", IstanbulTimes.utc(mo.observedAt))
                    LabelValueRow("Collected at", IstanbulTimes.format(mo.collectedAt))
                    LabelValueRow("Provider", mo.provider)
                    LabelValueRow("Data type", "Forecast")
                }
            }
        }

        // Real stored series - a point is only plotted when the provider
        // actually supplied that value.
        val temperature = WeatherDataReader.observationSeries(
            observations = history,
            label = "Temperature",
            unit = "degC",
            codes = listOf("sicaklik"),
        )
        val humidity = WeatherDataReader.observationSeries(
            observations = history,
            label = "Relative humidity",
            unit = "%",
            codes = listOf("nem"),
        )
        val wind = WeatherDataReader.observationSeries(
            observations = history,
            label = "Wind speed",
            unit = "km/h",
            codes = listOf("ruzgarHiz"),
        )
        val pressure = WeatherDataReader.observationSeries(
            observations = history,
            label = "Sea-level pressure",
            unit = "hPa",
            codes = listOf("denizeIndirgenmisBasinc"),
        )
        val rain = WeatherDataReader.observationSeries(
            observations = history,
            label = "Precipitation (24 h)",
            unit = "mm",
            codes = listOf("yagis24Saat"),
        )

        item {
            WeatherCard {
                SectionTitle("Stored temperature series")
                LabelValueRow("Points stored", "${temperature.points.size}")
                Spacer(Modifier.height(8.dp))
                LineChart(points = temperature.points, unit = "degC")
            }
        }
        item {
            WeatherCard {
                SectionTitle("Stored humidity series")
                LabelValueRow("Points stored", "${humidity.points.size}")
                Spacer(Modifier.height(8.dp))
                LineChart(points = humidity.points, unit = "%")
            }
        }
        item {
            WeatherCard {
                SectionTitle("Stored wind speed series")
                LabelValueRow("Points stored", "${wind.points.size}")
                Spacer(Modifier.height(8.dp))
                LineChart(points = wind.points, unit = "km/h")
            }
        }
        item {
            WeatherCard {
                SectionTitle("Stored sea-level pressure series")
                LabelValueRow("Points stored", "${pressure.points.size}")
                Spacer(Modifier.height(8.dp))
                LineChart(points = pressure.points, unit = "hPa")
            }
        }
        item {
            WeatherCard {
                SectionTitle("Stored precipitation series")
                LabelValueRow("Points stored", "${rain.points.size}")
                Spacer(Modifier.height(8.dp))
                LineChart(points = rain.points, unit = "mm")
            }
        }
        if (history.isNotEmpty()) {
            item {
                WeatherCard {
                    SectionTitle("Stored observations")
                    history.forEach { entity ->
                        LabelValueRow(
                            label = IstanbulTimes.format(entity.observedAt),
                            value = "${entity.sourceRecordId ?: "Istanbul"} - " +
                                "collected ${IstanbulTimes.format(entity.collectedAt)}",
                        )
                    }
                }
            }
        }
    }
}

/** Screen H - Forecast Change Analysis. */
@Composable
fun ForecastChangeScreen(uiState: WeatherUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("Successive forecast versions")
                Text(
                    "This analysis compares the stored forecast versions for the same " +
                        "valid time. Issue time and valid time are kept distinct. " +
                        "At least two collection runs are needed before changes appear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ChangeCard(
                title = "MGM forecast change",
                change = uiState.mgmForecastChange,
            )
        }
        item {
            ChangeCard(
                title = "Met Office forecast change",
                change = uiState.metOfficeForecastChange,
            )
        }

        item {
            WeatherCard {
                SectionTitle("Stored forecast runs")
                val runs = uiState.runs
                if (runs.isEmpty()) {
                    Text(
                        "No collection runs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    runs.forEach { run ->
                        LabelValueRow(
                            label = "#${run.id} ${run.trigger}",
                            value = "${run.overallStatus} - ${IstanbulTimes.format(run.startedAt)}",
                        )
                    }
                }
            }
        }
    }
}

/** One provider's forecast-change comparison. */
@Composable
private fun ChangeCard(
    title: String,
    change: com.istanbul.weatherintel.data.repository.ForecastChangeBuilder.Change?,
) {
    WeatherCard {
        SectionTitle(title)
        if (change == null || !change.hasComparison) {
            Text(
                "Two stored versions of this forecast are needed before a change can " +
                    "be calculated. Collect again after the first run.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@WeatherCard
        }
        LabelValueRow("Previous issue", IstanbulTimes.format(change.previousRun?.collectedAt))
        LabelValueRow("Latest issue", IstanbulTimes.format(change.currentRun?.collectedAt))
        LabelValueRow(
            "Parameters compared",
            "${change.deltas.count { it.status != com.istanbul.weatherintel.data.repository.ForecastChangeBuilder.Status.UNCHANGED }} changed" +
                " of ${change.deltas.size}",
        )
        Spacer(Modifier.height(6.dp))

        val grouped = com.istanbul.weatherintel.data.repository.ForecastChangeBuilder
            .byValidTime(change.deltas)
        val changed = grouped.filter { (_, deltas) ->
            deltas.any {
                it.status !=
                    com.istanbul.weatherintel.data.repository.ForecastChangeBuilder.Status.UNCHANGED
            }
        }
        if (changed.isEmpty()) {
            Text(
                "The two stored versions are identical for every shared valid time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@WeatherCard
        }
        changed.forEach { (validAt, deltas) ->
            Text(
                text = IstanbulTimes.format(validAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            deltas.filter {
                it.status !=
                    com.istanbul.weatherintel.data.repository.ForecastChangeBuilder.Status.UNCHANGED
            }.forEach { delta ->
                val before = delta.previousValue?.let { format(it) } ?: "Not provided"
                val after = delta.currentValue?.let { format(it) } ?: "Not provided"
                val movement = delta.change?.let {
                    (if (it > 0) "+" else "") + format(it)
                } ?: "-"
                LabelValueRow(
                    label = delta.parameterName,
                    value = "$before -> $after ($movement${delta.unit?.let { " $it" } ?: ""})",
                )
            }
        }
    }
}

/** Screen I - Weather Warnings. */
@Composable
fun WarningsScreen(uiState: WeatherUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("Available warning feeds")
                Text(
                    "MGM exposes no public warning endpoint (verified: /web/uyarilar " +
                        "returns HTTP 404). The Met Office Global Spot product does not " +
                        "include warnings for Istanbul. No warnings are invented here - " +
                        "this list will only contain warnings from an authorized feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uiState.warnings.isEmpty()) {
            item {
                WeatherCard {
                    Text(
                        "No warnings have been received from any authorized provider.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(uiState.warnings) { w ->
                WeatherCard {
                    SectionTitle(w.title ?: "Weather warning")
                    LabelValueRow("Severity", w.severity ?: "Not provided")
                    LabelValueRow("Issued", IstanbulTimes.format(w.issuedAt))
                    LabelValueRow(
                        "Valid from",
                        IstanbulTimes.format(w.validFrom),
                    )
                    LabelValueRow("Valid to", IstanbulTimes.format(w.validTo))
                    w.description?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Screen J - Collection History. */
@Composable
fun CollectionHistoryScreen(uiState: WeatherUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.runs.isEmpty()) {
            item {
                WeatherCard {
                    Text(
                        "No collection runs recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(uiState.runs) { run ->
            WeatherCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Run #${run.id}", Modifier.weight(1f))
                    StatusPill(
                        run.overallStatus,
                        if (run.overallStatus == "success") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                LabelValueRow("Trigger", run.trigger)
                LabelValueRow("Started", IstanbulTimes.format(run.startedAt))
                LabelValueRow("Finished", IstanbulTimes.format(run.finishedAt))
                LabelValueRow(
                    "Duration",
                    "${((run.finishedAt ?: run.startedAt) - run.startedAt).coerceAtLeast(0)} ms",
                )

                LabelValueRow("Interval configured", "${run.configuredIntervalMinutes} min")
                LabelValueRow("App version", run.appVersion)
                LabelValueRow(
                    "Providers",
                    "${run.successCount} succeeded / ${run.failureCount} failed " +
                        "of ${run.requestedProviderCount}",
                )
                val results = uiState.providerResults.filter { it.collectionRunId == run.id }
                results.forEach { r ->
                    LabelValueRow(
                        label = "Provider #${r.providerId}",
                        value = "${r.status} - ${r.recordsSaved} records " +
                            "(${r.recordsReceived} received)",
                    )
                    r.errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        if (uiState.errors.isNotEmpty()) {
            item {
                WeatherCard {
                    SectionTitle("Recent errors")
                    uiState.errors.forEach { e ->
                        Text(
                            text = "${IstanbulTimes.format(e.occurredAt)} - ${e.errorType}: ${e.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Error details never contain API keys or authorization headers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            RawResponseCard(uiState)
        }
    }
}

/**
 * Raw provider payloads.
 *
 * Every HTTP response is stored verbatim (with a SHA-256 hash) so the parsed
 * values can always be traced back to the exact bytes the provider sent. The
 * body is shown truncated in the list and in full when expanded.
 */
@Composable
private fun RawResponseCard(uiState: WeatherUiState) {
    var expandedId by remember { mutableStateOf<Long?>(null) }

    WeatherCard {
        SectionTitle("Raw provider responses")
        if (uiState.rawResponses.isEmpty()) {
            Text(
                "No raw responses stored yet. They are kept as soon as the first " +
                    "collection runs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@WeatherCard
        }
        Text(
            "The exact bytes returned by each provider, kept for provenance. " +
                "Request metadata excludes credentials.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        uiState.rawResponses.forEach { raw ->
            val expanded = expandedId == raw.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable { expandedId = if (expanded) null else raw.id }
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = raw.productOrEndpoint,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "HTTP ${raw.httpStatus ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${IstanbulTimes.format(raw.retrievedAt)} - " +
                        "${raw.responseBody.length} characters - sha-256 " +
                        raw.responseHash,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = raw.responseBody,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Content type: ${raw.contentType ?: "unknown"} - " +
                            "request: ${raw.requestMetadata}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Tap to view the full payload",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Screen L - Data Export. */
@Composable
fun DataExportScreen(uiState: WeatherUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("Export options")
                Text(
                    "CSV and JSON exports are available from Settings > Data export. " +
                        "Exports include timestamps (UTC and Istanbul local), units, " +
                        "original provider values and provenance references. " +
                        "Credentials are never included in an export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            WeatherCard {
                SectionTitle("Preview")
                val text = uiState.exportText
                if (text.isNullOrBlank()) {
                    Text(
                        "No export prepared yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LabelValueRow("File", uiState.exportFileName ?: "export")
                    LabelValueRow("Format", uiState.exportFormat.name)
                    LabelValueRow("Size", "${text.length} characters")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

