package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import org.json.JSONObject

/**
 * Classifies a [TandemError] from pumpx2 into a tier-aware presentation that the UI can render.
 *
 * Goals (see plan: shimmering-splashing-jellyfish.md):
 *  - TRANSIENT — self-healing handshake/protocol errors. Hidden until a threshold of repeats.
 *  - ACTIONABLE — user can do something (close another app, accept a Bluetooth prompt, etc.).
 *  - FATAL — no auto-recovery; user must re-pair or file a bug.
 *
 * The presentation carries user-facing copy (no jargon like MTU/HMAC/GATT), an action set the UI
 * wires up to intents, and the raw error fields for diagnostics.
 *
 * No toasts are emitted for any pump-critical-error — display is purely inline on the setup card.
 */
enum class Severity { TRANSIENT, ACTIONABLE, FATAL }

sealed class ErrorAction {
    object Retry : ErrorAction()
    object RepairPump : ErrorAction()
    object OpenBluetoothSettings : ErrorAction()
    object OpenTconnectAppInfo : ErrorAction()
    object EnableConnectionSharing : ErrorAction()
    object CopyDiagnostics : ErrorAction()

    fun key(): String = when (this) {
        is Retry -> "Retry"
        is RepairPump -> "RepairPump"
        is OpenBluetoothSettings -> "OpenBluetoothSettings"
        is OpenTconnectAppInfo -> "OpenTconnectAppInfo"
        is EnableConnectionSharing -> "EnableConnectionSharing"
        is CopyDiagnostics -> "CopyDiagnostics"
    }

    companion object {
        fun fromKey(key: String): ErrorAction? = when (key) {
            "Retry" -> Retry
            "RepairPump" -> RepairPump
            "OpenBluetoothSettings" -> OpenBluetoothSettings
            "OpenTconnectAppInfo" -> OpenTconnectAppInfo
            "EnableConnectionSharing" -> EnableConnectionSharing
            "CopyDiagnostics" -> CopyDiagnostics
            else -> null
        }
    }
}

/**
 * Branches per-model when applicable. PAIRING_CANNOT_BEGIN and similar setup-time errors carry
 * different remediation copy for t:slim X2 vs Mobi.
 */
enum class PumpModelHint { ANY, TSLIM_X2, MOBI }

data class ErrorPresentation(
    val severity: Severity,
    val name: String,
    val headline: String,
    val body: String,
    val rawMessage: String,
    val extra: String,
    val actions: List<ErrorAction>,
    val occurrenceThreshold: Int,
    val timeThresholdMs: Long,
    val errorCodeId: Int? = null,
    val requestCodeId: Int? = null,
    val initiatingMessageClass: String? = null,
    val modelHint: PumpModelHint = PumpModelHint.ANY,
) {
    fun toJson(): String = JSONObject().apply {
        put("severity", severity.name)
        put("name", name)
        put("headline", headline)
        put("body", body)
        put("rawMessage", rawMessage)
        put("extra", extra)
        put("actions", actions.joinToString(",") { it.key() })
        put("occurrenceThreshold", occurrenceThreshold)
        put("timeThresholdMs", timeThresholdMs)
        if (errorCodeId != null) put("errorCodeId", errorCodeId)
        if (requestCodeId != null) put("requestCodeId", requestCodeId)
        if (initiatingMessageClass != null) put("initiatingMessageClass", initiatingMessageClass)
        put("modelHint", modelHint.name)
    }.toString()

    companion object {
        fun fromJson(s: String): ErrorPresentation {
            val o = JSONObject(s)
            val actionsRaw = o.optString("actions", "")
            val actions = if (actionsRaw.isEmpty()) emptyList()
                else actionsRaw.split(",").mapNotNull { ErrorAction.fromKey(it) }
            return ErrorPresentation(
                severity = Severity.valueOf(o.getString("severity")),
                name = o.getString("name"),
                headline = o.getString("headline"),
                body = o.getString("body"),
                rawMessage = o.getString("rawMessage"),
                extra = o.optString("extra", ""),
                actions = actions,
                occurrenceThreshold = o.getInt("occurrenceThreshold"),
                timeThresholdMs = o.getLong("timeThresholdMs"),
                errorCodeId = if (o.has("errorCodeId")) o.getInt("errorCodeId") else null,
                requestCodeId = if (o.has("requestCodeId")) o.getInt("requestCodeId") else null,
                initiatingMessageClass = if (o.has("initiatingMessageClass"))
                    o.getString("initiatingMessageClass") else null,
                modelHint = PumpModelHint.valueOf(o.optString("modelHint", "ANY")),
            )
        }
    }
}

