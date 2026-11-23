package com.istanbul.weatherintel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database. Version 1 is the initial schema; version 2 adds the free-form
 * `providerNotes` column to observations. Migrations are declared in
 * [Migrations] and covered by migration tests (see DatabaseMigrationTest).
 *
 * The exported JSON schemas live in `app/schemas/.../WeatherDatabase/` and are
 * packaged as androidTest assets so `MigrationTestHelper` can validate them.
 */
@Database(
    entities = [
        ProviderEntity::class,
        LocationEntity::class,
        StationEntity::class,
        CollectionRunEntity::class,
        ProviderCollectionResultEntity::class,
        RawResponseEntity::class,
        WeatherObservationEntity::class,
        ForecastRunEntity::class,
        ForecastValueEntity::class,
        WeatherWarningEntity::class,
        WeatherMetadataEntity::class,
        CollectionErrorEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun locationDao(): LocationDao
    abstract fun stationDao(): StationDao
    abstract fun runDao(): RunDao
    abstract fun providerResultDao(): ProviderResultDao
    abstract fun rawResponseDao(): RawResponseDao
    abstract fun observationDao(): ObservationDao
    abstract fun forecastDao(): ForecastDao
    abstract fun warningDao(): WarningDao
    abstract fun metadataDao(): MetadataDao
    abstract fun errorDao(): ErrorDao

    companion object {
        const val NAME = "istanbul_weather.db"

        @Volatile
        private var instance: WeatherDatabase? = null

        fun get(context: Context): WeatherDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    NAME,
                )
                    .addMigrations(*Migrations.ALL)
                    .build()
                    .also { instance = it }
            }
    }
}

/** JSON-backed columns use plain strings; lists are stored as JSON arrays. */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String =
        value.joinToString(separator = SEP) { it.replace(SEP, " ") }

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEP)

    companion object {
        private const val SEP = "|~|"
    }
}

/**
 * Schema migrations. Example below is the shape used when a provider field is
 * added later; keeping migrations declarative means old installs never lose data.
 */
object Migrations {
    /** v1 -> v2: add provider-specific free-form column to observations. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE weather_observations ADD COLUMN providerNotes TEXT"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
