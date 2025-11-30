package com.jwoglom.controlx2

import android.content.Context
import android.util.Base64
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.util.PumpMessageSerializer
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList

class HttpDebugApiService(private val context: Context, private val port: Int = 18282) {

    private var server: DebugApiServer? = null
    private val prefs = Prefs(context)

    // Streaming clients for /api/pump/messages
    private val pumpMessageStreamClients = CopyOnWriteArrayList<PrintWriter>()

    // Streaming clients for /api/messaging/stream
    private val messagingStreamClients = CopyOnWriteArrayList<PrintWriter>()

    // Callbacks to get current pump data from CommService
    var getCurrentPumpDataCallback: (() -> String)? = null

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
    }

    /**
     * Called when a message is received (from CommService.handleMessageReceived)
     */
    fun onMessagingReceived(path: String, data: ByteArray, sourceNodeId: String) {
        val dataString = String(data)
        val dataHex = data.joinToString("") { "%02x".format(it) }
        val json = """{"path":"$path","sourceNodeId":"$sourceNodeId","dataString":"$dataString","dataHex":"$dataHex"}"""
        broadcastToMessagingClients(json)
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
                method == Method.GET && uri == "/api/pump/current" -> handlePumpCurrent()
                method == Method.GET && uri == "/api/pump/messages" -> handlePumpMessagesStream(session)
                method == Method.GET && uri == "/api/messaging/stream" -> handleMessagingStream(session)
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

        private fun handlePumpCurrent(): Response {
            val currentData = getCurrentPumpDataCallback?.invoke() ?: "{}"
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                currentData
            )
        }

        private fun handlePumpMessagesStream(session: IHTTPSession): Response {
            return newChunkedResponse(
                Response.Status.OK,
                "application/x-ndjson"
            ) { output ->
                val writer = PrintWriter(output, true)
                pumpMessageStreamClients.add(writer)
                Timber.i("New pump messages stream client connected")

                try {
                    // Keep connection open by waiting for client to disconnect
                    while (!writer.checkError()) {
                        Thread.sleep(1000)
                    }
                } catch (e: InterruptedException) {
                    Timber.d("Pump messages stream interrupted")
                } finally {
                    pumpMessageStreamClients.remove(writer)
                    Timber.i("Pump messages stream client disconnected")
                }
            }
        }

        private fun handleMessagingStream(session: IHTTPSession): Response {
            return newChunkedResponse(
                Response.Status.OK,
                "application/x-ndjson"
            ) { output ->
                val writer = PrintWriter(output, true)
                messagingStreamClients.add(writer)
                Timber.i("New messaging stream client connected")

                try {
                    // Keep connection open by waiting for client to disconnect
                    while (!writer.checkError()) {
                        Thread.sleep(1000)
                    }
                } catch (e: InterruptedException) {
                    Timber.d("Messaging stream interrupted")
                } finally {
                    messagingStreamClients.remove(writer)
                    Timber.i("Messaging stream client disconnected")
                }
            }
        }
    }
}
