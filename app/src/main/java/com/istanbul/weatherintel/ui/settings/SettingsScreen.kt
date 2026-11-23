package com.istanbul.weatherintel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.istanbul.weatherintel.ExportFormat
import com.istanbul.weatherintel.WeatherUiState
import com.istanbul.weatherintel.data.prefs.SettingsRepository
import com.istanbul.weatherintel.data.prefs.ThemeMode
import com.istanbul.weatherintel.data.provider.MgmStationCatalogue
import com.istanbul.weatherintel.ui.IstanbulTimes
import com.istanbul.weatherintel.ui.WeatherActions
import com.istanbul.weatherintel.ui.components.LabelValueRow
import com.istanbul.weatherintel.ui.components.SectionTitle
import com.istanbul.weatherintel.ui.components.StatusPill
import com.istanbul.weatherintel.ui.components.WeatherCard

/** Settings UI is a state renderer; every change is sent through existing actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: WeatherUiState,
    actions: WeatherActions,
) {
    val settings = uiState.settings
    var apiKey by remember { mutableStateOf("") }
    var confirmClearKey by remember { mutableStateOf(false) }
    var exportProvider by remember { mutableStateOf("All providers") }

    val savedInterval = settings?.collectionIntervalMinutes ?: SettingsRepository.DEFAULT_INTERVAL_MINUTES
    var intervalUnit by remember(savedInterval) {
        mutableStateOf(
            when {
                savedInterval % (24 * 60) == 0 -> IntervalUnit.DAYS
                savedInterval % 60 == 0 -> IntervalUnit.HOURS
                else -> IntervalUnit.MINUTES
            }
        )
    }
    var intervalValue by remember(savedInterval) {
        mutableStateOf(
            when (intervalUnit) {
                IntervalUnit.MINUTES -> savedInterval.toString()
                IntervalUnit.HOURS -> (savedInterval / 60).toString()
                IntervalUnit.DAYS -> (savedInterval / (24 * 60)).toString()
            }
        )
    }

    val nextCollectionAt = settings?.takeIf {
        it.autoCollectEnabled && it.lastSuccessfulCollectionAt > 0L
    }?.let { it.lastSuccessfulCollectionAt + it.collectionIntervalMinutes * 60_000L }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeatherCard {
                SectionTitle("Collection schedule")
                SettingSwitch(
                    title = "Automatic collection",
                    subtitle = "Allow scheduled background collection",
                    checked = settings?.autoCollectEnabled ?: true,
                    onCheckedChange = actions.onSetAutoCollect,
                )
                SettingSwitch(
                    title = "Collect when the app opens",
                    subtitle = "Start a collection when the app is launched",
                    checked = settings?.collectOnAppOpen ?: false,
                    onCheckedChange = actions.onSetCollectOnAppOpen,
                )
                Spacer(Modifier.height(8.dp))
                SectionTitle("Collection interval")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = intervalValue,
                        onValueChange = { intervalValue = it.filter(Char::isDigit).take(5) },
                        label = { Text("Every") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IntervalUnitRow(
                        selected = intervalUnit,
                        onSelect = { unit ->
                            val currentMinutes = intervalValue.toIntOrNull()?.let { n ->
                                when (intervalUnit) {
                                    IntervalUnit.MINUTES -> n
                                    IntervalUnit.HOURS -> n * 60
                                    IntervalUnit.DAYS -> n * 24 * 60
                                }
                            } ?: savedInterval
                            intervalUnit = unit
                            intervalValue = when (unit) {
                                IntervalUnit.MINUTES -> currentMinutes.toString()
                                IntervalUnit.HOURS -> (currentMinutes / 60).coerceAtLeast(1).toString()
                                IntervalUnit.DAYS -> (currentMinutes / (24 * 60)).coerceAtLeast(1).toString()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val amount = intervalValue.toIntOrNull()?.coerceAtLeast(1) ?: 6
                        val requestedMinutes = when (intervalUnit) {
                            IntervalUnit.MINUTES -> amount
                            IntervalUnit.HOURS -> amount * 60
                            IntervalUnit.DAYS -> amount * 24 * 60
                        }
                        actions.onSetInterval(
                            requestedMinutes.coerceIn(
                                SettingsRepository.MIN_INTERVAL_MINUTES,
                                SettingsRepository.MAX_INTERVAL_MINUTES,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Save interval")
                }
                Text(
                    "Minimum periodic interval: 15 minutes. Android may defer background work " +
                        "because of battery optimisation, Doze mode, connectivity or system scheduling.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            WeatherCard {
                SectionTitle("Weather providers")
                SettingSwitch(
                    title = "MGM · Turkish State Meteorological Service",
                    subtitle = "Istanbul station observations and available forecasts",
                    checked = settings?.mgmEnabled ?: true,
                    onCheckedChange = actions.onSetMgmEnabled,
                )
                SettingSwitch(
                    title = "Met Office Weather DataHub",
                    subtitle = if (settings?.metOfficeConfigured == true) {
                        "API key saved; licensed Global Spot products enabled"
                    } else "Add an API key below to enable collection",
                    checked = settings?.metOfficeEnabled ?: true,
                    onCheckedChange = actions.onSetMetOfficeEnabled,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = actions.onCollectNow,
                    enabled = !uiState.collecting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    if (uiState.collecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Collecting…")
                    } else Text("Collect Now")
                }
                uiState.message?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item { StationPickerCard(uiState, actions) }

        item {
            WeatherCard {
                SectionTitle("Collection status")
                LabelValueRow(
                    "Last successful collection",
                    settings?.lastSuccessfulCollectionAt?.takeIf { it > 0L }
                        ?.let { IstanbulTimes.format(it) } ?: "Never",
                )
                LabelValueRow("Overall status", settings?.lastCollectionStatus ?: "Not available")
                val mgmResult = uiState.providerResults.firstOrNull { it.providerId == 1L }
                val metOfficeResult = uiState.providerResults.firstOrNull { it.providerId == 2L }
                LabelValueRow(
                    "MGM provider status",
                    mgmResult?.let { "${it.status} · ${IstanbulTimes.format(it.finishedAt)}" } ?: "No result yet",
                )
                LabelValueRow(
                    "Met Office provider status",
                    metOfficeResult?.let { "${it.status} · ${IstanbulTimes.format(it.finishedAt)}" } ?: "No result yet",
                )
                LabelValueRow(
                    "Next collection (estimate)",
                    when {
                        settings?.autoCollectEnabled == false -> "Automatic collection is off"
                        nextCollectionAt != null -> IstanbulTimes.format(nextCollectionAt)
                        else -> "Estimated after the first successful collection"
                    },
                )
                if (uiState.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Collection errors")
                    uiState.errors.forEach { error ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = "${error.errorType} · ${IstanbulTimes.format(error.occurredAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            WeatherCard {
                SectionTitle("Appearance")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeOption("System Default", uiState.themeMode == ThemeMode.SYSTEM) {
                        actions.onSetThemeMode(ThemeMode.SYSTEM)
                    }
                    ThemeOption("Light", uiState.themeMode == ThemeMode.LIGHT) {
                        actions.onSetThemeMode(ThemeMode.LIGHT)
                    }
                    ThemeOption("Dark", uiState.themeMode == ThemeMode.DARK) {
                        actions.onSetThemeMode(ThemeMode.DARK)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "System Default follows Android's appearance. Your selected mode is persisted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            WeatherCard {
                SectionTitle("Met Office API key")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Paste Weather DataHub key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            actions.onSetMetOfficeKey(apiKey.trim())
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Save key") }
                    OutlinedButton(
                        onClick = { confirmClearKey = true },
                        enabled = settings?.metOfficeConfigured == true,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Clear key") }
                }
                Spacer(Modifier.height(8.dp))
                StatusPill(
                    text = if (settings?.metOfficeConfigured == true) "API key configured" else "No API key saved",
                    color = if (settings?.metOfficeConfigured == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The key is encrypted before local storage and is excluded from logs and exports. " +
                        "A credential entered into a distributed client app cannot be made completely secret.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            WeatherCard {
                SectionTitle("Storage and history")
                val stats = uiState.storageStats
                LabelValueRow("Observations", "${stats?.observations ?: 0}")
                LabelValueRow("Forecast values", "${stats?.forecastValues ?: 0}")
                LabelValueRow("Raw provider responses", "${stats?.rawResponses ?: 0}")
                LabelValueRow("Collection runs", "${stats?.runs ?: 0}")
                LabelValueRow("Recorded errors", "${stats?.errors ?: 0}")
                LabelValueRow("Warnings", "${stats?.warnings ?: 0}")
                Spacer(Modifier.height(6.dp))
                Text(
                    "History is stored locally and retained indefinitely by default. The UI does not replace stored records with sample data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            WeatherCard {
                SectionTitle("Data export")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportProviderChip("All providers", exportProvider == "All providers") { exportProvider = "All providers" }
                    ExportProviderChip("MGM only", exportProvider == "MGM only") { exportProvider = "MGM only" }
                    ExportProviderChip("Met Office only", exportProvider == "Met Office only") { exportProvider = "Met Office only" }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            actions.onExport(ExportFormat.CSV, exportProviderCode(exportProvider), null, null)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Export CSV") }
                    OutlinedButton(
                        onClick = {
                            actions.onExport(ExportFormat.JSON, exportProviderCode(exportProvider), null, null)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Export JSON") }
                }
                if (!uiState.exportText.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Prepared: ${uiState.exportFileName ?: "export"}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "The export is prepared from stored local records. Credentials are never included.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            WeatherCard {
                SectionTitle("Data attribution")
                Text(
                    "MGM · Meteoroloji Genel Müdürlüğü. Met Office Weather DataHub · Powered by Met Office data. " +
                        "Provider terms, attribution and licence restrictions apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmClearKey) {
        AlertDialog(
            onDismissRequest = { confirmClearKey = false },
            title = { Text("Clear Met Office API key?") },
            text = { Text("Met Office collection will stop until a key is saved again. Existing stored weather history will remain unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onClearMetOfficeKey()
                    confirmClearKey = false
                }) { Text("Clear key") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKey = false }) { Text("Cancel") }
            },
        )
    }
}

private fun exportProviderCode(label: String): String? = when (label) {
    "MGM only" -> "MGM"
    "Met Office only" -> "MET_OFFICE"
    else -> null
}

/**
 * MGM station selection.
 *
 * Verified live against MGM (November 2026):
 *  - Istanbul has exactly one MGM centre (merkezId 93401, Bakirkoy), so there
 *    is exactly one observation and daily-forecast source for the province.
 *  - Three Istanbul stations publish an hourly forecast: 17060 (ISTANBUL),
 *    17061 (ISTANBUL_KUZEY) and 17064 (ISTANBUL_ANADOLU).
 *
 * The picker therefore offers the forecast-capable stations for the hourly
 * product and lists the other Istanbul stations for reference.
 */
