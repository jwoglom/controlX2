package com.jwoglom.controlx2.sync.nightscout.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jwoglom.controlx2.sync.nightscout.NightscoutApiSecretHeader
import com.jwoglom.controlx2.sync.nightscout.hashNightscoutApiSecret
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Nightscout API client implementation
 *
 * This client handles authentication and HTTP communication with Nightscout.
 * It uses HttpURLConnection for simplicity - could be replaced with Retrofit later.
 */
class NightscoutClient(
    private val baseUrl: String,
    private val apiSecret: String
) : NightscoutApi {

    private val gson = Gson()
    private val apiSecretHash: String by lazy { hashNightscoutApiSecret(apiSecret) }
    private val announcementParser = NightscoutAnnouncementParser(gson)

    override suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                post("/api/v1/entries", entries)
                Timber.d("Uploaded ${entries.size} entries to Nightscout")
                Result.success(entries.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload entries to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                post("/api/v1/treatments", treatments)
                Timber.d("Uploaded ${treatments.size} treatments to Nightscout")
                Result.success(treatments.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload treatments to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                post("/api/v1/devicestatus", listOf(status))
                Timber.d("Uploaded device status to Nightscout")
                Result.success(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload device status to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun getLastEntries(count: Int): Result<List<NightscoutEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get("/api/v1/entries?count=$count")
                val type = object : TypeToken<List<NightscoutEntry>>() {}.type
                val entries: List<NightscoutEntry> = gson.fromJson(response, type)
                Result.success(entries)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get last entries from Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get("/api/v1/treatments?eventType=$eventType&count=1")
                val type = object : TypeToken<List<NightscoutTreatment>>() {}.type
                val treatments: List<NightscoutTreatment> = gson.fromJson(response, type)
                Result.success(treatments.firstOrNull())
            } catch (e: Exception) {
                Timber.e(e, "Failed to get last treatment from Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun getAnnouncements(count: Int): Result<List<NightscoutAnnouncement>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get(NightscoutAnnouncementEndpoint.latest(count))
                Result.success(announcementParser.parseAnnouncements(response))
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nightscout auth failed while fetching announcements")
                Result.failure(e)
            } catch (e: JsonSyntaxException) {
                val wrapped = IllegalArgumentException("Malformed announcements response payload", e)
                Timber.e(wrapped)
                Result.failure(wrapped)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch announcements")
                Result.failure(e)
            }
        }
    }

    override suspend fun getAnnouncementsSince(
        sinceTimestamp: Long?,
        sinceId: String?,
        count: Int
    ): Result<List<NightscoutAnnouncement>> {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = NightscoutAnnouncementEndpoint.since(
                    sinceTimestamp = sinceTimestamp,
                    sinceId = sinceId,
                    count = count
                )
                val response = get(endpoint)
                Result.success(announcementParser.parseAnnouncements(response))
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nightscout auth failed while fetching announcements since marker")
                Result.failure(e)
            } catch (e: JsonSyntaxException) {
                val wrapped = IllegalArgumentException("Malformed announcements response payload", e)
                Timber.e(wrapped)
                Result.failure(wrapped)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch announcements since marker")
                Result.failure(e)
            }
        }
    }

    private fun post(endpoint: String, body: Any): String {
        val url = URL("$baseUrl$endpoint")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty(NightscoutApiSecretHeader, apiSecretHash)
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(gson.toJson(body))
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                readResponseBody(connection.inputStream)
            } else {
                throw createHttpException(responseCode, readResponseBody(connection.errorStream))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(endpoint: String): String {
        val url = URL("$baseUrl$endpoint")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty(NightscoutApiSecretHeader, apiSecretHash)
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                readResponseBody(connection.inputStream)
            } else {
                throw createHttpException(responseCode, readResponseBody(connection.errorStream))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseBody(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        val reader = BufferedReader(InputStreamReader(stream))
        return reader.use { it.readText() }
    }

    private fun createHttpException(responseCode: Int, errorBody: String): Exception {
        return if (responseCode == 401 || responseCode == 403) {
            NightscoutAuthException("Nightscout API auth failed ($responseCode): $errorBody")
        } else {
            Exception("Nightscout API error ($responseCode): $errorBody")
        }
    }
}
