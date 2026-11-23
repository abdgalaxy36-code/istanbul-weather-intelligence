package com.istanbul.weatherintel.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.istanbul.weatherintel.WeatherApplication
import com.istanbul.weatherintel.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic collection worker. Runs on WorkManager's scheduler, which the OS may
 * delay (Doze, battery optimisation, network availability) - the UI states that
 * the next-run time is an estimate, never a promise.
 */
class WeatherCollectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WeatherApplication).container
        val settings = container.settingsRepository
        val config = settings.settings.first()

        if (!config.autoCollectEnabled) return Result.success()

        val sources = container.enabledSources(config)
        if (sources.isEmpty()) return Result.success()

        val outcome = container.collectionEngine.collect(sources, trigger = "periodic")

        // Transient failures: retry with exponential backoff. Permanent failures
        // (bad credentials, unsupported endpoint) are not retried endlessly.
        val retryable = outcome.providerOutcomes.any { !it.success && it.retryable }
        val allFailed = outcome.providerOutcomes.isNotEmpty() &&
            outcome.providerOutcomes.none { it.success }

        return when {
            retryable && allFailed -> Result.retry()
            else -> Result.success()
        }
    }
}

/**
 * Owns the single unique periodic work request and the manual "Collect Now"
 * action. Changing the interval replaces the unique work without duplicating it.
 */
class CollectionScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Applies the current interval and auto-collection toggle. */
    suspend fun applySettings() {
        val config = settings.settings.first()
        if (!config.autoCollectEnabled) {
            workManager.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val minutes = effectiveIntervalMinutes(config.collectionIntervalMinutes)
        val request = PeriodicWorkRequestBuilder<WeatherCollectionWorker>(
            minutes.toLong(), TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.MINUTES,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // replaces the schedule, no duplicates
            request,
        )
    }

    /** Manual collection: one-off work, unique so double taps do not duplicate. */
    fun collectNow() {
        val request = OneTimeWorkRequestBuilder<WeatherCollectionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            MANUAL_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_NAME)
        workManager.cancelUniqueWork(MANUAL_NAME)
    }

    /** Estimated next run (approximate - WorkManager does not guarantee exact timing). */
    suspend fun nextExpectedRunAt(): Long? {
        val info = workManager.getWorkInfosForUniqueWork(PERIODIC_NAME).get()
        val next = info.firstOrNull()?.nextScheduleTimeMillis
        return next?.takeIf { it > 0 }
    }

    companion object {
        const val PERIODIC_NAME = "istanbul-weather-periodic-collection"
        const val MANUAL_NAME = "istanbul-weather-manual-collection"

        /**
         * The effective periodic interval.
         *
         * WorkManager refuses a periodic interval shorter than 15 minutes, so a
         * smaller request is clamped rather than rejected. Kept separate from
         * the WorkManager call so the policy can be unit tested.
         */
        fun effectiveIntervalMinutes(requestedMinutes: Int): Int =
            requestedMinutes.coerceAtLeast(SettingsRepository.MIN_INTERVAL_MINUTES)
    }
}
