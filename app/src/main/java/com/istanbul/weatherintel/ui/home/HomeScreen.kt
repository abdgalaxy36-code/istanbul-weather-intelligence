package com.istanbul.weatherintel.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istanbul.weatherintel.WeatherUiState
import com.istanbul.weatherintel.data.db.ForecastValueEntity
import com.istanbul.weatherintel.data.provider.MetOfficeWeatherCodes
import com.istanbul.weatherintel.data.solar.SunTimes
import com.istanbul.weatherintel.ui.IstanbulTimes
import com.istanbul.weatherintel.ui.WeatherActions
import com.istanbul.weatherintel.ui.WeatherDataReader
import com.istanbul.weatherintel.ui.components.LabelValueRow
import com.istanbul.weatherintel.ui.components.SectionTitle
import com.istanbul.weatherintel.ui.components.StatusPill
import com.istanbul.weatherintel.ui.components.WeatherCard
import com.istanbul.weatherintel.ui.components.WeatherIcons
import com.istanbul.weatherintel.ui.format
import com.istanbul.weatherintel.ui.screens.AppSection
import com.istanbul.weatherintel.ui.theme.LocalIsDarkTheme
import com.istanbul.weatherintel.ui.theme.SkyPalette
import java.time.LocalDate

/**
 * Home page: weather only, in the approved visual style.
 *
 * The final specification removes the MGM-versus-Met-Office comparison card
 * from Home, so this page shows one selected source at a time: a sky-gradient
 * hero, a four-metric strip, hourly / three-hourly / daily previews, sunrise
 * and sunset, warnings and quick access to every detail screen.
 *
 * No value on this page is ever invented - each one is read from the stored
 * record, and a missing measurement stays missing.
 */
@Composable
fun HomeScreen(
    uiState: WeatherUiState,
    actions: WeatherActions,
) {
    val mgm = WeatherDataReader.readObservation(uiState.mgmObservation)
    val metOffice = WeatherDataReader.readObservation(uiState.metOfficeObservation)

    // A source selector is a source indicator, not a comparison view.
    var sourceLabel by remember { mutableStateOf("MGM") }
    val active = when (sourceLabel) {
        "Met Office" -> metOffice
        else -> mgm
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroSection(
                view = active,
                mgm = mgm,
                metOffice = metOffice,
                selectedSource = sourceLabel,
                onSelectSource = { sourceLabel = it },
            )
        }

        item {
            MetricsStrip(view = active)
        }

        item {
            HourlyForecastCard(uiState, actions, sourceLabel)
        }

        if (sourceLabel == "Met Office" && uiState.metOfficeThreeHourlyRun != null) {
            item { ThreeHourlyForecastCard(uiState, actions) }
        }

        item {
            DailyForecastCard(uiState, actions, sourceLabel)
        }

        item {
            SunTimesCard()
        }

        item {
            WarningsCard(uiState, actions)
        }

        item {
            MoreScreensCard(actions)
        }

        item {
            ProvenanceCard(uiState)
        }
    }
}

/* ------------------------------------------------------------------ hero */

/**
 * The sky-gradient hero.
 *
 * It continues the header gradient so the two read as one surface. The big
 * glyph, temperature and condition come from the selected source only.
 */
@Composable
private fun HeroSection(
    view: WeatherDataReader.ObservedView?,
    mgm: WeatherDataReader.ObservedView?,
    metOffice: WeatherDataReader.ObservedView?,
    selectedSource: String,
    onSelectSource: (String) -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    val sky = if (dark) SkyPalette.dark else SkyPalette.light

    val temperature = view?.value("sicaklik")
        ?: view?.value("screenTemperature")
        ?: view?.value("dayMaxScreenTemperature")
    val feelsLike = view?.value("hissedilenSicaklik")
        ?: view?.value("feelsLikeTemperature")
        ?: view?.value("feelsLikeTemp")
    val condition = conditionText(view)
    val rain = precipitationText(view)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(sky[1], sky[2])))
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = WeatherIcons.condition(condition),
                contentDescription = null,
                tint = WeatherIcons.tint(condition),
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = temperature?.let { "${format(it)}°C" } ?: "--",
                    fontSize = 56.sp,
                    lineHeight = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = condition ?: "No current condition stored",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                append(feelsLike?.let { "Feels like ${format(it)}°C" } ?: "Feels like not provided")
                append("  |  Rain: ")
                append(rain ?: "not provided")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        SourceIndicator(
            mgm = mgm,
            metOffice = metOffice,
            selected = selectedSource,
            onSelect = onSelectSource,
        )
    }
}

