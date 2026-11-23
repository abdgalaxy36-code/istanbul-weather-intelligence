package com.istanbul.weatherintel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity): Long

    @Query("SELECT * FROM providers WHERE providerCode = :code LIMIT 1")
    suspend fun byCode(code: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE providerCode = :code LIMIT 1")
    fun observeByCode(code: String): Flow<ProviderEntity?>

    @Query("SELECT * FROM providers ORDER BY id")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("UPDATE providers SET enabled = :enabled WHERE providerCode = :code")
    suspend fun setEnabled(code: String, enabled: Boolean)
}

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(location: LocationEntity): Long

    @Query("SELECT * FROM locations WHERE providerId = :providerId AND sourceLocationId = :sourceId LIMIT 1")
    suspend fun bySource(providerId: Long, sourceId: String): LocationEntity?

    @Query("SELECT * FROM locations WHERE providerId = :providerId LIMIT 1")
    fun observeForProvider(providerId: Long): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE providerId = :providerId LIMIT 1")
    suspend fun firstForProvider(providerId: Long): LocationEntity?
}

@Dao
interface StationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<StationEntity>)

    @Query("SELECT * FROM stations WHERE providerId = :providerId ORDER BY stationName")
    fun observeForProvider(providerId: Long): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations ORDER BY stationName")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE providerId = :providerId ORDER BY stationName")
    suspend fun forProvider(providerId: Long): List<StationEntity>

    @Query("SELECT COUNT(*) FROM stations WHERE providerId = :providerId")
    fun observeCount(providerId: Long): Flow<Int>

    @Query("UPDATE stations SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE stations SET isSelected = 1 WHERE providerId = :providerId AND externalStationId = :externalId")
    suspend fun select(providerId: Long, externalId: String)
}

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: CollectionRunEntity): Long

    @Query(
        """UPDATE collection_runs
           SET finishedAt = :finishedAt, overallStatus = :status,
               successCount = :successCount, failureCount = :failureCount
           WHERE id = :id"""
    )
    suspend fun finish(
        id: Long,
        finishedAt: Long,
        status: String,
        successCount: Int,
        failureCount: Int,
    )

    @Query("SELECT * FROM collection_runs ORDER BY startedAt DESC LIMIT :limit")
    fun observeRuns(limit: Int): Flow<List<CollectionRunEntity>>

    @Query("SELECT * FROM collection_runs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun runs(limit: Int): List<CollectionRunEntity>

    @Query("SELECT * FROM collection_runs WHERE id = :id")
    suspend fun byId(id: Long): CollectionRunEntity?

    @Query("DELETE FROM collection_runs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM collection_runs")
    fun observeCount(): Flow<Int>
}

@Dao
interface ProviderResultDao {
    @Insert
    suspend fun insert(result: ProviderCollectionResultEntity): Long

    @Query("SELECT * FROM provider_collection_results WHERE collectionRunId = :runId ORDER BY startedAt")
    fun observeForRun(runId: Long): Flow<List<ProviderCollectionResultEntity>>

    @Query("SELECT * FROM provider_collection_results WHERE collectionRunId = :runId ORDER BY startedAt")
    suspend fun forRun(runId: Long): List<ProviderCollectionResultEntity>

    @Query("SELECT * FROM provider_collection_results ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProviderCollectionResultEntity>>

    @Query(
        """SELECT r.* FROM provider_collection_results r
           JOIN providers p ON p.id = r.providerId
           WHERE p.providerCode = :code ORDER BY r.startedAt DESC LIMIT 1"""
    )
    fun observeLastForProvider(code: String): Flow<ProviderCollectionResultEntity?>

    @Query(
        """SELECT r.* FROM provider_collection_results r
           JOIN providers p ON p.id = r.providerId
           WHERE p.providerCode = :code ORDER BY r.startedAt DESC LIMIT 1"""
    )
    suspend fun lastForProvider(code: String): ProviderCollectionResultEntity?
}

@Dao
interface RawResponseDao {
    @Insert
    suspend fun insert(response: RawResponseEntity): Long

    @Query("SELECT * FROM raw_responses WHERE collectionRunId = :runId ORDER BY retrievedAt")
    suspend fun forRun(runId: Long): List<RawResponseEntity>

    @Query(
        """SELECT r.* FROM raw_responses r
           JOIN providers p ON p.id = r.providerId
           WHERE p.providerCode = :code ORDER BY r.retrievedAt DESC LIMIT :limit"""
    )
    fun observeRecentForProvider(code: String, limit: Int): Flow<List<RawResponseEntity>>

    @Query("SELECT COUNT(*) FROM raw_responses")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM raw_responses ORDER BY retrievedAt DESC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<RawResponseEntity>>
}

@Dao
interface ObservationDao {
    @Insert
    suspend fun insert(observation: WeatherObservationEntity): Long

    @Query(
        """SELECT o.* FROM weather_observations o
           JOIN providers p ON p.id = o.providerId
           WHERE p.providerCode = :code
           ORDER BY o.observedAt DESC, o.collectedAt DESC LIMIT 1"""
    )
    fun observeLatest(code: String): Flow<WeatherObservationEntity?>

