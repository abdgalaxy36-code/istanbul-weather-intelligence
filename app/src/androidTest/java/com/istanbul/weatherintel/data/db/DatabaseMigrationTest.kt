package com.istanbul.weatherintel.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test (requirement section 6: "Use Room migrations and migration
 * tests"). It creates the exported v1 schema, applies the declared migration
 * and checks that the new column exists and existing data survived.
 *
 * The schemas under `app/schemas/.../WeatherDatabase/` are packaged as
 * androidTest assets, so this test validates against the real exported schema
 * rather than a hand-written approximation.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val databaseName = "migration-test.db"

    private val helper by lazy {
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            WeatherDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
    }

    @Test
    fun migratesFromVersion1To2() {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.getDatabasePath(databaseName).delete()

        // Create the v1 schema and insert a row.
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO collection_runs (id, startedAt, finishedAt, configuredIntervalMinutes, " +
                    "overallStatus, appVersion, requestedProviderCount, successCount, failureCount, trigger) " +
                    "VALUES (1, 1000, 2000, 360, 'success', '1.0.0', 2, 2, 0, 'manual')"
            )
            execSQL(
                "INSERT INTO weather_observations (id, providerId, locationId, stationId, sourceRecordId, " +
                    "observedAt, collectedAt, normalizedJson, providerSpecificJson, dataType, collectionRunId) " +
                    "VALUES (1, 1, NULL, NULL, '17060', 3000, 4000, '[]', '{}', 'observation', 1)"
            )
            close()
        }

        // Apply the migration and validate against the exported v2 schema.
        helper.runMigrationsAndValidate(databaseName, 2, true, Migrations.MIGRATION_1_2).apply {
            // The new column exists and is readable.
            query("SELECT providerNotes FROM weather_observations").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                // The pre-existing row survived the migration with a null value.
                assertEquals(null, cursor.getString(0))
            }
            // Existing data is intact.
            query("SELECT overallStatus FROM collection_runs WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("success", cursor.getString(0))
            }
            query("SELECT sourceRecordId FROM weather_observations WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("17060", cursor.getString(0))
            }
            close()
        }

        context.getDatabasePath(databaseName).delete()
    }
}