/** The condition description, whichever way the provider spelled it. */
private fun conditionText(view: WeatherDataReader.ObservedView?): String? {
    if (view == null) return null
    view.text("hadiseAdi")?.takeIf { it.isNotBlank() }?.let { return it }
    view.text("condition")?.takeIf { it.isNotBlank() }?.let { return it }
    val code = view.text("significantWeatherCode") ?: view.text("hadiseKodu")
    if (!code.isNullOrBlank()) {
        MetOfficeWeatherCodes.label(code)?.let { return it }
        if (code.any { it.isLetter() }) return code
    }
    return null
}

/** The current precipitation amount, formatted for the hero line. */
private fun precipitationText(view: WeatherDataReader.ObservedView?): String? {
    if (view == null) return null
    val amount = view.value("yagis00Now")
        ?: view.value("yagis10Dk")
        ?: view.value("totalPrecipAmount")
    if (amount != null) return "${format(amount)} mm"
    val probability = view.value("probOfPrecipitation")
        ?: view.value("screenRelativeHumidity")
    if (probability != null) return "${format(probability)}%"
    return null
}

/** Source pills - an indicator of the active source, never a comparison. */
@Composable
private fun SourceIndicator(
    mgm: WeatherDataReader.ObservedView?,
    metOffice: WeatherDataReader.ObservedView?,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourcePill("MGM", mgm != null, selected == "MGM") { onSelect("MGM") }
        SourcePill("Met Office", metOffice != null, selected == "Met Office") { onSelect("Met Office") }
    }
}

@Composable
private fun SourcePill(
    label: String,
    available: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (available) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/* --------------------------------------------------------- metric strip */

/**
 * The four-metric strip from the approved reference.
 *
 * Every entry is read from the selected source; a measurement the source did
 * not supply is shown as unavailable rather than as zero.
 */
@Composable
private fun MetricsStrip(view: WeatherDataReader.ObservedView?) {
    WeatherCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Air,
                label = "Wind",
                value = windText(view),
            )
            MetricDivider()
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Opacity,
                label = "Humidity",
                value = humidityText(view),
            )
            MetricDivider()
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Compress,
                label = "Pressure",
                value = pressureText(view),
            )
            MetricDivider()
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Visibility,
                label = "Visibility",
                value = visibilityText(view),
            )
        }
    }
}

@Composable
private fun MetricCell(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: "—",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(54.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    )
}

private fun windText(view: WeatherDataReader.ObservedView?): String? {
    if (view == null) return null
    val kmh = view.value("ruzgarHiz")
    if (kmh != null) {
        val direction = view.value("ruzgarYon") ?: view.value("ruzgarYonu")
        return if (direction != null) {
            "${format(kmh)} km/h ${compass(direction)}"
        } else {
            "${format(kmh)} km/h"
        }
    }
    val ms = view.value("windSpeed10m")
    if (ms != null) {
        val direction = view.value("windDirectionFrom10m")
        return if (direction != null) {
            "${format(ms * 3.6)} km/h ${compass(direction)}"
        } else {
            "${format(ms * 3.6)} km/h"
        }
    }
    return null
}

private fun humidityText(view: WeatherDataReader.ObservedView?): String? {
    val value = view?.value("nem") ?: view?.value("screenRelativeHumidity") ?: return null
    return "${format(value)}%"
}

private fun pressureText(view: WeatherDataReader.ObservedView?): String? {
    if (view == null) return null
    val hpa = view.value("aktuelBasinc") ?: view.value("denizeIndirgenmisBasinc")
    if (hpa != null) return "${format(hpa)} hPa"
    val pa = view.value("mslp")
    if (pa != null) return "${format(pa / 100.0)} hPa"
    return null
}

