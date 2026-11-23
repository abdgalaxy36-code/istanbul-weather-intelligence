package com.istanbul.weatherintel.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.istanbul.weatherintel.WeatherApplication
import com.istanbul.weatherintel.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Scheduler tests.
 *
 * These run against the real WorkManager instance the app uses, so they verify
 * the actual enqueue behaviour: exactly one unique periodic work, the requested
 * interval is applied, re-applying does not duplicate the schedule, and turning
 * auto-collection off cancels it.
 */
@RunWith(AndroidJUnit4::class)
class CollectionSchedulerTest {

    private lateinit var app: WeatherApplication
    private lateinit var settings: SettingsRepository
    private lateinit var scheduler: CollectionScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() = runBlocking {
        app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as WeatherApplication
        settings = app.container.settingsRepository
        scheduler = CollectionScheduler(app, settings)
        workManager = WorkManager.getInstance(app)

        // Start from a known state.
        settings.setAutoCollect(true)
        settings.setInterval(360)
    }

    @After
    fun tearDown() = runBlocking {
        settings.setAutoCollect(true)
        settings.setInterval(360)
        scheduler.applySettings()
    }

    private suspend fun periodicInfos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(CollectionScheduler.PERIODIC_NAME).get()

    /** The repeat interval of a periodic work, in millis (null when not periodic). */
    private fun WorkInfo.repeatIntervalMillis(): Long? = periodicityInfo?.repeatIntervalMillis

    private fun WorkInfo.isPeriodicWork(): Boolean = periodicityInfo != null

    @Test
    fun exactlyOnePeriodicWorkIsEnqueued() = runBlocking {
        scheduler.applySettings()
        val infos = periodicInfos()
        assertEquals("the schedule must never be duplicated", 1, infos.size)
        assertTrue("the work must be periodic", infos[0].isPeriodicWork())
    }

    @Test
    fun reapplyingTheSettingsReplacesTheScheduleInsteadOfDuplicatingIt() = runBlocking {
        scheduler.applySettings()
        settings.setInterval(60)
        scheduler.applySettings()
        settings.setInterval(120)
        scheduler.applySettings()

        val infos = periodicInfos()
        assertEquals(1, infos.size)
        assertEquals(
            TimeUnit.MINUTES.toMillis(120),
            infos[0].repeatIntervalMillis(),
        )
    }

    @Test
    fun theRequestedIntervalIsApplied() = runBlocking {
        settings.setInterval(45)
        scheduler.applySettings()
        val infos = periodicInfos()
        assertEquals(1, infos.size)
        assertEquals(TimeUnit.MINUTES.toMillis(45), infos[0].repeatIntervalMillis())
    }

    @Test
    fun anIntervalBelowTheWorkManagerMinimumIsClamped() = runBlocking {
        // SettingsRepository itself clamps to 15 minutes; the scheduler policy
        // clamps again, so the enqueued work is always valid.
        settings.setInterval(1)
        scheduler.applySettings()
        val infos = periodicInfos()
        assertEquals(1, infos.size)
        assertEquals(
            TimeUnit.MINUTES.toMillis(SettingsRepository.MIN_INTERVAL_MINUTES.toLong()),
            infos[0].repeatIntervalMillis(),
        )
    }

    @Test
    fun turningAutoCollectOffCancelsThePeriodicWork() = runBlocking {
        scheduler.applySettings()
        assertEquals(1, periodicInfos().size)

        settings.setAutoCollect(false)
        scheduler.applySettings()

        val infos = periodicInfos()
        assertTrue(
            "the periodic work must be cancelled when auto-collection is off",
            infos.isEmpty() || infos.all { it.state == WorkInfo.State.CANCELLED },
        )
    }

    @Test
    fun manualCollectionUsesItsOwnUniqueWorkName() = runBlocking {
        scheduler.collectNow()
        val manual = workManager
            .getWorkInfosForUniqueWork(CollectionScheduler.MANUAL_NAME)
            .get()
        assertEquals("double taps must not create duplicate work", 1, manual.size)
        assertTrue(!manual[0].isPeriodicWork())
        scheduler.cancelAll()
    }

    @Test
    fun theNextRunTimeIsExposedForTheUi() = runBlocking {
        scheduler.applySettings()
        val next = scheduler.nextExpectedRunAt()
        // WorkManager may not schedule immediately (constraints / Doze), so the
        // contract is only that the value is either absent or in the future.
        if (next != null) {
            assertTrue("the next run must be in the future", next > System.currentTimeMillis())
        }
    }
}
