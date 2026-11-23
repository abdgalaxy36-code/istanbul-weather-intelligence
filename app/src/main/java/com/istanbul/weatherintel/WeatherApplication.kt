package com.istanbul.weatherintel

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.istanbul.weatherintel.data.collection.CollectionEngine
import com.istanbul.weatherintel.data.db.WeatherDatabase
import com.istanbul.weatherintel.data.net.HttpClient
import com.istanbul.weatherintel.data.prefs.SettingsRepository
import com.istanbul.weatherintel.data.provider.MetOfficeWeatherDataSource
import com.istanbul.weatherintel.data.provider.MgmWeatherDataSource
import com.istanbul.weatherintel.data.provider.WeatherDataSource
import com.istanbul.weatherintel.data.repository.WeatherRepository
import com.istanbul.weatherintel.security.CryptoManager
import com.istanbul.weatherintel.worker.CollectionScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manual dependency injection container. Simple, explicit and testable - the app
 * has a single object graph, so a DI framework would add build weight without
 * adding clarity here.
 */
class AppContainer(val app: Application) {

    val cryptoManager = CryptoManager()
    val settingsRepository = SettingsRepository(app, cryptoManager)
    val database: WeatherDatabase by lazy { WeatherDatabase.get(app) }
    val httpClient: HttpClient by lazy { HttpClient(settingsRepository) }
    val repository: WeatherRepository by lazy { WeatherRepository(database) }

    val collectionEngine: CollectionEngine by lazy {
        CollectionEngine(
            repository = repository,
            settings = settingsRepository,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    val collectionScheduler: CollectionScheduler by lazy {
        CollectionScheduler(app, settingsRepository)
    }

    val exportManager: com.istanbul.weatherintel.data.export.ExportManager by lazy {
        com.istanbul.weatherintel.data.export.ExportManager(database)
    }

    /** Provider adapters, honouring the user's enable/disable toggles. */
    fun enabledSources(config: com.istanbul.weatherintel.data.prefs.AppSettings): List<WeatherDataSource> {
        val sources = mutableListOf<WeatherDataSource>()
        if (config.mgmEnabled) {
            sources += MgmWeatherDataSource(
                http = httpClient,
                province = config.mgmProvince,
                forecastStationId = config.mgmForecastStationId,
            )
        }
        if (config.metOfficeEnabled && config.metOfficeConfigured) {
            sources += MetOfficeWeatherDataSource(
                http = httpClient,
                latitude = config.metOfficeLatitude,
                longitude = config.metOfficeLongitude,
            )
        }
        return sources
    }
}

class WeatherApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Ensure provider rows exist and restore the persisted schedule after
        // process death / reboot (WorkManager restores pending work itself).
        runBlocking {
            container.repository.ensureProviders()
            container.collectionScheduler.applySettings()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

/** Convenience accessor used by the worker and UI. */
val Context.container: AppContainer
    get() = (applicationContext as WeatherApplication).container
