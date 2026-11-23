package com.istanbul.weatherintel.data.collection

import android.util.Log
import com.istanbul.weatherintel.data.model.CollectionOutcome
import com.istanbul.weatherintel.data.model.ProviderId
import com.istanbul.weatherintel.data.model.ProviderOutcome
import com.istanbul.weatherintel.data.prefs.SettingsRepository
import com.istanbul.weatherintel.data.provider.ProviderHttpException
import com.istanbul.weatherintel.data.provider.ProviderParseException
import com.istanbul.weatherintel.data.provider.WeatherDataSource
import com.istanbul.weatherintel.data.repository.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/**
 * Orchestrates collection across providers.
 *
 * Each provider runs independently inside its own coroutine, so an MGM failure
 * can never prevent Met Office data from being saved (requirement section 4).
 */
class CollectionEngine(
    private val repository: WeatherRepository,
    private val settings: SettingsRepository,
    private val appVersion: String,
) {
    /**
     * @param sources providers to collect from, already filtered by the user toggle.
     * @param trigger periodic | manual | app_open
     */
    suspend fun collect(
        sources: List<WeatherDataSource>,
        trigger: String,
    ): CollectionOutcome {
        val config = settings.settings.first()
        repository.ensureProviders()

        val runId = repository.startRun(
            trigger = trigger,
            intervalMinutes = config.collectionIntervalMinutes,
            requestedProviders = sources.size,
            appVersion = appVersion,
        )

        val outcomes = coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) { collectOne(runId, source) }
            }.awaitAll()
        }

        repository.finishRun(
            runId = runId,
            successCount = outcomes.count { it.success },
            failureCount = outcomes.count { !it.success },
        )
        settings.recordCollection(
            at = System.currentTimeMillis(),
            status = when {
                outcomes.all { it.success } -> "OK"
                outcomes.any { it.success } -> "Partial"
                else -> "Failed"
            },
        )
        return CollectionOutcome(runId, outcomes)
    }

    private suspend fun collectOne(
        runId: Long,
        source: WeatherDataSource,
    ): ProviderOutcome {
        val startedAt = System.currentTimeMillis()
        return try {
            val collection = source.collect()
            val received = collection.recordCount
            val saved = repository.saveProviderCollection(runId, collection, received)
            val finishedAt = System.currentTimeMillis()
            repository.recordProviderResult(
                runId = runId,
                provider = source.providerId,
                success = true,
                startedAt = startedAt,
                finishedAt = finishedAt,
                recordsReceived = received,
                recordsSaved = saved,
                errorCode = null,
                errorMessage = null,
                endpoints = collection.rawPayloads.map { it.endpoint },
            )
            ProviderOutcome(
                provider = source.providerId,
                success = true,
                startedAt = startedAt,
                finishedAt = finishedAt,
                recordsReceived = received,
                recordsSaved = saved,
                errorCode = null,
                errorMessage = null,
                endpoints = collection.rawPayloads.map { it.endpoint },
                retryable = false,
            )
        } catch (t: Throwable) {
            val finishedAt = System.currentTimeMillis()
            val code = classify(t)
            val message = t.message?.take(400) ?: t.javaClass.simpleName
            // Log message only - never request headers or credentials.
            Log.w("CollectionEngine", "${source.providerId} collection failed: $code ${t.javaClass.simpleName}")
            repository.recordProviderResult(
                runId = runId,
                provider = source.providerId,
                success = false,
                startedAt = startedAt,
                finishedAt = finishedAt,
                recordsReceived = 0,
                recordsSaved = 0,
                errorCode = code,
                errorMessage = message,
                endpoints = emptyList(),
            )
            ProviderOutcome(
                provider = source.providerId,
                success = false,
                startedAt = startedAt,
                finishedAt = finishedAt,
                recordsReceived = 0,
                recordsSaved = 0,
                errorCode = code,
                errorMessage = message,
                endpoints = emptyList(),
                retryable = code in RETRYABLE,
            )
        }
    }

    private fun classify(t: Throwable): String = when (t) {
        is ProviderHttpException -> when (t.status) {
            401, 403 -> "auth"
            429 -> "http_429"
            in 500..599 -> "http_5xx"
            else -> "http_${t.status}"
        }
        is ProviderParseException -> "parse"
        is java.net.SocketTimeoutException -> "timeout"
        is java.io.IOException -> "network"
        else -> "unknown"
    }

    companion object {
        private val RETRYABLE = setOf("network", "timeout", "http_429", "http_5xx")
    }
}
