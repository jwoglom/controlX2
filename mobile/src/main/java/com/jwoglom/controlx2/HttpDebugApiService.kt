package com.jwoglom.controlx2

import android.content.Context
import android.util.Base64
import com.jwoglom.pumpx2.pump.messages.Characteristic
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.util.PumpMessageSerializer
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
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
            val spec = """
{
  "openapi": "3.0.0",
  "info": {
    "title": "ControlX2 HTTP Debug API",
    "version": "1.0.0",
    "description": "HTTP API for debugging and managing ControlX2 pump communication"
  },
  "servers": [
    {
      "url": "http://0.0.0.0:18282",
      "description": "Local debug server"
    }
  ],
  "security": [
    {
      "basicAuth": []
    }
  ],
  "components": {
    "securitySchemes": {
      "basicAuth": {
        "type": "http",
        "scheme": "basic",
        "description": "HTTP Basic Authentication with username and password configured in Settings > Debug"
      }
    },
    "schemas": {
      "PumpData": {
        "type": "object",
        "properties": {
          "statusText": {"type": "string"},
          "connectionTime": {"type": "string", "format": "date-time", "nullable": true},
          "lastMessageTime": {"type": "string", "format": "date-time", "nullable": true},
          "batteryPercent": {"type": "integer", "nullable": true},
          "iobUnits": {"type": "number", "nullable": true},
          "cartridgeRemainingUnits": {"type": "integer", "nullable": true}
        }
      },
      "PumpMessage": {
        "type": "object",
        "description": "Pump message in JSON format from PumpMessageSerializer",
        "additionalProperties": true
      },
      "MessagingStreamEvent": {
        "type": "object",
        "properties": {
          "path": {"type": "string"},
          "sourceNodeId": {"type": "string"},
          "dataString": {"type": "string"},
          "dataHex": {"type": "string"}
        },
        "required": ["path", "sourceNodeId", "dataString", "dataHex"]
      },
      "Preferences": {
        "type": "object",
        "additionalProperties": true,
        "description": "All SharedPreferences key-value pairs"
      },
      "MessagingRequest": {
        "type": "object",
        "properties": {
          "path": {"type": "string", "description": "Message bus path"},
          "dataString": {"type": "string", "description": "Message data as string (use either dataString or dataHex)"},
          "dataHex": {"type": "string", "description": "Message data as hex string (use either dataString or dataHex)"}
        },
        "required": ["path"]
      },
      "Error": {
        "type": "object",
        "properties": {
          "error": {"type": "string"}
        }
      },
      "Success": {
        "type": "object",
        "properties": {
          "success": {"type": "boolean"}
        }
      }
    }
  },
  "paths": {
    "/openapi.json": {
      "get": {
        "summary": "OpenAPI specification",
        "description": "Returns this OpenAPI v3 specification document",
        "responses": {
          "200": {
            "description": "OpenAPI specification",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object"
                }
              }
            }
          }
        }
      }
    },
    "/api/pump/current": {
      "get": {
        "summary": "Get current pump data",
        "description": "Returns the current pump data including battery, IOB, and cartridge status",
        "responses": {
          "200": {
            "description": "Current pump data",
            "content": {
              "application/json": {
                "schema": {
                  "${'$'}ref": "#/components/schemas/PumpData"
                }
              }
            }
          },
          "401": {
            "description": "Unauthorized"
          }
        }
      }
    },
    "/api/pump/messages": {
      "get": {
        "summary": "Stream pump messages",
        "description": "Streaming endpoint that returns newline-delimited JSON (jsonlines) of pump messages as they are received",
        "responses": {
          "200": {
            "description": "Stream of pump messages",
            "content": {
              "application/x-ndjson": {
                "schema": {
                  "type": "string",
                  "description": "Newline-delimited JSON stream of PumpMessage objects"
                }
              }
            }
          },
          "401": {
            "description": "Unauthorized"
          }
        }
      },
      "post": {
        "summary": "Send pump message(s) and wait for responses",
        "description": "Sends one or more pump messages and waits for responses (30 second timeout per message). Accepts either a single message object or an array of messages.",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "oneOf": [
                  {"${'$'}ref": "#/components/schemas/PumpMessage"},
                  {
                    "type": "array",
                    "items": {"${'$'}ref": "#/components/schemas/PumpMessage"}
                  }
                ]
              },
              "examples": {
                "singleMessage": {
                  "summary": "Single message",
                  "value": {
                    "opCode": 123,
                    "characteristic": "CURRENT_STATUS"
                  }
                },
                "multipleMessages": {
                  "summary": "Multiple messages",
                  "value": [
                    {"opCode": 123, "characteristic": "CURRENT_STATUS"},
                    {"opCode": 124, "characteristic": "CURRENT_STATUS"}
                  ]
                }
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Array of response messages",
            "content": {
              "application/json": {
                "schema": {
                  "type": "array",
                  "items": {"${'$'}ref": "#/components/schemas/PumpMessage"}
                }
              }
            }
          },
          "400": {
            "description": "Bad request",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Error"}
              }
            }
          },
          "401": {
            "description": "Unauthorized"
          },
          "500": {
            "description": "Internal server error",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Error"}
              }
            }
          }
        }
      }
    },
    "/api/messaging/stream": {
      "get": {
        "summary": "Stream message bus events",
        "description": "Streaming endpoint that returns newline-delimited JSON (jsonlines) of message bus events as they occur",
        "responses": {
          "200": {
            "description": "Stream of message bus events",
            "content": {
              "application/x-ndjson": {
                "schema": {
                  "type": "string",
                  "description": "Newline-delimited JSON stream of MessagingStreamEvent objects"
                }
              }
            }
          },
          "401": {
            "description": "Unauthorized"
          }
        }
      }
    },
    "/api/prefs": {
      "get": {
        "summary": "Get all preferences",
        "description": "Returns all SharedPreferences as a JSON object",
        "responses": {
          "200": {
            "description": "All preferences",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Preferences"}
              }
            }
          },
          "401": {
            "description": "Unauthorized"
          },
          "500": {
            "description": "Internal server error",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Error"}
              }
            }
          }
        }
      }
    },
    "/api/messaging": {
      "post": {
        "summary": "Send message bus message",
        "description": "Sends a message to the message bus. Does not wait for or return a response.",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {"${'$'}ref": "#/components/schemas/MessagingRequest"},
              "examples": {
                "withDataString": {
                  "summary": "Using dataString",
                  "value": {
                    "path": "/to-pump/command",
                    "dataString": "test"
                  }
                },
                "withDataHex": {
                  "summary": "Using dataHex",
                  "value": {
                    "path": "/to-pump/command",
                    "dataHex": "74657374"
                  }
                }
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Message sent successfully",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Success"}
              }
            }
          },
          "400": {
            "description": "Bad request",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Error"}
              }
            }
          },
          "401": {
            "description": "Unauthorized"
          },
          "500": {
            "description": "Internal server error",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/Error"}
              }
            }
          }
        }
      }
    }
  }
}
            """.trimIndent()

            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                spec
            )
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
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    """{"error":"${e.message}"}"""
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
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            """{"error":"Failed to deserialize message"}"""
                        )
                    }
                }

                if (messages.isEmpty()) {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        """{"error":"No valid messages provided"}"""
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
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        """{"error":"sendPumpMessagesCallback not configured"}"""
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
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    """{"error":"${e.message}"}"""
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
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        """{"error":"Missing 'path' field"}"""
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
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            """{"error":"Missing 'dataString' or 'dataHex' field"}"""
                        )
                    }
                }

                sendMessagingCallback?.invoke(path, data) ?: run {
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        """{"error":"sendMessagingCallback not configured"}"""
                    )
                }

                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"success":true}"""
                )

            } catch (e: Exception) {
                Timber.e(e, "Error handling POST /api/messaging")
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    """{"error":"${e.message}"}"""
                )
            }
        }
    }
}
