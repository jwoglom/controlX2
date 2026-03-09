package com.jwoglom.controlx2.sync.nightscout

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutClient
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.controlx2.sync.nightscout.models.createDeviceStatus
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Integration tests for NightscoutClient that start a real in-process HTTP server
 * and verify the full HTTP communication path: serialization, headers, endpoints,
 * authentication, and error handling.
 */
class NightscoutClientIntegrationTest {

    data class CapturedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    class TestServer : NanoHTTPD("127.0.0.1", 0) {
        val capturedRequests = CopyOnWriteArrayList<CapturedRequest>()
        var nextResponseCode: Response.IStatus = Response.Status.OK
        var nextResponseBody: String = "[]"

        override fun serve(session: IHTTPSession): Response {
            val bodySize = (session.headers["content-length"] ?: "0").toIntOrNull() ?: 0
            val bodyMap = HashMap<String, String>()
            if (bodySize > 0) {
                session.parseBody(bodyMap)
            }
            val body = bodyMap["postData"] ?: ""

            val queryString = session.queryParameterString
            val fullPath = session.uri + (if (queryString.isNullOrEmpty()) "" else "?$queryString")

            capturedRequests.add(
                CapturedRequest(
                    method = session.method.name,
                    path = fullPath,
                    headers = session.headers,
                    body = body
                )
            )

            return newFixedLengthResponse(nextResponseCode, "application/json", nextResponseBody)
        }

        fun reset() {
            capturedRequests.clear()
            nextResponseCode = Response.Status.OK
            nextResponseBody = "[]"
        }
    }

    private lateinit var server: TestServer
    private lateinit var client: NightscoutClient
    private val gson = Gson()

    private val testApiSecret = "test-secret-123"
    private val expectedApiSecretHash: String = MessageDigest.getInstance("SHA-1")
        .digest(testApiSecret.toByteArray())
        .joinToString("") { "%02x".format(it) }

    @Before
    fun setup() {
        server = TestServer()
        server.start()
        client = NightscoutClient(
            baseUrl = "http://127.0.0.1:${server.listeningPort}",
            apiSecret = testApiSecret
        )
    }

    @After
    fun teardown() {
        server.stop()
    }

    // --- Upload Entries ---

