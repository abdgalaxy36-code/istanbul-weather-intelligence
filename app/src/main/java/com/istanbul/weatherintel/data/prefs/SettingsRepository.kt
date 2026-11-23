package com.istanbul.weatherintel.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.istanbul.weatherintel.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val collectionIntervalMinutes: Int,
    val autoCollectEnabled: Boolean,
    val collectOnAppOpen: Boolean,
    val mgmEnabled: Boolean,
    val metOfficeEnabled: Boolean,
    val themeMode: ThemeMode,
    val headerDarkMode: Boolean?,       // null = follow themeMode; true/false = explicit sun/moon toggle
    val metOfficeConfigured: Boolean,
    val metOfficeLatitude: Double,
    val metOfficeLongitude: Double,
    val mgmProvince: String,
    val mgmForecastStationId: Int,        // MGM hourly forecast station (see MGM_NOTES.md)
    val firstUseSwipeHintShown: Boolean,
    val lastSuccessfulCollectionAt: Long,
    val lastCollectionStatus: String,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("app_settings")

/**
 * All user-configurable behaviour. Credentials are stored encrypted via [CryptoManager];
 * nothing here is ever written to logs.
 */
class SettingsRepository(
    private val context: Context,
    private val crypto: CryptoManager,
) {
    private object Keys {
        val INTERVAL = intPreferencesKey("collection_interval_minutes")
        val AUTO = booleanPreferencesKey("auto_collect_enabled")
        val ON_OPEN = booleanPreferencesKey("collect_on_app_open")
        val MGM_ON = booleanPreferencesKey("mgm_enabled")
        val MO_ON = booleanPreferencesKey("metoffice_enabled")
        val THEME = stringPreferencesKey("theme_mode")
        val HEADER_DARK = booleanPreferencesKey("header_dark_mode")
        val MO_KEY_ENC = stringPreferencesKey("metoffice_api_key_enc")
        val MO_LAT = doublePreferencesKey("metoffice_lat")
        val MO_LON = doublePreferencesKey("metoffice_lon")
        val MGM_PROVINCE = stringPreferencesKey("mgm_province")
        val MGM_STATION = intPreferencesKey("mgm_forecast_station_id")
        val SWIPE_HINT = booleanPreferencesKey("first_use_swipe_hint_shown")
        val LAST_OK = longPreferencesKey("last_successful_collection_at")
        val LAST_STATUS = stringPreferencesKey("last_collection_status")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            collectionIntervalMinutes = p[Keys.INTERVAL] ?: DEFAULT_INTERVAL_MINUTES,
            autoCollectEnabled = p[Keys.AUTO] ?: true,
            collectOnAppOpen = p[Keys.ON_OPEN] ?: false,
            mgmEnabled = p[Keys.MGM_ON] ?: true,
            metOfficeEnabled = p[Keys.MO_ON] ?: true,
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            headerDarkMode = p[Keys.HEADER_DARK],
            metOfficeConfigured = !p[Keys.MO_KEY_ENC].isNullOrBlank(),
            metOfficeLatitude = p[Keys.MO_LAT] ?: DEFAULT_LAT,
            metOfficeLongitude = p[Keys.MO_LON] ?: DEFAULT_LON,
            mgmProvince = p[Keys.MGM_PROVINCE] ?: DEFAULT_PROVINCE,
            mgmForecastStationId = p[Keys.MGM_STATION] ?: DEFAULT_MGM_FORECAST_STATION,
            firstUseSwipeHintShown = p[Keys.SWIPE_HINT] ?: false,
            lastSuccessfulCollectionAt = p[Keys.LAST_OK] ?: 0L,
            lastCollectionStatus = p[Keys.LAST_STATUS] ?: "Never collected",
        )
    }

    suspend fun setInterval(minutes: Int) {
        // WorkManager minimum periodic interval is 15 minutes.
        val clamped = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        context.dataStore.edit { it[Keys.INTERVAL] = clamped }
    }

    suspend fun setAutoCollect(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO] = enabled }
    suspend fun setCollectOnAppOpen(enabled: Boolean) = context.dataStore.edit { it[Keys.ON_OPEN] = enabled }
    suspend fun setMgmEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.MGM_ON] = enabled }
    suspend fun setMetOfficeEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.MO_ON] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit {
        it[Keys.THEME] = mode.name
        it.remove(Keys.HEADER_DARK) // explicit choice wins again once user re-picks a mode
    }

    /** Header sun/moon toggle: persists an explicit light/dark override. */
    suspend fun setHeaderDarkMode(dark: Boolean) = context.dataStore.edit { it[Keys.HEADER_DARK] = dark }

    suspend fun setMetOfficeApiKey(plain: String) = context.dataStore.edit {
        if (plain.isBlank()) it.remove(Keys.MO_KEY_ENC)
        else it[Keys.MO_KEY_ENC] = crypto.encrypt(plain.trim())
    }

    /** Plaintext only at the moment a request is signed; never logged. */
    suspend fun metOfficeApiKey(): String? {
        val enc = context.dataStore.data.first()[Keys.MO_KEY_ENC] ?: return null
        return crypto.decrypt(enc)
    }

    suspend fun setMetOfficePoint(lat: Double, lon: Double) = context.dataStore.edit {
        it[Keys.MO_LAT] = lat
        it[Keys.MO_LON] = lon
    }

    suspend fun setMgmProvince(province: String) = context.dataStore.edit {
        it[Keys.MGM_PROVINCE] = province.ifBlank { DEFAULT_PROVINCE }.lowercase()
    }

    /**
     * The MGM station used for the hourly forecast.
     *
     * Verified live: 17060 (ISTANBUL / Bakirkoy), 17061 (ISTANBUL_KUZEY /
     * Sariyer) and 17064 (ISTANBUL_ANADOLU / Kartal) publish an Istanbul
     * hourly forecast; the other Istanbul stations do not.
     */
    suspend fun setMgmForecastStation(stationId: Int) = context.dataStore.edit {
        it[Keys.MGM_STATION] = stationId
    }

    suspend fun markSwipeHintShown() = context.dataStore.edit { it[Keys.SWIPE_HINT] = true }

    suspend fun recordCollection(at: Long, status: String) = context.dataStore.edit {
        if (status == "OK") it[Keys.LAST_OK] = at
        it[Keys.LAST_STATUS] = status
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 360 // 6 hours
        const val MIN_INTERVAL_MINUTES = 15      // WorkManager hard minimum
        const val MAX_INTERVAL_MINUTES = 60 * 24 * 7
        const val DEFAULT_LAT = 41.0082
        const val DEFAULT_LON = 28.9784
        const val DEFAULT_PROVINCE = "istanbul"

        /** MGM's Istanbul hourly forecast station (Bakirkoy / Ataturk Airport). */
        const val DEFAULT_MGM_FORECAST_STATION = 17060

        /**
         * The Istanbul stations that publish an MGM hourly forecast.
         * Verified live against `/web/tahminler/saatlik?istno=` on 2026-11-22.
         */
        val MGM_FORECAST_STATIONS = mapOf(
            17060 to "ISTANBUL - Istanbul (Bakirkoy)",
            17061 to "ISTANBUL_KUZEY - Istanbul North (Sariyer)",
            17064 to "ISTANBUL_ANADOLU - Istanbul East (Kartal)",
        )
    }
}