    @Query(
        """SELECT o.* FROM weather_observations o
           JOIN providers p ON p.id = o.providerId
           WHERE p.providerCode = :code
           ORDER BY o.observedAt DESC, o.collectedAt DESC LIMIT 1"""
    )
    suspend fun latest(code: String): WeatherObservationEntity?

    @Query(
        """SELECT o.* FROM weather_observations o
           JOIN providers p ON p.id = o.providerId
           WHERE p.providerCode = :code
             AND o.observedAt BETWEEN :from AND :to
           ORDER BY o.observedAt DESC"""
    )
    fun observeRange(code: String, from: Long, to: Long): Flow<List<WeatherObservationEntity>>

    @Query(
        """SELECT o.* FROM weather_observations o
           JOIN providers p ON p.id = o.providerId
           WHERE p.providerCode = :code
           ORDER BY o.observedAt DESC, o.collectedAt DESC LIMIT :limit"""
    )
    fun observeHistory(code: String, limit: Int): Flow<List<WeatherObservationEntity>>

    @Query("SELECT COUNT(*) FROM weather_observations")
    fun observeCount(): Flow<Int>
}

@Dao
interface ForecastDao {
    @Insert
    suspend fun insert(run: ForecastRunEntity): Long

    @Insert
    suspend fun insertValues(values: List<ForecastValueEntity>)

    @Transaction
    @Query(
        """SELECT f.* FROM forecast_runs f
           JOIN providers p ON p.id = f.providerId
           WHERE p.providerCode = :code AND f.productName = :product
           ORDER BY f.collectedAt DESC LIMIT 1"""
    )
    suspend fun latestRun(code: String, product: String): ForecastRunEntity?

    @Query(
        """SELECT f.* FROM forecast_runs f
           JOIN providers p ON p.id = f.providerId
           WHERE p.providerCode = :code AND f.productName = :product
           ORDER BY f.collectedAt DESC LIMIT 1"""
    )
    fun observeLatestRun(code: String, product: String): Flow<ForecastRunEntity?>

    @Query(
        """SELECT v.* FROM forecast_values v
           JOIN forecast_runs f ON f.id = v.forecastRunId
           JOIN providers p ON p.id = f.providerId
           WHERE f.id = :forecastRunId
           ORDER BY v.validAt ASC, v.category ASC, v.parameterCode ASC"""
    )
    fun observeValues(forecastRunId: Long): Flow<List<ForecastValueEntity>>

    @Query(
        """SELECT v.* FROM forecast_values v
           JOIN forecast_runs f ON f.id = v.forecastRunId
           JOIN providers p ON p.id = f.providerId
           WHERE f.id = :forecastRunId
           ORDER BY v.validAt ASC, v.category ASC, v.parameterCode ASC"""
    )
    suspend fun values(forecastRunId: Long): List<ForecastValueEntity>

    @Query(
        """SELECT f.* FROM forecast_runs f
           JOIN providers p ON p.id = f.providerId
           WHERE p.providerCode = :code AND f.productName = :product
           ORDER BY f.collectedAt DESC LIMIT :limit"""
    )
    fun observeRunHistory(code: String, product: String, limit: Int): Flow<List<ForecastRunEntity>>

    @Query(
        """SELECT f.* FROM forecast_runs f
           JOIN providers p ON p.id = f.providerId
           WHERE p.providerCode = :code AND f.productName = :product
           ORDER BY f.collectedAt DESC LIMIT :limit"""
    )
    suspend fun runHistory(code: String, product: String, limit: Int): List<ForecastRunEntity>

    @Query("SELECT COUNT(*) FROM forecast_values")
    fun observeValueCount(): Flow<Int>
}

@Dao
interface WarningDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(warnings: List<WeatherWarningEntity>)

    @Query(
        """SELECT w.* FROM weather_warnings w
           JOIN providers p ON p.id = w.providerId
           ORDER BY IFNULL(w.issuedAt, 0) DESC"""
    )
    fun observeAll(): Flow<List<WeatherWarningEntity>>

    @Query("SELECT COUNT(*) FROM weather_warnings")
    fun observeCount(): Flow<Int>
}

@Dao
interface MetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: WeatherMetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metadata: List<WeatherMetadataEntity>)

    @Query(
        """SELECT m.* FROM weather_metadata m
           JOIN providers p ON p.id = m.providerId
           ORDER BY m.category ASC, m.parameterName ASC"""
    )
    fun observeAll(): Flow<List<WeatherMetadataEntity>>

    @Query(
        """SELECT m.* FROM weather_metadata m
           JOIN providers p ON p.id = m.providerId
           WHERE p.providerCode = :code ORDER BY m.category ASC, m.parameterName ASC"""
    )
    suspend fun forProvider(code: String): List<WeatherMetadataEntity>
}

@Dao
interface ErrorDao {
    @Insert
    suspend fun insert(error: CollectionErrorEntity): Long

    @Query(
        """SELECT e.* FROM collection_errors e
           JOIN providers p ON p.id = e.providerId
           ORDER BY e.occurredAt DESC LIMIT :limit"""
    )
    fun observeRecent(limit: Int): Flow<List<CollectionErrorEntity>>

    @Query("SELECT COUNT(*) FROM collection_errors")
    fun observeCount(): Flow<Int>
}