@Composable
private fun StationPickerCard(
    uiState: WeatherUiState,
    actions: WeatherActions,
) {
    val selectedId = uiState.settings?.mgmForecastStationId
        ?: SettingsRepository.DEFAULT_MGM_FORECAST_STATION
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }

    val all = remember(uiState.stations) {
        MgmStationCatalogue.istanbul(uiState.stations)
    }
    val forecastStations = remember(all) {
        all.filter { SettingsRepository.MGM_FORECAST_STATIONS.containsKey(it.externalId.toIntOrNull()) }
    }
    val filtered = remember(all, query) {
        MgmStationCatalogue.search(uiState.stations, query)
    }

    WeatherCard {
        SectionTitle("MGM station")

        Text(
            "MGM publishes one observation centre for Istanbul, so current " +
                "conditions and the daily forecast always come from the provincial " +
                "centre. The hourly forecast is published for three Istanbul " +
                "stations - choose the one this app collects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        forecastStations.forEach { entry ->
            StationRow(
                entry = entry,
                selected = entry.externalId.toIntOrNull() == selectedId,
                onSelect = {
                    entry.externalId.toIntOrNull()?.let(actions.onSetMgmForecastStation)
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "All Istanbul stations (${all.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search stations") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))

        val visible = if (showAll) filtered else filtered.take(8)
        if (visible.isEmpty()) {
            Text(
                "No station matches \"$query\".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visible.forEach { entry ->
                StationRow(
                    entry = entry,
                    selected = entry.externalId.toIntOrNull() == selectedId,
                    enabled = SettingsRepository.MGM_FORECAST_STATIONS
                        .containsKey(entry.externalId.toIntOrNull()),
                    onSelect = {
                        entry.externalId.toIntOrNull()?.let(actions.onSetMgmForecastStation)
                    },
                )
            }
            if (filtered.size > 8) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (showAll) "Show fewer stations"
                        else "Show all ${filtered.size} matching stations"
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Stations without an MGM hourly forecast are listed for reference only; " +
                "MGM publishes no forecast product for them. Station identifiers and " +
                "metadata are retained from MGM's own station catalogue.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StationRow(
    entry: MgmStationCatalogue.Entry,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled, onClick = onSelect)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected; contentDescription = "${entry.name}, ${if (selected) "selected" else "not selected"}" }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = "${entry.subtitle} - station ${entry.externalId}" +
                    (entry.elevation?.let { ", ${it.toInt()} m" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

private enum class IntervalUnit(val label: String) {
    MINUTES("min"), HOURS("h"), DAYS("d")
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "$title, ${if (checked) "on" else "off"}" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IntervalUnitRow(
    selected: IntervalUnit,
    onSelect: (IntervalUnit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IntervalUnit.entries.forEach { unit ->
            val active = unit == selected
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(unit) }
                    .heightIn(min = 48.dp)
                    .semantics { this.selected = active; contentDescription = "${unit.label} interval" }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = unit.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected; contentDescription = "$label theme" }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ExportProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected; contentDescription = "$label export provider" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

