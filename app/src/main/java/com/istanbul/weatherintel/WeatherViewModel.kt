package com.istanbul.weatherintel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.istanbul.weatherintel.data.db.CollectionErrorEntity
import com.istanbul.weatherintel.data.db.CollectionRunEntity
import com.istanbul.weatherintel.data.db.ForecastRunEntity
import com.istanbul.weatherintel.data.db.ForecastValueEntity
import com.istanbul.weatherintel.data.db.ProviderCollectionResultEntity
import com.istanbul.weatherintel.data.db.WeatherMetadataEntity
import com.istanbul.weatherintel.data.db.WeatherObservationEntity
import com.istanbul.weatherintel.data.db.WeatherWarningEntity
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.prefs.AppSettings
import com.istanbul.weatherintel.data.prefs.ThemeMode
import com.istanbul.weatherintel.data.repository.ForecastChangeBuilder
import com.istanbul.weatherintel.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three top-level pages, in swipe order. */
enum class AppPage { HOME, COMPARE, SETTINGS }

enum class ExportFormat { CSV, JSON }

/**
 * All screen state for the app. Every displayed weather value comes from the
 * local database - the UI layer performs no live network calls.
 */
data class WeatherUiState(
    val page: AppPage = AppPage.HOME,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val headerDarkMode: Boolean? = null,
    val settings: AppSettings? = null,
    val mgmObservation: WeatherObservationEntity? = null,
    val metOfficeObservation: WeatherObservationEntity? = null,
    val mgmHourlyRun: ForecastRunEntity? = null,
    val metOfficeHourlyRun: ForecastRunEntity? = null,
    val mgmDailyRun: ForecastRunEntity? = null,
    val metOfficeDailyRun: ForecastRunEntity? = null,
    val metOfficeThreeHourlyRun: ForecastRunEntity? = null,
    val mgmHourlyValues: List<com.istanbul.weatherintel.data.db.ForecastValueEntity> = emptyList(),
    val metOfficeHourlyValues: List<com.istanbul.weatherintel.data.db.ForecastValueEntity> = emptyList(),
    val mgmDailyValues: List<com.istanbul.weatherintel.data.db.ForecastValueEntity> = emptyList(),
    val metOfficeDailyValues: List<com.istanbul.weatherintel.data.db.ForecastValueEntity> = emptyList(),
    val metOfficeThreeHourlyValues: List<com.istanbul.weatherintel.data.db.ForecastValueEntity> = emptyList(),
    val observationHistory: List<WeatherObservationEntity> = emptyList(),
    val stations: List<com.istanbul.weatherintel.data.db.StationEntity> = emptyList(),
    val rawResponses: List<com.istanbul.weatherintel.data.db.RawResponseEntity> = emptyList(),
    val mgmForecastChange: com.istanbul.weatherintel.data.repository.ForecastChangeBuilder.Change? = null,
    val metOfficeForecastChange: com.istanbul.weatherintel.data.repository.ForecastChangeBuilder.Change? = null,
    val warnings: List<WeatherWarningEntity> = emptyList(),
    val runs: List<CollectionRunEntity> = emptyList(),
    val providerResults: List<ProviderCollectionResultEntity> = emptyList(),
    val errors: List<CollectionErrorEntity> = emptyList(),
    val metadata: List<WeatherMetadataEntity> = emptyList(),
    val storageStats: WeatherRepository.StorageStats? = null,
    val collecting: Boolean = false,
    val message: String? = null,
    val exportText: String? = null,
    val exportFileName: String? = null,
    val exportFormat: ExportFormat = ExportFormat.CSV,
)

private data class Observations(val mgm: WeatherObservationEntity?, val metOffice: WeatherObservationEntity?)

private data class ForecastRuns(
    val hourlyMgm: ForecastRunEntity?,
    val hourlyMetOffice: ForecastRunEntity?,
    val dailyMgm: ForecastRunEntity?,
    val dailyMetOffice: ForecastRunEntity?,
    val threeHourlyMetOffice: ForecastRunEntity?,
)

