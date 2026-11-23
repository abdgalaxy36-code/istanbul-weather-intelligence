package com.istanbul.weatherintel.worker

import com.istanbul.weatherintel.data.prefs.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scheduling policy tests.
 *
 * WorkManager refuses a periodic interval shorter than 15 minutes, so the
 * requested interval is clamped rather than rejected. The default is 6 hours.
 */
class CollectionSchedulerPolicyTest {

    @Test
    fun `an interval below the WorkManager minimum is clamped to 15 minutes`() {
        assertEquals(
            SettingsRepository.MIN_INTERVAL_MINUTES,
            CollectionScheduler.effectiveIntervalMinutes(1),
        )
        assertEquals(
            SettingsRepository.MIN_INTERVAL_MINUTES,
            CollectionScheduler.effectiveIntervalMinutes(14),
        )
        assertEquals(
            SettingsRepository.MIN_INTERVAL_MINUTES,
            CollectionScheduler.effectiveIntervalMinutes(0),
        )
        assertEquals(
            SettingsRepository.MIN_INTERVAL_MINUTES,
            CollectionScheduler.effectiveIntervalMinutes(-30),
        )
    }

    @Test
    fun `an interval at or above the minimum is kept as requested`() {
        assertEquals(15, CollectionScheduler.effectiveIntervalMinutes(15))
        assertEquals(30, CollectionScheduler.effectiveIntervalMinutes(30))
        assertEquals(360, CollectionScheduler.effectiveIntervalMinutes(360))
        assertEquals(1440, CollectionScheduler.effectiveIntervalMinutes(1440))
    }

    @Test
    fun `the WorkManager minimum is 15 minutes`() {
        assertEquals(15, SettingsRepository.MIN_INTERVAL_MINUTES)
    }

    @Test
    fun `the default interval is 6 hours`() {
        assertEquals(360, SettingsRepository.DEFAULT_INTERVAL_MINUTES)
    }

    @Test
    fun `the unique work names are stable so the schedule is never duplicated`() {
        assertEquals(
            "istanbul-weather-periodic-collection",
            CollectionScheduler.PERIODIC_NAME,
        )
        assertEquals(
            "istanbul-weather-manual-collection",
            CollectionScheduler.MANUAL_NAME,
        )
        assertTrue(CollectionScheduler.PERIODIC_NAME != CollectionScheduler.MANUAL_NAME)
    }
}
