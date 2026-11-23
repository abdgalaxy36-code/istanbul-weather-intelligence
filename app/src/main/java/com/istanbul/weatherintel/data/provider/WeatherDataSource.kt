package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.model.DataType
import com.istanbul.weatherintel.data.model.ForecastRun
import com.istanbul.weatherintel.data.model.LocationRecord
import com.istanbul.weatherintel.data.model.ObservationRecord
import com.istanbul.weatherintel.data.model.ProviderCollection
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.model.RawPayload
import com.istanbul.weatherintel.data.model.StationRecord
import com.istanbul.weatherintel.data.model.WarningRecord

/**
 * Shared adapter contract. Each provider implements only the products it
 * actually supports; unsupported products are reported as unsupported rather
 * than fabricated (requirement section 4).
 */
interface WeatherDataSource {
    val providerId: ProviderId

    /** Product names this provider cannot serve, e.g. "three-hourly" for MGM. */
    val unsupportedProducts: List<String>

    suspend fun fetchLocationMetadata(): LocationRecord?

    suspend fun fetchStations(): List<StationRecord>

    /** Measured observations - only providers that actually observe. */
    suspend fun fetchCurrentObservations(): List<ObservationRecord> = emptyList()

    suspend fun fetchHourlyForecast(): List<ForecastRun> = emptyList()

    suspend fun fetchThreeHourlyForecast(): List<ForecastRun> = emptyList()

    suspend fun fetchDailyForecast(): List<ForecastRun> = emptyList()

    /** Warnings only from an authorized feed; empty when none exists. */
    suspend fun fetchWarnings(): List<WarningRecord> = emptyList()

    /** Verbatim payloads collected during the current cycle. */
    suspend fun drainRawPayloads(): List<RawPayload> = emptyList()

    /** Full collection for one provider cycle. */
    suspend fun collect(): ProviderCollection
}

/** Which data type a normalized measurement belongs to. */
fun typeOf(provider: ProviderId, product: String): DataType =
    if (product == "observation") DataType.OBSERVATION else DataType.FORECAST