private fun visibilityText(view: WeatherDataReader.ObservedView?): String? {
    val metres = view?.value("gorus") ?: view?.value("visibility") ?: return null
    return if (metres >= 10_000) "> ${format(metres / 1000.0)} km" else "${format(metres)} m"
}

/** Compass point for a bearing in degrees. */
private fun compass(degrees: Double): String {
    val points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val index = (((degrees % 360) + 360) % 360 / 22.5).toInt().coerceIn(0, 15)
    return points[index]
}

/* --------------------------------------------------------- forecast rows */

/** One horizontal forecast column: label, glyph, value and a helper line. */
@Composable
private fun ForecastColumn(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    primary: String,
    secondary: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(66.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = secondary ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Section title with a leading glyph and a trailing "See all" action. */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .heightIn(min = 40.dp)
                .padding(horizontal = 4.dp, vertical = 10.dp),
        )
    }
}

/**
 * A horizontal forecast preview.
 *
 * Only the first few columns are shown in the preview; every period remains
 * available in the corresponding detail screen, which the "See all" action
 * opens.
 */
@Composable
private fun ForecastStrip(
    periods: List<ForecastColumnData>,
    emptyText: String,
) {
    if (periods.isEmpty()) {
        EmptyText(emptyText)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        periods.forEach { period ->
            ForecastColumn(
                label = period.label,
                icon = period.icon,
                iconTint = period.iconTint,
                primary = period.primary,
                secondary = period.secondary,
            )
        }
    }
}

/** Prepared column data for one forecast preview. */
private data class ForecastColumnData(
    val label: String,
    val icon: ImageVector,
    val iconTint: Color,
    val primary: String,
    val secondary: String?,
)

