package com.jwoglom.controlx2

import android.content.Context
import android.util.Base64
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogTypeStats
import com.jwoglom.controlx2.util.AppVersionInfo
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class HttpDebugApiService(private val context: Context, private val port: Int = 18282) {

    private var server: DebugApiServer? = null
    private val prefs = Prefs(context)

    private val historyLogDb by lazy { HistoryLogDatabase.getDatabase(context) }
    private val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }

    // Streaming clients for /api/pump/messages
    private val pumpMessageStreamClients = CopyOnWriteArrayList<PrintWriter>()

    private data class HistoryLogStreamClient(val writer: PrintWriter, val format: String, val pumpSid: Int?)

    // Streaming clients for /api/historylog/stream
    private val historyLogStreamClients = CopyOnWriteArrayList<HistoryLogStreamClient>()

    // Streaming clients for /api/comm/messages/stream
    private val messagingStreamClients = CopyOnWriteArrayList<PrintWriter>()

    // Pending pump message requests waiting for responses
    private val pendingPumpRequests = ConcurrentHashMap<Pair<Characteristic, Byte>, CompletableFuture<Message>>()

    // Callbacks to get current pump data from CommService
    var getCurrentPumpDataCallback: (() -> String)? = null

    // Callback to get current pump SID from CommService
    var getCurrentPumpSidCallback: (() -> Int?)? = null

    // Callback to send pump messages
    var sendPumpMessagesCallback: ((ByteArray) -> Unit)? = null

    // Callback to send messaging messages
    var sendMessagingCallback: ((String, ByteArray) -> Unit)? = null

    // Callback when new history log entries are inserted
    var onHistoryLogInsertedCallback: ((HistoryLogItem) -> Unit)? = null

    fun start() {
        if (!prefs.httpDebugApiEnabled()) {
            Timber.i("HttpDebugApiService not starting - disabled in preferences")
            return
        }

        val username = prefs.httpDebugApiUsername()
        val password = prefs.httpDebugApiPassword()

        if (password.isEmpty()) {
            Timber.w("HttpDebugApiService not starting - password not configured")
            return
        }

        try {
            server = DebugApiServer(port, username, password)
            server?.start()
            Timber.i("HttpDebugApiService started on port $port")
        } catch (e: IOException) {
            Timber.e(e, "Failed to start HttpDebugApiService")
        }
    }

    fun stop() {
        server?.stop()
        server = null
        pumpMessageStreamClients.clear()
        historyLogStreamClients.clear()
        messagingStreamClients.clear()
        Timber.i("HttpDebugApiService stopped")
    }

    fun isRunning(): Boolean = server?.isAlive == true

    /**
     * Called when a pump message is received (from CommService.PumpCommHandler.Pump.onReceiveMessage)
     */
    fun onPumpMessageReceived(message: Message) {
        Timber.d("onPumpMessageReceived: $message")
        // parse+decode to force as a single line
        val jsonString = JSONObject(message.jsonToString()).toString()
        Timber.d("Broadcasting pump message to ${pumpMessageStreamClients.size} clients")
        broadcastToPumpMessageClients(jsonString)

        // Check if this message is a response to a pending request
        val key = Pair(message.characteristic, message.opCode())
        pendingPumpRequests[key]?.complete(message)
    }

    /**
     * Called when a message is received (from CommService.handleMessageReceived)
     */
    fun onMessagingReceived(path: String, data: ByteArray, sourceNodeId: String) {
        val dataString = String(data)
        val dataHex = data.joinToString("") { "%02x".format(it) }
        val jsonObject = JSONObject()
        jsonObject.put("path", path)
        jsonObject.put("sourceNodeId", sourceNodeId)
        jsonObject.put("dataString", dataString)
        jsonObject.put("dataHex", dataHex)
        broadcastToMessagingClients(jsonObject.toString())
    }

    fun onHistoryLogInserted(item: HistoryLogItem) {
        broadcastToHistoryLogClients(item)
    }

    private fun broadcastToPumpMessageClients(message: String) {
        val toRemove = mutableListOf<PrintWriter>()
        pumpMessageStreamClients.forEach { writer ->
            try {
                writer.println(message)
                writer.flush()
                if (writer.checkError()) {
                    Timber.w("Error writing to pump message stream client (checkError)")
                    toRemove.add(writer)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error writing to pump message stream client (exception)")
                toRemove.add(writer)
            }
        }
        pumpMessageStreamClients.removeAll(toRemove)
    }

    private fun broadcastToMessagingClients(message: String) {
        val toRemove = mutableListOf<PrintWriter>()
        messagingStreamClients.forEach { writer ->
            try {
                writer.println(message)
                writer.flush()
                if (writer.checkError()) {
                    toRemove.add(writer)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error writing to messaging stream client")
                toRemove.add(writer)
            }
        }
        messagingStreamClients.removeAll(toRemove)
    }

    private fun broadcastToHistoryLogClients(item: HistoryLogItem) {
        val toRemove = mutableListOf<PrintWriter>()
        historyLogStreamClients.forEach { client ->
            if (client.pumpSid != null && client.pumpSid != item.pumpSid) {
                return@forEach
            }
            try {
                val message = historyLogItemToJson(item, client.format).toString()
                client.writer.println(message)
                client.writer.flush()
                if (client.writer.checkError()) {
                    toRemove.add(client.writer)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error writing to history log stream client")
                toRemove.add(client.writer)
            }
        }
        historyLogStreamClients.removeAll { toRemove.contains(it.writer) }
    }

    private inner class DebugApiServer(
        port: Int,
        private val username: String,
        private val password: String
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            // Check authentication
            if (!checkAuth(session)) {
                val response = newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    MIME_PLAINTEXT,
                    "Unauthorized"
                )
                response.addHeader("WWW-Authenticate", "Basic realm=\"ControlX2 Debug API\"")
                return response
            }

            Timber.d("HTTP API request: $method $uri")

            return when {
                method == Method.GET && uri == "/" -> handleIndex()
                method == Method.GET && uri == "/openapi.json" -> handleOpenApiSpec()
                method == Method.GET && uri == "/api/pump/current" -> handlePumpCurrent()
                method == Method.GET && uri == "/api/pump/messages" -> handlePumpMessagesStream(session)
                method == Method.POST && uri == "/api/pump/messages" -> handlePumpMessagesPost(session)
                method == Method.GET && uri == "/api/comm/messages" -> handleMessagingStream(session)
                method == Method.POST && uri == "/api/comm/messages" -> handleMessagingPost(session)
                method == Method.GET && uri == "/api/prefs" -> handlePrefsGet()
                method == Method.GET && uri == "/api/historylog/status" -> handleHistoryLogStatus(session)
                method == Method.GET && uri == "/api/historylog/stats" -> handleHistoryLogStats(session)
                method == Method.GET && uri == "/api/historylog/entries" -> handleHistoryLogEntries(session)
                method == Method.GET && uri.startsWith("/api/historylog/entries/") -> handleHistoryLogEntry(session, uri)
                method == Method.GET && uri == "/api/historylog/types" -> handleHistoryLogTypes(session)
                method == Method.GET && uri == "/api/historylog/stream" -> handleHistoryLogStream(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found: ${method} ${uri}"
                )
            }
        }

        private fun checkAuth(session: IHTTPSession): Boolean {
            val authHeader = session.headers["authorization"] ?: return false

            if (!authHeader.startsWith("Basic ")) {
                return false
            }

            try {
                val encodedCredentials = authHeader.substring(6)
                val decodedBytes = Base64.decode(encodedCredentials, Base64.DEFAULT)
                val credentials = String(decodedBytes)
                val parts = credentials.split(":", limit = 2)

                if (parts.size != 2) {
                    return false
                }

                val (user, pass) = parts
                return user == username && pass == password
            } catch (e: Exception) {
                Timber.w(e, "Error decoding auth header")
                return false
            }
        }

        private fun handleIndex(): Response {
            val ver = AppVersionInfo(context)

            val versionInfo = JSONObject()
            versionInfo.put("version", ver.version)
            versionInfo.put("buildVersion", ver.buildVersion)
            versionInfo.put("buildTime", ver.buildTime)
            val px2Version = JSONObject()
            px2Version.put("version", ver.pumpX2)
            px2Version.put("buildTime", ver.pumpX2BuildTime)
            versionInfo.put("pumpx2", px2Version)

            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                versionInfo.toString()
            )
        }

        private fun handleOpenApiSpec(): Response {
            try {
                val inputStream = context.assets.open("openapi.json")
                val spec = inputStream.bufferedReader().use { it.readText() }
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    spec
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading OpenAPI specification")
                val errorJson = JSONObject()
                errorJson.put("error", "Failed to load OpenAPI specification: ${e.message}")
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    errorJson.toString()
                )
            }
        }

        private fun handlePumpCurrent(): Response {
            val currentData = getCurrentPumpDataCallback?.invoke() ?: "{}"
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                currentData
            )
        }

        private fun handlePumpMessagesStream(session: IHTTPSession): Response {
            val inputStream = object : java.io.InputStream() {
                private val buffer = java.io.PipedOutputStream()
                private val input = java.io.PipedInputStream(buffer, 8192)  // Larger buffer
                private val writer = PrintWriter(buffer, true)

                init {
                    pumpMessageStreamClients.add(writer)
                    Timber.i("New pump messages stream client connected")

                    // Send initial comment to prime the stream
                    writer.println("\n")
                    writer.flush()
                }

                override fun read(): Int {
                    return input.read()
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    return input.read(b, off, len)
                }

                override fun available(): Int {
                    return input.available()
                }

                override fun close() {
                    pumpMessageStreamClients.remove(writer)
                    buffer.close()
                    input.close()
                    Timber.i("Pump messages stream client disconnected")
                }
            }

            val response = newFixedLengthResponse(Response.Status.OK, "application/x-ndjson", inputStream, -1)
            response.addHeader("Cache-Control", "no-cache")
            return response
        }

        private fun handleMessagingStream(session: IHTTPSession): Response {
            val inputStream = object : java.io.InputStream() {
                private val buffer = java.io.PipedOutputStream()
                private val input = java.io.PipedInputStream(buffer, 8192)  // Larger buffer
                private val writer = PrintWriter(buffer, true)

                init {
                    messagingStreamClients.add(writer)
                    Timber.i("New messaging stream client connected")

                    // Send initial comment to prime the stream
                    writer.println("\n")
                    writer.flush()
                }

                override fun read(): Int {
                    return input.read()
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    return input.read(b, off, len)
                }

                override fun available(): Int {
                    return input.available()
                }

                override fun close() {
                    messagingStreamClients.remove(writer)
                    buffer.close()
                    input.close()
                    Timber.i("Messaging stream client disconnected")
                }
            }

            val response = newFixedLengthResponse(Response.Status.OK, "application/x-ndjson", inputStream, -1)
            response.addHeader("Cache-Control", "no-cache")
            return response
        }

        private fun handlePrefsGet(): Response {
            try {
                val allPrefs = prefs.prefs().all
                val jsonObject = JSONObject()

                allPrefs.forEach { (key, value) ->
                    jsonObject.put(key, value)
                }

                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    jsonObject.toString()
                )
            } catch (e: Exception) {
                Timber.e(e, "Error getting preferences")
                val errorJson = JSONObject()
                errorJson.put("error", e.message)
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    errorJson.toString()
                )
            }
        }

        private fun getPumpSidFromSession(session: IHTTPSession?): Int? {
            val paramSid = session?.parameters?.get("pumpSid")?.firstOrNull()?.toIntOrNull()
            if (paramSid != null) {
                return paramSid
            }
            return getCurrentPumpSidCallback?.invoke() ?: Prefs(context).currentPumpSid()
        }

        private fun handleHistoryLogStatus(session: IHTTPSession): Response {
            val pumpSid = getPumpSidFromSession(session)
                ?: return errorResponse("No pumpSid available", Response.Status.BAD_REQUEST)

            return try {
                val oldest = runBlocking { historyLogRepo.getOldest(pumpSid).firstOrNull() }
                val latest = runBlocking { historyLogRepo.getLatest(pumpSid).firstOrNull() }
                val count = runBlocking { historyLogRepo.getCount(pumpSid).firstOrNull() } ?: 0

                val json = JSONObject()
                json.put("pumpSid", pumpSid)
                json.put("totalCount", count)
                json.put("oldestSeqId", oldest?.seqId)
                json.put("newestSeqId", latest?.seqId)
                json.put("oldestPumpTime", oldest?.pumpTime?.toString())
                json.put("newestPumpTime", latest?.pumpTime?.toString())
                json.put("oldestAddedTime", oldest?.addedTime?.toString())
                json.put("newestAddedTime", latest?.addedTime?.toString())

                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            } catch (e: Exception) {
                Timber.e(e, "Error handling history log status")
                errorResponse("${e.message}", Response.Status.INTERNAL_ERROR)
            }
        }

        private fun handleHistoryLogStats(session: IHTTPSession): Response {
            val pumpSid = getPumpSidFromSession(session)
                ?: return errorResponse("No pumpSid available", Response.Status.BAD_REQUEST)

            return try {
                val stats: List<HistoryLogTypeStats> = historyLogRepo.getTypeStats(pumpSid)
                val totalEntries = runBlocking { historyLogRepo.getCount(pumpSid).firstOrNull() } ?: 0
                val json = JSONObject()
                json.put("pumpSid", pumpSid)
                json.put("totalEntries", totalEntries)

                val typeStatsArray = JSONArray()
                stats.forEach { stat ->
                    val obj = JSONObject()
                    obj.put("typeId", stat.typeId)
                    obj.put("typeName", typeNameFromTypeId(stat.typeId))
                    obj.put("count", stat.count)
                    obj.put("latestSeqId", stat.latestSeqId)
                    obj.put("latestPumpTime", stat.latestPumpTime?.toString())
                    typeStatsArray.put(obj)
                }

                json.put("typeStats", typeStatsArray)
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            } catch (e: Exception) {
                Timber.e(e, "Error handling history log stats")
                errorResponse("${e.message}", Response.Status.INTERNAL_ERROR)
            }
        }

        private fun handleHistoryLogEntries(session: IHTTPSession): Response {
            val pumpSid = getPumpSidFromSession(session)
                ?: return errorResponse("No pumpSid available", Response.Status.BAD_REQUEST)

            val params = session.parameters
            val limit = params["limit"]?.firstOrNull()?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            val offset = params["offset"]?.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val typeId = params["typeId"]?.firstOrNull()?.toIntOrNull()
            val seqIdMin = params["seqIdMin"]?.firstOrNull()?.toLongOrNull()
            val seqIdMax = params["seqIdMax"]?.firstOrNull()?.toLongOrNull()
            val pumpTimeMin = parseTimeParam(params["pumpTimeMin"]?.firstOrNull())
            val pumpTimeMax = parseTimeParam(params["pumpTimeMax"]?.firstOrNull())
            val format = params["format"]?.firstOrNull()?.lowercase() ?: "raw"

            return try {
                val items = runBlocking {
                    when {
                        typeId != null && (seqIdMin != null || seqIdMax != null) -> historyLogRepo.getRangeForType(
                            pumpSid,
                            typeId,
                            seqIdMin ?: 0,
                            seqIdMax ?: Long.MAX_VALUE
                        ).firstOrNull() ?: emptyList()
                        typeId != null -> historyLogRepo.allForType(pumpSid, typeId).firstOrNull() ?: emptyList()
                        seqIdMin != null || seqIdMax != null -> historyLogRepo.getRange(
                            pumpSid,
                            seqIdMin ?: 0,
                            seqIdMax ?: Long.MAX_VALUE
                        ).firstOrNull() ?: emptyList()
                        else -> historyLogRepo.getAll(pumpSid).firstOrNull() ?: emptyList()
                    }
                }

                val filtered = filterByTimeRange(items, pumpTimeMin, pumpTimeMax)
                val paged = filtered.drop(offset).take(limit)
                val jsonArray = JSONArray()
                paged.forEach { item ->
                    jsonArray.put(historyLogItemToJson(item, format))
                }

                val json = JSONObject()
                json.put("entries", jsonArray)
                json.put("count", paged.size)
                json.put("hasMore", filtered.size > offset + paged.size)

                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            } catch (e: Exception) {
                Timber.e(e, "Error handling history log entries")
                errorResponse("${e.message}", Response.Status.INTERNAL_ERROR)
            }
        }

        private fun handleHistoryLogEntry(session: IHTTPSession, uri: String): Response {
            val pumpSid = getPumpSidFromSession(session)
                ?: return errorResponse("No pumpSid available", Response.Status.BAD_REQUEST)

            val seqId = uri.substringAfterLast("/").toLongOrNull()
                ?: return errorResponse("Invalid seqId", Response.Status.BAD_REQUEST)
            val format = session.parameters["format"]?.firstOrNull()?.lowercase() ?: "raw"

            val item = runBlocking { historyLogRepo.getRange(pumpSid, seqId, seqId).firstOrNull()?.firstOrNull() }
                ?: return errorResponse("History log entry not found: seqId=$seqId, pumpSid=$pumpSid", Response.Status.NOT_FOUND)

            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                historyLogItemToJson(item, format).toString()
            )
        }

        private fun handleHistoryLogTypes(session: IHTTPSession): Response {
            val pumpSid = getPumpSidFromSession(session)
                ?: return errorResponse("No pumpSid available", Response.Status.BAD_REQUEST)

            return try {
                val stats: List<HistoryLogTypeStats> = historyLogRepo.getTypeStats(pumpSid)
                val typesArray = JSONArray()
                stats.forEach { stat ->
                    val obj = JSONObject()
                    obj.put("typeId", stat.typeId)
                    obj.put("typeName", typeNameFromTypeId(stat.typeId))
                    obj.put("count", stat.count)
                    typesArray.put(obj)
                }

                val json = JSONObject()
                json.put("types", typesArray)
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            } catch (e: Exception) {
                Timber.e(e, "Error handling history log types")
                errorResponse("${e.message}", Response.Status.INTERNAL_ERROR)
            }
        }

        private fun handleHistoryLogStream(session: IHTTPSession): Response {
            val pumpSid = getPumpSidFromSession(session)
                ?: return errorResponse("No pumpSid available", Response.Status.BAD_REQUEST)
            val format = session.parameters["format"]?.firstOrNull()?.lowercase() ?: "raw"
            val inputStream = object : java.io.InputStream() {
                private val buffer = java.io.PipedOutputStream()
                private val input = java.io.PipedInputStream(buffer, 8192)
                private val writer = PrintWriter(buffer, true)
                private val client = HistoryLogStreamClient(writer, format, pumpSid)

                init {
                    historyLogStreamClients.add(client)
                    Timber.i("New history log stream client connected")

                    writer.println("\n")
                    writer.flush()
                }

                override fun read(): Int {
                    return input.read()
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    return input.read(b, off, len)
                }

                override fun available(): Int {
                    return input.available()
                }

                override fun close() {
                    historyLogStreamClients.remove(client)
                    buffer.close()
                    input.close()
                    Timber.i("History log stream client disconnected")
                }
            }

            val response = newFixedLengthResponse(Response.Status.OK, "application/x-ndjson", inputStream, -1)
            response.addHeader("Cache-Control", "no-cache")
            return response
        }

        private fun errorResponse(message: String, status: Response.Status = Response.Status.BAD_REQUEST): Response {
            val json = JSONObject()
            json.put("error", message)
            return newFixedLengthResponse(status, "application/json", json.toString())
        }

        private fun parseTimeParam(value: String?): LocalDateTime? {
            if (value.isNullOrBlank()) return null
            return try {
                LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e: Exception) {
                null
            }
        }

        private fun filterByTimeRange(items: List<HistoryLogItem>, min: LocalDateTime?, max: LocalDateTime?): List<HistoryLogItem> {
            return items.filter { item ->
                val afterMin = min?.let { item.pumpTime >= it } ?: true
                val beforeMax = max?.let { item.pumpTime <= it } ?: true
                afterMin && beforeMax
            }
        }

        private fun handlePumpMessagesPost(session: IHTTPSession): Response {
            try {
                // Read POST body
                val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(contentLength)
                session.inputStream.read(body)
                val bodyString = String(body)

                Timber.d("POST /api/pump/messages body: $bodyString")

                // Parse JSON - can be single object or array
                val messages = mutableListOf<Message>()
                val jsonBody = bodyString.trim()

                if (jsonBody.startsWith("[")) {
                    // Array of messages
                    val jsonArray = JSONArray(jsonBody)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObj = jsonArray.getJSONObject(i)
                        val message = PumpMessageSerializer.fromBytes(jsonObj.toString().toByteArray())
                        if (message != null) {
                            messages.add(message)
                        } else {
                            Timber.w("Failed to deserialize message at index $i")
                        }
                    }
                } else {
                    // Single message
                    val message = PumpMessageSerializer.fromBytes(jsonBody.toByteArray())
                    if (message != null) {
                        messages.add(message)
                    } else {
                        val errorJson = JSONObject()
                        errorJson.put("error", "Failed to deserialize message")
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            errorJson.toString()
                        )
                    }
                }

                if (messages.isEmpty()) {
                    val errorJson = JSONObject()
                    errorJson.put("error", "No valid messages provided")
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        errorJson.toString()
                    )
                }

                // Register pending requests for each message
                val futures = mutableMapOf<Pair<Characteristic, Byte>, CompletableFuture<Message>>()
                messages.forEach { msg ->
                    val key = Pair(msg.characteristic, msg.responseOpCode)
                    val future = CompletableFuture<Message>()
                    pendingPumpRequests[key] = future
                    futures[key] = future
                }

                // Send messages
                val messagesBytes = PumpMessageSerializer.toBulkBytes(messages)
                sendPumpMessagesCallback?.invoke(messagesBytes) ?: run {
                    futures.keys.forEach { pendingPumpRequests.remove(it) }
                    val errorJson = JSONObject()
                    errorJson.put("error", "sendPumpMessagesCallback not configured")
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        errorJson.toString()
                    )
                }

                // Wait for responses (with 30 second timeout)
                val responses = mutableListOf<Message>()
                futures.values.forEach { future ->
                    try {
                        val response = future.get(30, TimeUnit.SECONDS)
                        responses.add(response)
                    } catch (e: Exception) {
                        Timber.w(e, "Timeout or error waiting for pump message response")
                    }
                }

                // Clean up pending requests
                futures.keys.forEach { pendingPumpRequests.remove(it) }

                // Convert responses to JSON array
                val jsonArray = JSONArray()
                responses.forEach { response ->
                    val responseJson = JSONObject(response.jsonToString()).toString()
                    jsonArray.put(JSONObject(responseJson))
                }

                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    jsonArray.toString()
                )

            } catch (e: Exception) {
                Timber.e(e, "Error handling POST /api/pump/messages")
                val errorJson = JSONObject()
                errorJson.put("error", e.message)
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    errorJson.toString()
                )
            }
        }

        private fun handleMessagingPost(session: IHTTPSession): Response {
            try {
                // Read POST body
                val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(contentLength)
                session.inputStream.read(body)
                val bodyString = String(body)

                Timber.d("POST /api/comm/messages body: $bodyString")

                val jsonObj = JSONObject(bodyString)
                val path = jsonObj.optString("path")

                if (path.isEmpty()) {
                    val errorJson = JSONObject()
                    errorJson.put("error", "Missing 'path' field")
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        errorJson.toString()
                    )
                }

                val data: ByteArray = when {
                    jsonObj.has("dataString") -> {
                        jsonObj.getString("dataString").toByteArray()
                    }
                    jsonObj.has("dataHex") -> {
                        val hex = jsonObj.getString("dataHex")
                        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    }
                    else -> {
                        val errorJson = JSONObject()
                        errorJson.put("error", "Missing 'dataString' or 'dataHex' field")
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            errorJson.toString()
                        )
                    }
                }

                sendMessagingCallback?.invoke(path, data) ?: run {
                    val errorJson = JSONObject()
                    errorJson.put("error", "sendMessagingCallback not configured")
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        errorJson.toString()
                    )
                }

                val successJson = JSONObject()
                successJson.put("success", true)
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    successJson.toString()
                )

            } catch (e: Exception) {
                Timber.e(e, "Error handling POST /api/comm/messages")
                val errorJson = JSONObject()
                errorJson.put("error", e.message)
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    errorJson.toString()
                )
            }
        }
    }

    private fun typeNameFromTypeId(typeId: Int): String? {
        return try {
            HistoryLog.fromTypeId(typeId)?.javaClass?.simpleName
        } catch (e: Exception) {
            null
        }
    }

    private fun historyLogItemToJson(item: HistoryLogItem, format: String = "raw"): JSONObject {
        val json = JSONObject()
        json.put("seqId", item.seqId)
        json.put("pumpSid", item.pumpSid)
        json.put("typeId", item.typeId)
        json.put("cargoHex", item.cargo.joinToString("") { "%02x".format(it) })
        json.put("pumpTime", item.pumpTime.toString())
        json.put("addedTime", item.addedTime.toString())

        if (format == "parsed") {
            try {
                val historyLog = item.parse()
                json.put("typeName", historyLog.javaClass.simpleName)
                json.put("parsed", JSONObject(historyLog.jsonToString()))
            } catch (e: Exception) {
                json.put("parseError", e.message)
            }
        }

        return json
    }
}
