package com.istanbul.weatherintel.data.net

import com.istanbul.weatherintel.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client shared by both providers.
 *  - MGM requires browser-like Referer/Origin headers (verified live).
 *  - Met Office requires the `apikey` header from Keystore-encrypted settings.
 *  - HTTPS only; credentials are never written to logs.
 */
class HttpClient(
    private val settings: SettingsRepository,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class HttpResult(
        val code: Int,
        val body: String?,
        val contentType: String?,
    ) {
        val isSuccess: Boolean get() = code in 200..299
    }

    suspend fun getMgm(path: String): HttpResult = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else MGM_BASE + path
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://www.mgm.gov.tr/")
            .header("Origin", "https://www.mgm.gov.tr")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        execute(request)
    }

    suspend fun getMetOffice(pathAndQuery: String): HttpResult = withContext(Dispatchers.IO) {
        val apiKey = settings.metOfficeApiKey()
            ?: return@withContext HttpResult(401, """{"error":"API key not configured"}""", "application/json")
        val request = Request.Builder()
            .url(MET_OFFICE_BASE + pathAndQuery)
            .header("apikey", apiKey)
            .header("Accept", "application/json")
            .get()
            .build()
        execute(request)
    }

    private fun execute(request: Request): HttpResult = try {
        client.newCall(request).execute().use { response ->
            HttpResult(
                code = response.code,
                body = response.body?.string(),
                contentType = response.header("Content-Type"),
            )
        }
    } catch (io: IOException) {
        HttpResult(0, """{"error":"${io.javaClass.simpleName}: ${io.message?.take(120) ?: "network error"}"}""", "application/json")
    }

    companion object {
        const val MGM_BASE = "https://servis.mgm.gov.tr"
        const val MET_OFFICE_BASE = "https://data.hub.api.metoffice.gov.uk"
    }
}