@Composable
private fun HourlyForecastCard(
    uiState: WeatherUiState,
    actions: WeatherActions,
    selectedSource: String,
) {
    val values = if (selectedSource == "MGM") uiState.mgmHourlyValues
    else uiState.metOfficeHourlyValues
    val run = if (selectedSource == "MGM") uiState.mgmHourlyRun
    else uiState.metOfficeHourlyRun

    WeatherCard {
        SectionHeader(
            title = "Hourly Forecast",
            icon = Icons.Filled.Schedule,
            actionLabel = "See all →",
            onAction = { actions.onOpenSection(AppSection.HOURLY) },
        )
        if (run == null || values.isEmpty()) {
            EmptyText("No hourly forecast has been stored for $selectedSource yet.")
            return@WeatherCard
        }
        val periods = remember(values) { hourlyColumns(values) }
        ForecastStrip(periods, "No hourly forecast stored yet.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$selectedSource · issued ${IstanbulTimes.format(run.issuedAt)} · " +
                "${run.rowCount} periods in the full forecast",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThreeHourlyForecastCard(uiState: WeatherUiState, actions: WeatherActions) {
    val values = uiState.metOfficeThreeHourlyValues
    val run = uiState.metOfficeThreeHourlyRun

    WeatherCard {
        SectionHeader(
            title = "Three-hourly Forecast",
            icon = Icons.Filled.Schedule,
            actionLabel = "See all →",
            onAction = { actions.onOpenSection(AppSection.THREE_HOURLY) },
        )
        if (run == null || values.isEmpty()) {
            EmptyText("No three-hourly forecast stored yet.")
            return@WeatherCard
        }
        val periods = remember(values) { hourlyColumns(values) }
        ForecastStrip(periods, "No three-hourly forecast stored yet.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Met Office · issued ${IstanbulTimes.format(run.issuedAt)} · " +
                "${run.rowCount} periods in the full forecast",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyForecastCard(
    uiState: WeatherUiState,
    actions: WeatherActions,
    selectedSource: String,
) {
    val values = if (selectedSource == "MGM") uiState.mgmDailyValues
    else uiState.metOfficeDailyValues
    val run = if (selectedSource == "MGM") uiState.mgmDailyRun
    else uiState.metOfficeDailyRun

    WeatherCard {
        SectionHeader(
            title = "Daily Forecast",
            icon = Icons.Filled.CalendarMonth,
            actionLabel = "See all →",
            onAction = { actions.onOpenSection(AppSection.DAILY) },
        )
        if (run == null || values.isEmpty()) {
            EmptyText("No daily forecast has been stored for $selectedSource yet.")
            return@WeatherCard
        }
        val periods = remember(values) { dailyColumns(values) }
        ForecastStrip(periods, "No daily forecast stored yet.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$selectedSource · issued ${IstanbulTimes.format(run.issuedAt)} · " +
                "${run.rowCount} days in the full forecast",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Builds hourly columns from whichever provider codes are present. */
private fun hourlyColumns(values: List<ForecastValueEntity>): List<ForecastColumnData> {
    val grouped = values.groupBy { it.validAt }.toSortedMap()
    return grouped.entries.map { (validAt, rows) ->
        val temperature = rows.pick("screenTemperature") ?: rows.pick("sicaklik")
        val condition = rows.conditionText()
        val probability = rows.pick("probOfPrecipitation")?.numericValue
            ?: rows.pick("totalPrecipAmount")?.numericValue
        ForecastColumnData(
            label = IstanbulTimes.time(validAt),
            icon = WeatherIcons.condition(condition),
            iconTint = WeatherIcons.tint(condition),
            primary = temperature?.numericValue?.let { "${format(it)}°C" } ?: "—",
            secondary = probability?.let { "${format(it)}%" },
        )
    }
}

/** Builds daily columns with the day name, high and low. */
private fun dailyColumns(values: List<ForecastValueEntity>): List<ForecastColumnData> {
    val grouped = values.groupBy { it.validAt }.toSortedMap()
    return grouped.entries.map { (validAt, rows) ->
        val high = rows.pick("dayMaxScreenTemperature") ?: rows.pickPrefix("enYuksekGun")
        val low = rows.pick("nightMinScreenTemperature") ?: rows.pickPrefix("enDusukGun")
        val condition = rows.conditionText()
        val probability = rows.pick("dayProbabilityOfPrecipitation")?.numericValue
        ForecastColumnData(
            label = IstanbulTimes.day(validAt).substringBefore(' '),
            icon = WeatherIcons.condition(condition),
            iconTint = WeatherIcons.tint(condition),
            primary = high?.numericValue?.let { "${format(it)}°" } ?: "—",
            secondary = buildString {
                low?.numericValue?.let { append("${format(it)}°") }
                if (probability != null) {
                    if (isNotEmpty()) append("  ")
                    append("${format(probability)}%")
                }
            }.ifBlank { null },
        )
    }
}

/** Finds a value by its exact parameter code. */
private fun List<ForecastValueEntity>.pick(code: String): ForecastValueEntity? =
    firstOrNull { it.parameterCode.equals(code, ignoreCase = true) }

/**
 * Finds a value by code prefix.
 *
 * MGM embeds the day index in the code (for example `enYuksekGun0`), so the
 * daily values are matched by their stable prefix.
 */
private fun List<ForecastValueEntity>.pickPrefix(prefix: String): ForecastValueEntity? =
    firstOrNull { it.parameterCode.startsWith(prefix, ignoreCase = true) }

/** The condition description stored with a forecast period. */
private fun List<ForecastValueEntity>.conditionText(): String? {
    pick("hadiseAdi")?.textValue?.takeIf { it.isNotBlank() }?.let { return it }
    val code = pick("significantWeatherCode")?.textValue
        ?: pick("daySignificantWeatherCode")?.textValue
        ?: pickPrefix("hadiseGun")?.textValue
    if (!code.isNullOrBlank()) {
        MetOfficeWeatherCodes.label(code)?.let { return it }
        if (code.any { it.isLetter() }) return code
    }
    return null
}

/* ------------------------------------------------------------- sun times */

/**
 * Sunrise and sunset for Istanbul.
 *
 * Neither provider publishes these times, so they are computed locally and
 * labelled as computed - they are never presented as provider observations.
 */
@Composable
private fun SunTimesCard() {
    val today = remember { LocalDate.now(IstanbulTimes.zone) }
    val sunrise = remember(today) { SunTimes.sunriseUtc(today, ISTANBUL_LAT, ISTANBUL_LON) }
    val sunset = remember(today) { SunTimes.sunsetUtc(today, ISTANBUL_LAT, ISTANBUL_LON) }
    val noon = remember(today) { SunTimes.solarNoonUtc(today, ISTANBUL_LAT, ISTANBUL_LON) }
    val daylight = remember(today) { SunTimes.daylightMinutes(today, ISTANBUL_LAT, ISTANBUL_LON) }

    WeatherCard {
        SectionHeader(
            title = "Sunrise & Sunset",
            icon = Icons.Filled.WbSunny,
            actionLabel = "",
            onAction = {},
        )
        if (sunrise == null || sunset == null) {
            EmptyText("The sun does not cross the horizon on this date.")
            return@WeatherCard
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            SunCell("Sunrise", IstanbulTimes.time(sunrise), Modifier.weight(1f))
            SunCell("Solar noon", IstanbulTimes.time(noon), Modifier.weight(1f))
            SunCell("Sunset", IstanbulTimes.time(sunset), Modifier.weight(1f))
            SunCell(
                "Daylight",
                daylight?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Computed locally for Istanbul ($ISTANBUL_LAT, $ISTANBUL_LON) with the " +
                "NOAA sunrise equation. Neither MGM nor the Met Office publishes sunrise " +
                "or sunset, so these are not provider observations.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val ISTANBUL_LAT = 41.0082
private const val ISTANBUL_LON = 28.9784

@Composable
private fun SunCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* --------------------------------------------------------------- warnings */

@Composable
private fun WarningsCard(uiState: WeatherUiState, actions: WeatherActions) {
    WeatherCard {
        SectionHeader(
            title = "Weather Warnings",
            icon = Icons.Filled.WaterDrop,
            actionLabel = "See all →",
            onAction = { actions.onOpenSection(AppSection.WARNINGS) },
        )
        if (uiState.warnings.isEmpty()) {
            EmptyText(
                "No warnings received. MGM publishes no accessible warning feed and the " +
                    "Met Office Global Spot product does not include warnings for Istanbul."
            )
            return@WeatherCard
        }
        uiState.warnings.forEach { warning ->
            LabelValueRow(warning.title ?: "Warning", warning.severity ?: "")
            LabelValueRow("Issued", IstanbulTimes.format(warning.issuedAt))
            LabelValueRow(
                "Valid",
                "${IstanbulTimes.format(warning.validFrom)} - ${IstanbulTimes.format(warning.validTo)}",
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/* ---------------------------------------------------------- quick access */

/** Entry points to the complete screens (A-L). */
@Composable
private fun MoreScreensCard(actions: WeatherActions) {
    WeatherCard {
        SectionTitle("All screens")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenChip("Dashboard") { actions.onOpenSection(AppSection.DASHBOARD) }
            ScreenChip("Current") { actions.onOpenSection(AppSection.CURRENT) }
            ScreenChip("Hourly") { actions.onOpenSection(AppSection.HOURLY) }
            ScreenChip("3-hourly") { actions.onOpenSection(AppSection.THREE_HOURLY) }
            ScreenChip("Daily") { actions.onOpenSection(AppSection.DAILY) }
            ScreenChip("History") { actions.onOpenSection(AppSection.HISTORICAL) }
            ScreenChip("Changes") { actions.onOpenSection(AppSection.FORECAST_CHANGE) }
            ScreenChip("Warnings") { actions.onOpenSection(AppSection.WARNINGS) }
            ScreenChip("Runs") { actions.onOpenSection(AppSection.COLLECTION_HISTORY) }
            ScreenChip("Export") { actions.onOpenSection(AppSection.DATA_EXPORT) }
        }
    }
}

@Composable
private fun ScreenChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/* -------------------------------------------------------------- provenance */

@Composable
private fun ProvenanceCard(uiState: WeatherUiState) {
    WeatherCard {
        SectionTitle("Data sources")
        Text(
            text = "MGM - Meteoroloji Genel Müdürlüğü (Turkish State Met Service). " +
                "Observed conditions from the Istanbul centre.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Met Office Weather DataHub - Global Spot. Forecast data for the Istanbul " +
                "coordinates (not a local station observation).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Powered by Met Office data",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.mgmObservation?.let {
            Spacer(Modifier.height(6.dp))
            LabelValueRow("MGM last update", IstanbulTimes.format(it.collectedAt))
        }
    }
}

@Composable
internal fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