object PumpCriticalErrorClassifier {
    fun classify(error: TandemError): ErrorPresentation {
        val rawMessage = error.message ?: ""
        val extra = error.extra ?: ""
        val initiatingClass = error.initiatingMessage?.javaClass?.simpleName

        // Compile-time exhaustive: when over enum without `else` so adding a new
        // TandemError value forces an update here.
        return when (error) {
            TandemError.SET_MTU_FAILED -> ErrorPresentation(
                severity = Severity.TRANSIENT,
                name = error.name,
                headline = "Connecting…",
                body = "Negotiating Bluetooth connection…",
                rawMessage = rawMessage,
                extra = extra,
                actions = emptyList(),
                occurrenceThreshold = 3,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.NOTIFICATION_STATE_FAILED -> ErrorPresentation(
                severity = Severity.TRANSIENT,
                name = error.name,
                headline = "Waiting for pump",
                body = "Waiting for pump to allow the connection…",
                rawMessage = rawMessage,
                extra = extra,
                actions = emptyList(),
                occurrenceThreshold = 2,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.CHARACTERISTIC_WRITE_FAILED -> ErrorPresentation(
                severity = Severity.TRANSIENT,
                name = error.name,
                headline = "Pump rejected a command",
                body = "Pump rejected a command. Retrying…",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.Retry),
                occurrenceThreshold = 3,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.CONNECTION_UPDATE_FAILED -> ErrorPresentation(
                severity = Severity.TRANSIENT,
                name = error.name,
                headline = "Connecting…",
                body = "Negotiating Bluetooth connection…",
                rawMessage = rawMessage,
                extra = extra,
                actions = emptyList(),
                occurrenceThreshold = 2,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.BT_CONNECTION_FAILED -> {
                val body = when {
                    extra.contains("AUTH_FAILURE") ->
                        "Failed to authenticate with pump. To connect, you need to re-pair."
                    extra.contains("CONNECTION_TIMEOUT") || extra.contains("CONNECTION_FAILED_ESTABLISHMENT") ->
                        "Connection with pump timed out. Retrying…"
                    else -> "Try toggling Bluetooth."
                }
                val actions = if (extra.contains("AUTH_FAILURE"))
                    listOf(ErrorAction.RepairPump, ErrorAction.OpenBluetoothSettings, ErrorAction.Retry)
                else
                    listOf(ErrorAction.OpenBluetoothSettings, ErrorAction.Retry)
                ErrorPresentation(
                    severity = Severity.ACTIONABLE,
                    name = error.name,
                    headline = "Couldn't connect to pump",
                    body = body,
                    rawMessage = rawMessage,
                    extra = extra,
                    actions = actions,
                    occurrenceThreshold = 1,
                    timeThresholdMs = 0,
                    initiatingMessageClass = initiatingClass,
                )
            }
            TandemError.UNEXPECTED_TRANSACTION_ID -> ErrorPresentation(
                severity = Severity.ACTIONABLE,
                name = error.name,
                headline = "Pump message out of sync",
                body = "Transaction ID was out of sync with what the pump expected. Is another app trying to connect to the pump?",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.OpenTconnectAppInfo, ErrorAction.EnableConnectionSharing),
                occurrenceThreshold = 2,
                timeThresholdMs = 30_000,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.UNEXPECTED_OPCODE_REPLY -> ErrorPresentation(
                severity = Severity.ACTIONABLE,
                name = error.name,
                headline = "Unexpected pump response",
                body = "Unexpected pump response. Is another app communicating with the pump?",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.Retry, ErrorAction.OpenTconnectAppInfo, ErrorAction.EnableConnectionSharing),
                occurrenceThreshold = 1,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.UNPROCESSABLE_MESSAGE -> ErrorPresentation(
                severity = Severity.ACTIONABLE,
                name = error.name,
                headline = "Couldn't read pump response",
                body = "Couldn't process pump responses. This pump firmware version may be unsupported.",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.Retry, ErrorAction.CopyDiagnostics),
                occurrenceThreshold = 1,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.PAIRING_CANNOT_BEGIN -> ErrorPresentation(
                severity = Severity.ACTIONABLE,
                name = error.name,
                headline = "Pump isn't ready to pair",
                body = "Open the Pair Device screen on your pump.",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.Retry),
                occurrenceThreshold = 1,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
                // The UI will branch the body further on the connected pump model
                // (t:slim X2 vs Mobi) — see PumpSetupStageDescription rendering.
                modelHint = PumpModelHint.ANY,
            )
            TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET -> {
                val retryAttempt = parseRetryAttempt(extra)
                if (retryAttempt != null && retryAttempt <= 1) {
                    // Suppressed: pumpx2 retries this every tick during the pairing race.
                    ErrorPresentation(
                        severity = Severity.TRANSIENT,
                        name = error.name,
                        headline = "Pairing…",
                        body = "Waiting for Bluetooth pairing prompt. Accept the connection on your phone.",
                        rawMessage = rawMessage,
                        extra = extra,
                        actions = listOf(ErrorAction.OpenBluetoothSettings),
                        occurrenceThreshold = Int.MAX_VALUE,
                        timeThresholdMs = 0,
                        initiatingMessageClass = initiatingClass,
                    )
                } else {
                    ErrorPresentation(
                        severity = Severity.ACTIONABLE,
                        name = error.name,
                        headline = "Pairing…",
                        body = "Waiting for Bluetooth pairing prompt. Accept the connection on your phone.",
                        rawMessage = rawMessage,
                        extra = extra,
                        actions = listOf(ErrorAction.OpenBluetoothSettings),
                        occurrenceThreshold = 1,
                        timeThresholdMs = 0,
                        initiatingMessageClass = initiatingClass,
                    )
                }
            }
            TandemError.SHARING_CONNECTION_WITH_TCONNECT_APP -> ErrorPresentation(
                severity = Severity.FATAL,
                name = error.name,
                headline = "Another app is connected to the pump.",
                body = "Only one app can connect to the pump at the same time. To use ControlX2, disconnect the pump from the Tandem app.",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.OpenTconnectAppInfo, ErrorAction.EnableConnectionSharing),
                occurrenceThreshold = 1,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.INVALID_SIGNED_HMAC_SIGNATURE -> ErrorPresentation(
                severity = Severity.FATAL,
                name = error.name,
                headline = "Pairing code is incorrect",
                body = if (extra.isNotBlank()) extra
                    else "ControlX2 cannot authenticate with your pump. You must re-pair.",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.RepairPump, ErrorAction.CopyDiagnostics),
                occurrenceThreshold = 1,
                timeThresholdMs = 0,
                initiatingMessageClass = initiatingClass,
            )
            TandemError.ERROR_RESPONSE -> classifyErrorResponse(error, rawMessage, extra, initiatingClass)
        }
    }

    private fun classifyErrorResponse(
        error: TandemError,
        rawMessage: String,
        extra: String,
        initiatingClass: String?,
    ): ErrorPresentation {
        val errorResponse: ErrorResponse? = error.errorResponse
        val errorCode = errorResponse?.errorCode
        val errorCodeId = errorCode?.id()
        val requestCodeId = errorResponse?.requestCodeId

        return when (errorCode) {
            ErrorResponse.ErrorCode.INVALID_AUTHENTICATION_ERROR -> ErrorPresentation(
                severity = Severity.FATAL,
                name = error.name,
                headline = "Pairing code is incorrect",
                body = "ControlX2 cannot authenticate with your pump. You must re-pair.",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.RepairPump, ErrorAction.CopyDiagnostics),
                occurrenceThreshold = 1,
                timeThresholdMs = 0,
                errorCodeId = errorCodeId,
                requestCodeId = requestCodeId,
                initiatingMessageClass = initiatingClass,
            )
            ErrorResponse.ErrorCode.TRANSACTION_ID_MISMATCH,
            ErrorResponse.ErrorCode.MESSAGE_BUFFER_FULL -> ErrorPresentation(
                severity = Severity.TRANSIENT,
                name = error.name,
                headline = "Pump is busy",
                body = "Pump is busy, reconnecting…",
                rawMessage = rawMessage,
                extra = extra,
                actions = listOf(ErrorAction.Retry),
                occurrenceThreshold = 2,
                timeThresholdMs = 0,
                errorCodeId = errorCodeId,
                requestCodeId = requestCodeId,
                initiatingMessageClass = initiatingClass,
            )
            ErrorResponse.ErrorCode.CRC_MISMATCH,
            ErrorResponse.ErrorCode.BAD_CARGO_LENGTH,
            ErrorResponse.ErrorCode.BAD_OPCODE,
            ErrorResponse.ErrorCode.INVALID_REQUIRED_PARAMETER,
            ErrorResponse.ErrorCode.UNDEFINED_ERROR,
            null -> {
                val codeLabel = errorCode?.name ?: errorCodeId?.toString() ?: "unknown"
                val requestLabel = requestCodeId?.toString() ?: "?"
                ErrorPresentation(
                    severity = Severity.FATAL,
                    name = error.name,
                    headline = "The pump rejected a request",
                    body = "ControlX2 sent a message the pump did not accept (code: $codeLabel, request: $requestLabel). This is likely a bug in ControlX2.",
                    rawMessage = rawMessage,
                    extra = extra,
                    actions = listOf(ErrorAction.Retry, ErrorAction.CopyDiagnostics),
                    occurrenceThreshold = 1,
                    timeThresholdMs = 0,
                    errorCodeId = errorCodeId,
                    requestCodeId = requestCodeId,
                    initiatingMessageClass = initiatingClass,
                )
            }
        }
    }

    /** Parses `retryAttempt=N, ...` style extras attached by pumpx2's retry loop. */
    internal fun parseRetryAttempt(extra: String): Int? {
        val m = Regex("retryAttempt=(\\d+)").find(extra) ?: return null
        return m.groupValues[1].toIntOrNull()
    }
}