    @Test
    fun uploadEntries_postsToCorrectEndpoint() = runBlocking {
        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = "Flat", seqId = 1001L
            )
        )

        val result = client.uploadEntries(entries)

        assertTrue(result.isSuccess)
        assertEquals(1, server.capturedRequests.size)
        assertEquals("POST", server.capturedRequests[0].method)
        assertEquals("/api/v1/entries", server.capturedRequests[0].path)
    }

    @Test
    fun uploadEntries_sendsApiSecretAsSha1Hash() = runBlocking {
        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = null, seqId = 1L
            )
        )

        client.uploadEntries(entries)

        assertEquals(expectedApiSecretHash, server.capturedRequests[0].header("api-secret"))
    }

    @Test
    fun uploadEntries_sendsJsonContentType() = runBlocking {
        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 100, direction = null, seqId = 1L
            )
        )

        client.uploadEntries(entries)

        assertEquals("application/json", server.capturedRequests[0].header("content-type"))
    }

    @Test
    fun uploadEntries_serializesBodyCorrectly() = runBlocking {
        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = "Flat", seqId = 1001L
            ),
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 35, 0),
                sgv = 145, direction = "FortyFiveUp", seqId = 1002L
            )
        )

        client.uploadEntries(entries)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)

        assertEquals(2, body.size)
        assertEquals("sgv", body[0]["type"])
        assertEquals(120.0, body[0]["sgv"])
        assertEquals("Flat", body[0]["direction"])
        assertEquals("1001", body[0]["identifier"])
        assertEquals("ControlX2", body[0]["device"])

        assertEquals(145.0, body[1]["sgv"])
        assertEquals("FortyFiveUp", body[1]["direction"])
        assertEquals("1002", body[1]["identifier"])
    }

    @Test
    fun uploadEntries_returnsUploadedCount() = runBlocking {
        val entries = (1..3).map { i ->
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, i * 5, 0),
                sgv = 100 + i * 10, direction = null, seqId = i.toLong()
            )
        }

        val result = client.uploadEntries(entries)

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())
    }

    @Test
    fun uploadEntries_includesDateFields() = runBlocking {
        val timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0)
        val entries = listOf(
            NightscoutEntry.fromTimestamp(timestamp = timestamp, sgv = 120, direction = null, seqId = 1L)
        )

        client.uploadEntries(entries)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)

        assertNotNull(body[0]["date"])
        assertTrue((body[0]["date"] as Double).toLong() > 0)
        assertNotNull(body[0]["dateString"])
        assertTrue((body[0]["dateString"] as String).contains("2024"))
    }

    // --- Upload Treatments ---

    @Test
    fun uploadTreatments_postsToCorrectEndpoint() = runBlocking {
        val treatments = listOf(
            NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = LocalDateTime.of(2024, 3, 15, 12, 0, 0),
                seqId = 2001L, insulin = 2.5
            )
        )

        val result = client.uploadTreatments(treatments)

        assertTrue(result.isSuccess)
        assertEquals(1, server.capturedRequests.size)
        assertEquals("POST", server.capturedRequests[0].method)
        assertEquals("/api/v1/treatments", server.capturedRequests[0].path)
    }

    @Test
    fun uploadTreatments_serializesBolusCorrectly() = runBlocking {
        val treatments = listOf(
            NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = LocalDateTime.of(2024, 3, 15, 12, 0, 0),
                seqId = 2001L, insulin = 2.5, notes = "Test bolus"
            )
        )

        client.uploadTreatments(treatments)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)

        assertEquals(1, body.size)
        assertEquals("Bolus", body[0]["eventType"])
        assertEquals(2.5, body[0]["insulin"])
        assertEquals("2001", body[0]["pumpId"])
        assertEquals("ControlX2", body[0]["enteredBy"])
        assertEquals("ControlX2", body[0]["device"])
        assertEquals("Test bolus", body[0]["notes"])
        assertNotNull(body[0]["created_at"])
        assertNotNull(body[0]["timestamp"])
    }

    @Test
    fun uploadTreatments_serializesTempBasalCorrectly() = runBlocking {
        val treatments = listOf(
            NightscoutTreatment.fromTimestamp(
                eventType = "Temp Basal",
                timestamp = LocalDateTime.of(2024, 3, 15, 14, 0, 0),
                seqId = 2002L, rate = 0.8, absolute = 0.8, duration = 30
            )
        )

        client.uploadTreatments(treatments)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)

        assertEquals("Temp Basal", body[0]["eventType"])
        assertEquals(0.8, body[0]["rate"])
        assertEquals(0.8, body[0]["absolute"])
        assertEquals(30.0, body[0]["duration"])
    }

    @Test
    fun uploadTreatments_serializesSuspensionCorrectly() = runBlocking {
        val treatments = listOf(
            NightscoutTreatment.fromTimestamp(
                eventType = "Temp Basal",
                timestamp = LocalDateTime.of(2024, 3, 15, 15, 0, 0),
                seqId = 2003L, rate = 0.0, absolute = 0.0, reason = "Pumping suspended"
            )
        )

        client.uploadTreatments(treatments)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)

        assertEquals(0.0, body[0]["rate"])
        assertEquals(0.0, body[0]["absolute"])
        assertEquals("Pumping suspended", body[0]["reason"])
    }

    @Test
    fun uploadTreatments_handlesBatchUpload() = runBlocking {
        val treatments = (1..5).map { i ->
            NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = LocalDateTime.of(2024, 3, 15, 12, i * 5, 0),
                seqId = 2000L + i, insulin = i * 0.5
            )
        }

        val result = client.uploadTreatments(treatments)

        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull())

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)
        assertEquals(5, body.size)
    }

    // --- Upload Device Status ---

    @Test
    fun uploadDeviceStatus_postsToCorrectEndpoint() = runBlocking {
        val status = createDeviceStatus(
            timestamp = LocalDateTime.of(2024, 3, 15, 10, 0, 0),
            batteryPercent = 85, reservoirUnits = 150.0,
            iob = 3.5, pumpStatus = "normal", uploaderBattery = 72
        )

        val result = client.uploadDeviceStatus(status)

        assertTrue(result.isSuccess)
        assertEquals(1, server.capturedRequests.size)
        assertEquals("POST", server.capturedRequests[0].method)
        assertEquals("/api/v1/devicestatus", server.capturedRequests[0].path)
    }

    @Test
    fun uploadDeviceStatus_serializesCorrectly() = runBlocking {
        val status = createDeviceStatus(
            timestamp = LocalDateTime.of(2024, 3, 15, 10, 0, 0),
            batteryPercent = 85, reservoirUnits = 150.0,
            iob = 3.5, pumpStatus = "normal", uploaderBattery = 72
        )

        client.uploadDeviceStatus(status)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)

        assertEquals(1, body.size)
        assertEquals("ControlX2", body[0]["device"])
        assertNotNull(body[0]["created_at"])
        assertEquals(72.0, body[0]["uploaderBattery"])

        @Suppress("UNCHECKED_CAST")
        val pump = body[0]["pump"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val battery = pump["battery"] as Map<String, Any>
        assertEquals(85.0, battery["percent"])
        assertEquals(150.0, pump["reservoir"])

        @Suppress("UNCHECKED_CAST")
        val iob = pump["iob"] as Map<String, Any>
        assertEquals(3.5, iob["iob"])
    }

    // --- GET Endpoints ---

    @Test
    fun getLastEntries_sendsCorrectRequest() = runBlocking {
        server.nextResponseBody = """[
            {"type":"sgv","sgv":120,"date":1710500000000,"dateString":"2024-03-15","device":"ControlX2"}
        ]"""

        val result = client.getLastEntries(5)

        assertTrue(result.isSuccess)
        assertEquals(1, server.capturedRequests.size)
        assertEquals("GET", server.capturedRequests[0].method)
        assertEquals("/api/v1/entries?count=5", server.capturedRequests[0].path)
        assertEquals(expectedApiSecretHash, server.capturedRequests[0].header("api-secret"))
    }

    @Test
    fun getLastEntries_parsesResponse() = runBlocking {
        server.nextResponseBody = """[
            {"type":"sgv","sgv":120,"date":1710500000000,"dateString":"2024-03-15","device":"ControlX2"},
            {"type":"sgv","sgv":130,"date":1710500300000,"dateString":"2024-03-15","device":"ControlX2"}
        ]"""

        val result = client.getLastEntries(2)

        assertTrue(result.isSuccess)
        val entries = result.getOrNull()!!
        assertEquals(2, entries.size)
        assertEquals(120, entries[0].sgv)
        assertEquals(130, entries[1].sgv)
    }

    @Test
    fun getLastTreatment_sendsCorrectRequest() = runBlocking {
        server.nextResponseBody = """[
            {"eventType":"Bolus","created_at":"2024-03-15","timestamp":1710500000000,"insulin":2.5}
        ]"""

        val result = client.getLastTreatment("Bolus")

        assertTrue(result.isSuccess)
        assertEquals(1, server.capturedRequests.size)
        assertEquals("GET", server.capturedRequests[0].method)
        assertEquals("/api/v1/treatments?eventType=Bolus&count=1", server.capturedRequests[0].path)
    }

    @Test
    fun getLastTreatment_returnsNullWhenEmpty() = runBlocking {
        server.nextResponseBody = "[]"

        val result = client.getLastTreatment("Bolus")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    // --- Error Handling ---

    @Test
    fun uploadEntries_returnsFailureOnServerError() = runBlocking {
        server.nextResponseCode = NanoHTTPD.Response.Status.INTERNAL_ERROR
        server.nextResponseBody = """{"status":500,"message":"Internal Server Error"}"""

        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = null, seqId = 1L
            )
        )

        val result = client.uploadEntries(entries)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun uploadEntries_returnsFailureOnUnauthorized() = runBlocking {
        server.nextResponseCode = NanoHTTPD.Response.Status.UNAUTHORIZED
        server.nextResponseBody = """{"status":401,"message":"Unauthorized"}"""

        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = null, seqId = 1L
            )
        )

        val result = client.uploadEntries(entries)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun uploadTreatments_returnsFailureOnServerError() = runBlocking {
        server.nextResponseCode = NanoHTTPD.Response.Status.INTERNAL_ERROR

        val treatments = listOf(
            NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = LocalDateTime.of(2024, 3, 15, 12, 0, 0),
                seqId = 1L, insulin = 1.0
            )
        )

        val result = client.uploadTreatments(treatments)

        assertTrue(result.isFailure)
    }

    @Test
    fun uploadEntries_returnsFailureOnConnectionRefused() = runBlocking {
        val deadClient = NightscoutClient(
            baseUrl = "http://127.0.0.1:1",
            apiSecret = testApiSecret
        )

        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = null, seqId = 1L
            )
        )

        val result = deadClient.uploadEntries(entries)

        assertTrue(result.isFailure)
    }

    // --- Authentication ---

    @Test
    fun differentApiSecrets_produceDifferentHeaders() = runBlocking {
        val secret2 = "different-secret"

        // Use a separate server for the second client to avoid ordering ambiguity
        val server2 = TestServer()
        server2.start()
        try {
            val client2 = NightscoutClient(
                baseUrl = "http://127.0.0.1:${server2.listeningPort}",
                apiSecret = secret2
            )

            val entries = listOf(
                NightscoutEntry.fromTimestamp(
                    timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                    sgv = 120, direction = null, seqId = 1L
                )
            )

            client.uploadEntries(entries)
            client2.uploadEntries(entries)

            assertEquals(1, server.capturedRequests.size)
            assertEquals(1, server2.capturedRequests.size)

            val hash1 = server.capturedRequests[0].header("api-secret")
            val hash2 = server2.capturedRequests[0].header("api-secret")

            assertNotNull(hash1)
            assertNotNull(hash2)
            assertNotEquals("Different secrets should produce different hashes", hash1, hash2)
            assertEquals(40, hash1!!.length)
            assertEquals(40, hash2!!.length)
        } finally {
            server2.stop()
        }
    }

    @Test
    fun apiSecretHeader_isConsistentAcrossRequestTypes() = runBlocking {
        val entry = NightscoutEntry.fromTimestamp(
            timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
            sgv = 120, direction = null, seqId = 1L
        )
        val treatment = NightscoutTreatment.fromTimestamp(
            eventType = "Bolus",
            timestamp = LocalDateTime.of(2024, 3, 15, 12, 0, 0),
            seqId = 2L, insulin = 1.0
        )
        val deviceStatus = createDeviceStatus(
            timestamp = LocalDateTime.of(2024, 3, 15, 14, 0, 0),
            batteryPercent = 80
        )

        client.uploadEntries(listOf(entry))
        client.uploadTreatments(listOf(treatment))
        client.uploadDeviceStatus(deviceStatus)

        assertEquals(3, server.capturedRequests.size)
        assertTrue(server.capturedRequests.all { it.header("api-secret") == expectedApiSecretHash })
    }

    // --- Sequential uploads ---

    @Test
    fun multipleUploads_eachHitsServer() = runBlocking {
        val batch1 = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 0, 0),
                sgv = 100, direction = null, seqId = 1L
            )
        )
        val batch2 = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 5, 0),
                sgv = 110, direction = null, seqId = 2L
            )
        )

        client.uploadEntries(batch1)
        client.uploadEntries(batch2)

        assertEquals(2, server.capturedRequests.size)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body1: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)
        val body2: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[1].body, type)

        assertEquals(100.0, body1[0]["sgv"])
        assertEquals(110.0, body2[0]["sgv"])
    }

    // --- Deduplication identifiers ---

    @Test
    fun entryIdentifier_matchesSeqId() = runBlocking {
        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 0, 0),
                sgv = 100, direction = null, seqId = 98765L
            )
        )

        client.uploadEntries(entries)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)
        assertEquals("98765", body[0]["identifier"])
    }

    @Test
    fun treatmentPumpId_matchesSeqId() = runBlocking {
        val treatments = listOf(
            NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = LocalDateTime.of(2024, 3, 15, 12, 0, 0),
                seqId = 54321L, insulin = 1.0
            )
        )

        client.uploadTreatments(treatments)

        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val body: List<Map<String, Any>> = gson.fromJson(server.capturedRequests[0].body, type)
        assertEquals("54321", body[0]["pumpId"])
    }

    // --- URL edge case ---

    @Test
    fun trailingSlashInBaseUrl_doesNotDoubleSlash() = runBlocking {
        val clientWithSlash = NightscoutClient(
            baseUrl = "http://127.0.0.1:${server.listeningPort}/",
            apiSecret = testApiSecret
        )

        val entries = listOf(
            NightscoutEntry.fromTimestamp(
                timestamp = LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                sgv = 120, direction = null, seqId = 1L
            )
        )

        val result = clientWithSlash.uploadEntries(entries)

        assertTrue(result.isSuccess)
    }
}