private data class History(
    val runs: List<CollectionRunEntity>,
    val providerResults: List<ProviderCollectionResultEntity>,
    val errors: List<CollectionErrorEntity>,
)

private data class Extras(
    val warnings: List<WeatherWarningEntity>,
    val metadata: List<WeatherMetadataEntity>,
    val storage: WeatherRepository.StorageStats,
)

private data class Flags(
    val page: AppPage,
    val collecting: Boolean,
    val message: String?,
)

private data class ExportState(
    val text: String?,
    val fileName: String?,
    val format: ExportFormat,
)

class WeatherViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val page = MutableStateFlow(AppPage.HOME)
    private val collecting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val exportText = MutableStateFlow<String?>(null)
    private val exportFileName = MutableStateFlow<String?>(null)
    private val exportFormat = MutableStateFlow(ExportFormat.CSV)

    private val flags = combine(page, collecting, message) { p, c, m -> Flags(p, c, m) }
    private val export = combine(exportText, exportFileName, exportFormat) { t, n, f ->
        ExportState(t, n, f)
    }
    private val observations = combine(
        container.repository.observeLatestObservation("MGM"),
        container.repository.observeLatestObservation("MET_OFFICE"),
    ) { a, b -> Observations(a, b) }
    private val forecasts = combine(
        container.repository.observeLatestForecastRun("MGM", "hourly"),
        container.repository.observeLatestForecastRun("MET_OFFICE", "hourly"),
        container.repository.observeLatestForecastRun("MGM", "daily"),
        container.repository.observeLatestForecastRun("MET_OFFICE", "daily"),
        container.repository.observeLatestForecastRun("MET_OFFICE", "three-hourly"),
    ) { h1, h2, d1, d2, t3 ->
        ForecastRuns(h1, h2, d1, d2, t3)
    }
    private val history = combine(
        container.repository.observeRuns(200),
        container.repository.observeProviderResults(200),
        container.repository.observeErrors(50),
    ) { r, pr, e -> History(r, pr, e) }
    private val extras = combine(
        container.repository.observeWarnings(),
        container.repository.observeMetadata(),
        container.repository.observeStorageStats(),
    ) { w, m, s -> Extras(w, m, s) }

    /**
     * Forecast values for the detail screens. Each latest-run flow is chained to
     * its values with flatMapLatest, so values always match the displayed run.
     */
    private fun valuesOf(runFlow: Flow<ForecastRunEntity?>): Flow<List<ForecastValueEntity>> =
        runFlow.flatMapLatest { run ->
            if (run == null) flowOf(emptyList())
            else container.repository.observeForecastValues(run.id)
        }

    private val forecastValues = combine(
        combine(
            valuesOf(container.repository.observeLatestForecastRun("MGM", "hourly")),
            valuesOf(container.repository.observeLatestForecastRun("MET_OFFICE", "hourly")),
        ) { a, b -> a to b },
        combine(
            valuesOf(container.repository.observeLatestForecastRun("MGM", "daily")),
            valuesOf(container.repository.observeLatestForecastRun("MET_OFFICE", "daily")),
        ) { a, b -> a to b },
        valuesOf(container.repository.observeLatestForecastRun("MET_OFFICE", "three-hourly")),
    ) { hourly, daily, threeHourly ->
        ForecastValues(
            mgmHourly = hourly.first,
            metOfficeHourly = hourly.second,
            mgmDaily = daily.first,
            metOfficeDaily = daily.second,
            metOfficeThreeHourly = threeHourly,
        )
    }

    private data class ForecastValues(
        val mgmHourly: List<ForecastValueEntity>,
        val metOfficeHourly: List<ForecastValueEntity>,
        val mgmDaily: List<ForecastValueEntity>,
        val metOfficeDaily: List<ForecastValueEntity>,
        val metOfficeThreeHourly: List<ForecastValueEntity>,
    )

    /** Stored MGM observations, newest first - the historical series source. */
    private val observationHistory = container.repository.observeObservations("MGM", 500)

    /** Every stored station (MGM publishes the full national catalogue). */
    private val stations = container.repository.observeAllStations()

    /** Stored raw provider payloads, newest first - the provenance viewer. */
    private val rawResponses = container.repository.observeRawResponses(100)

    /**
     * Compares the two most recent stored versions of a forecast product.
     * Only products with at least two stored runs produce a comparison.
     */
    private fun changesFor(
        provider: String,
        product: String,
    ): Flow<ForecastChangeBuilder.Change> =
        container.repository.observeForecastRunHistory(provider, product, 2)
            .flatMapLatest { runs ->
                val current = runs.getOrNull(0)
                val previous = runs.getOrNull(1)
                if (current == null || previous == null) {
                    flowOf(
                        ForecastChangeBuilder.Change(
                            provider = provider,
                            product = product,
                            previousRun = previous,
                            currentRun = current,
                            deltas = emptyList(),
                        )
                    )
                } else {
                    combine(
                        container.repository.observeForecastValues(current.id),
                        container.repository.observeForecastValues(previous.id),
                    ) { currentValues, previousValues ->
                        ForecastChangeBuilder.build(
                            provider = provider,
                            product = product,
                            previousRun = previous,
                            currentRun = current,
                            previousValues = previousValues,
                            currentValues = currentValues,
                        )
                    }
                }
            }

    /** Forecast change analysis for both providers. */
    private val forecastChanges = combine(
        changesFor("MGM", "hourly"),
        changesFor("MET_OFFICE", "three-hourly"),
    ) { mgm, metOffice -> mgm to metOffice }

    /** Everything the UI needs that is not an observation or a forecast run. */
    private data class Support(
        val values: ForecastValues,
        val changes: Pair<ForecastChangeBuilder.Change, ForecastChangeBuilder.Change>,
        val observationHistory: List<WeatherObservationEntity>,
        val stations: List<com.istanbul.weatherintel.data.db.StationEntity>,
        val rawResponses: List<com.istanbul.weatherintel.data.db.RawResponseEntity>,
    )

    private val support = combine(
        combine(
            combine(forecastValues, forecastChanges) { v, c -> v to c },
            observationHistory,
        ) { vc, obsHistory -> Triple(vc.first, vc.second, obsHistory) },
        combine(stations, rawResponses) { s, r -> s to r },
    ) { vc, sr ->
        Support(
            values = vc.first,
            changes = vc.second,
            observationHistory = vc.third,
            stations = sr.first,
            rawResponses = sr.second,
        )
    }

    val uiState: StateFlow<WeatherUiState> = combine(
        combine(
            combine(flags, export) { f, e -> f to e },
            container.settingsRepository.settings,
        ) { head, settings -> head to settings },
        combine(observations, forecasts) { o, f -> o to f },
        combine(history, extras) { h, e -> h to e },
        support,
    ) { head, obsAndForecasts, histAndExtras, supportData ->
        val (f, e) = head.first
        val settings = head.second
        val obs = obsAndForecasts.first
        val fc = obsAndForecasts.second
        val hist = histAndExtras.first
        val ex = histAndExtras.second
        val values = supportData.values
        val changes = supportData.changes
        val obsHistory = supportData.observationHistory
        val stationList = supportData.stations
        val rawList = supportData.rawResponses
        WeatherUiState(
            page = f.page,
            themeMode = settings.themeMode,
            headerDarkMode = settings.headerDarkMode,
            settings = settings,
            mgmObservation = obs.mgm,
            metOfficeObservation = obs.metOffice,
            mgmHourlyRun = fc.hourlyMgm,
            metOfficeHourlyRun = fc.hourlyMetOffice,
            mgmDailyRun = fc.dailyMgm,
            metOfficeDailyRun = fc.dailyMetOffice,
            metOfficeThreeHourlyRun = fc.threeHourlyMetOffice,
            mgmHourlyValues = values.mgmHourly,
            metOfficeHourlyValues = values.metOfficeHourly,
            mgmDailyValues = values.mgmDaily,
            metOfficeDailyValues = values.metOfficeDaily,
            metOfficeThreeHourlyValues = values.metOfficeThreeHourly,
            observationHistory = obsHistory,
            stations = stationList,
            rawResponses = rawList,
            mgmForecastChange = changes.first,
            metOfficeForecastChange = changes.second,
            warnings = ex.warnings,
            metadata = ex.metadata,
            storageStats = ex.storage,
            runs = hist.runs,
            providerResults = hist.providerResults,
            errors = hist.errors,
            collecting = f.collecting,
            message = f.message,
            exportText = e.text,
            exportFileName = e.fileName,
            exportFormat = e.format,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    fun selectPage(page: AppPage) {
        this.page.value = page
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val current = container.settingsRepository.settings.first()
            // The switch flips the theme that is actually on screen. In SYSTEM
            // mode that is the system setting, so the sun/moon button always
            // produces a visible change.
            val isDark = current.headerDarkMode ?: when (current.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> {
                    val nightMask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    val uiMode = container.app.resources.configuration.uiMode and nightMask
                    uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
            container.settingsRepository.setHeaderDarkMode(!isDark)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun collectNow() {
        if (collecting.value) return
        viewModelScope.launch {
            collecting.value = true
            message.value = "Collecting..."
            val config = container.settingsRepository.settings.first()
            val sources = container.enabledSources(config)
            if (sources.isEmpty()) {
                collecting.value = false
                message.value = "No providers enabled or configured. Enable a provider in Settings."
                return@launch
            }
            val outcome = container.collectionEngine.collect(sources, trigger = "manual")
            collecting.value = false
            message.value = "Collection ${outcome.overallStatus}: " +
                outcome.providerOutcomes.joinToString(", ") { r ->
                    "${r.provider.display} ${if (r.success) "OK (${r.recordsSaved} records)" else "failed"}"
                }
        }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.setInterval(minutes)
            container.collectionScheduler.applySettings()
            message.value = "Collection interval updated"
        }
    }

    fun setAutoCollect(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAutoCollect(enabled)
            container.collectionScheduler.applySettings()
        }
    }

    fun setCollectOnAppOpen(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setCollectOnAppOpen(enabled) }
    }

    fun setMgmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setMgmEnabled(enabled)
            container.repository.setProviderEnabled(ProviderId.MGM, enabled)
        }
    }

    fun setMetOfficeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setMetOfficeEnabled(enabled)
            container.repository.setProviderEnabled(ProviderId.MET_OFFICE, enabled)
        }
    }

    fun setMetOfficeKey(key: String) {
        viewModelScope.launch {
            container.settingsRepository.setMetOfficeApiKey(key)
            message.value = if (key.isBlank()) "API key cleared" else "API key saved (encrypted)"
        }
    }

    fun clearMetOfficeKey() = setMetOfficeKey("")

    fun dismissSwipeHint() {
        viewModelScope.launch { container.settingsRepository.markSwipeHintShown() }
    }

    fun selectStation(stationId: Long) {
        viewModelScope.launch {
            val providerId = container.repository.providerIdOf(ProviderId.MGM)
            val station = container.database.stationDao().forProvider(providerId)
                .firstOrNull { it.id == stationId } ?: return@launch
            container.database.stationDao().clearSelection()
            container.database.stationDao().select(providerId, station.externalStationId)
            message.value = "Station selected: ${station.stationName}"
        }
    }

    /**
     * Chooses the MGM station used for the hourly forecast.
     *
     * Only the three Istanbul stations that actually publish an MGM hourly
     * forecast can be selected (verified live - see docs/MGM_NOTES.md).
     */
    fun setMgmForecastStation(stationId: Int) {
        viewModelScope.launch {
            container.settingsRepository.setMgmForecastStation(stationId)
            val label = com.istanbul.weatherintel.data.prefs.SettingsRepository
                .MGM_FORECAST_STATIONS[stationId]
            message.value = "MGM forecast station set to ${label ?: stationId}"
        }
    }

    fun exportData(format: ExportFormat, providerCode: String?, from: Long?, to: Long?) {
        viewModelScope.launch {
            val result = container.exportManager.build(format, providerCode, from, to)
            exportFormat.value = format
            exportText.value = result.content
            exportFileName.value = result.fileName
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WeatherViewModel(container) as T
            }
    }
}
