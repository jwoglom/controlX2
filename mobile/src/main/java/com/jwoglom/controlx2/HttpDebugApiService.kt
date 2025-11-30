package com.jwoglom.controlx2

import android.content.Context
import android.util.Base64
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.util.PumpMessageSerializer
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class HttpDebugApiService(private val context: Context, private val port: Int = 18282) {

    private var server: DebugApiServer? = null
    private val prefs = Prefs(context)

    // Streaming clients for /api/pump/messages
    private val pumpMessageStreamClients = CopyOnWriteArrayList<PrintWriter>()

    // Streaming clients for /api/messaging/stream
    private val messagingStreamClients = CopyOnWriteArrayList<PrintWriter>()

    // Pending pump message requests waiting for responses
    private val pendingPumpRequests = ConcurrentHashMap<Pair<Characteristic, Byte>, CompletableFuture<Message>>()

    // Callbacks to get current pump data from CommService
    var getCurrentPumpDataCallback: (() -> String)? = null

    // Callback to send pump messages
    var sendPumpMessagesCallback: ((ByteArray) -> Unit)? = null

    // Callback to send messaging messages
    var sendMessagingCallback: ((String, ByteArray) -> Unit)? = null

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
        messagingStreamClients.clear()
        Timber.i("HttpDebugApiService stopped")
    }

    fun isRunning(): Boolean = server?.isAlive == true

    /**
     * Called when a pump message is received (from CommService.PumpCommHandler.Pump.onReceiveMessage)
     */
    fun onPumpMessageReceived(message: Message) {
        val jsonString = message.jsonToString()
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

    private fun broadcastToPumpMessageClients(message: String) {
        val toRemove = mutableListOf<PrintWriter>()
        pumpMessageStreamClients.forEach { writer ->
            try {
                writer.println(message)
                writer.flush()
                if (writer.checkError()) {
                    toRemove.add(writer)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error writing to pump message stream client")
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
                method == Method.GET && uri == "/openapi.json" -> handleOpenApiSpec()
                method == Method.GET && uri == "/api/pump/current" -> handlePumpCurrent()
                method == Method.GET && uri == "/api/pump/messages" -> handlePumpMessagesStream(session)
                method == Method.GET && uri == "/api/messaging/stream" -> handleMessagingStream(session)
                method == Method.GET && uri == "/api/prefs" -> handlePrefsGet()
                method == Method.POST && uri == "/api/pump/messages" -> handlePumpMessagesPost(session)
                method == Method.POST && uri == "/api/messaging" -> handleMessagingPost(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found"
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
                private val input = java.io.PipedInputStream(buffer)
                private val writer = PrintWriter(buffer, true)

                init {
                    pumpMessageStreamClients.add(writer)
                    Timber.i("New pump messages stream client connected")
                }

                override fun read(): Int {
                    return input.read()
                }

                override fun close() {
                    pumpMessageStreamClients.remove(writer)
                    buffer.close()
                    input.close()
                    Timber.i("Pump messages stream client disconnected")
                }
            }

            val response = newFixedLengthResponse(Response.Status.OK, "application/x-ndjson", inputStream, -1)
            response.addHeader("Transfer-Encoding", "chunked")
            return response
        }

        private fun handleMessagingStream(session: IHTTPSession): Response {
            val inputStream = object : java.io.InputStream() {
                private val buffer = java.io.PipedOutputStream()
                private val input = java.io.PipedInputStream(buffer)
                private val writer = PrintWriter(buffer, true)

                init {
                    messagingStreamClients.add(writer)
                    Timber.i("New messaging stream client connected")
                }

                override fun read(): Int {
                    return input.read()
                }

                override fun close() {
                    messagingStreamClients.remove(writer)
                    buffer.close()
                    input.close()
                    Timber.i("Messaging stream client disconnected")
                }
            }

            val response = newFixedLengthResponse(Response.Status.OK, "application/x-ndjson", inputStream, -1)
            response.addHeader("Transfer-Encoding", "chunked")
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
                        val message = PumpMessageSerializer.fromJSON(jsonObj.toString())
                        if (message != null) {
                            messages.add(message)
                        } else {
                            Timber.w("Failed to deserialize message at index $i")
                        }
                    }
                } else {
                    // Single message
                    val message = PumpMessageSerializer.fromJSON(jsonBody)
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
                    val key = Pair(msg.characteristic, msg.opCode())
                    val future = CompletableFuture<Message>()
                    pendingPumpRequests[key] = future
                    futures[key] = future
                }

                // Send messages
                val messagesBytes = PumpMessageSerializer.toBytes(messages.toTypedArray())
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
                    jsonArray.put(JSONObject(response.jsonToString()))
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

                Timber.d("POST /api/messaging body: $bodyString")

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
                Timber.e(e, "Error handling POST /api/messaging")
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
}
