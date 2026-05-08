package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTE: TandemError is a JVM-wide enum singleton whose `withExtra` / `withErrorResponse`
 * mutate `this`. Tests reset extras after each case to avoid cross-test contamination.
 */
class PumpCriticalErrorClassifierTest {

    @After
    fun resetEnumState() {
        for (e in TandemError.values()) {
            e.withExtra("")
        }
    }

    // --- Tier coverage ---

    @Test
    fun `every TandemError gets a presentation with non-blank headline and body`() {
        for (e in TandemError.values()) {
            val p = PumpCriticalErrorClassifier.classify(e)
            assertEquals("name should match enum", e.name, p.name)
            assertTrue("headline blank for $e", p.headline.isNotBlank())
            assertTrue("body blank for $e", p.body.isNotBlank())
        }
    }

    @Test
    fun `transient handshake errors are TRANSIENT`() {
        for (e in listOf(
            TandemError.SET_MTU_FAILED,
            TandemError.NOTIFICATION_STATE_FAILED,
            TandemError.CHARACTERISTIC_WRITE_FAILED,
            TandemError.CONNECTION_UPDATE_FAILED,
        )) {
            assertEquals("$e", Severity.TRANSIENT, PumpCriticalErrorClassifier.classify(e).severity)
        }
    }

    @Test
    fun `fatal pairing errors are FATAL`() {
        assertEquals(Severity.FATAL, PumpCriticalErrorClassifier.classify(TandemError.SHARING_CONNECTION_WITH_TCONNECT_APP).severity)
        assertEquals(Severity.FATAL, PumpCriticalErrorClassifier.classify(TandemError.INVALID_SIGNED_HMAC_SIGNATURE).severity)
    }

    // --- BT_CONNECTION_FAILED branches by HCI status in extra ---

    @Test
    fun `BT_CONNECTION_FAILED AUTH_FAILURE recommends re-pair`() {
        TandemError.BT_CONNECTION_FAILED.withExtra("status=AUTH_FAILURE")
        val p = PumpCriticalErrorClassifier.classify(TandemError.BT_CONNECTION_FAILED)
        assertEquals(Severity.ACTIONABLE, p.severity)
        assertTrue("body should mention re-pair: ${p.body}", p.body.contains("re-pair"))
        assertTrue(p.actions.contains(ErrorAction.RepairPump))
    }

    @Test
    fun `BT_CONNECTION_FAILED CONNECTION_TIMEOUT shows timeout message`() {
        TandemError.BT_CONNECTION_FAILED.withExtra("status=CONNECTION_TIMEOUT")
        val p = PumpCriticalErrorClassifier.classify(TandemError.BT_CONNECTION_FAILED)
        assertTrue("body: ${p.body}", p.body.contains("timed out"))
    }

    @Test
    fun `BT_CONNECTION_FAILED CONNECTION_FAILED_ESTABLISHMENT shows timeout message`() {
        TandemError.BT_CONNECTION_FAILED.withExtra("status=CONNECTION_FAILED_ESTABLISHMENT")
        val p = PumpCriticalErrorClassifier.classify(TandemError.BT_CONNECTION_FAILED)
        assertTrue("body: ${p.body}", p.body.contains("timed out"))
    }

    @Test
    fun `BT_CONNECTION_FAILED unknown HCI defaults to toggle Bluetooth`() {
        TandemError.BT_CONNECTION_FAILED.withExtra("status=UNKNOWN_FOO")
        val p = PumpCriticalErrorClassifier.classify(TandemError.BT_CONNECTION_FAILED)
        assertTrue("body: ${p.body}", p.body.contains("toggling Bluetooth"))
    }

    // --- PAIRING_PROMPT_NOT_ACCEPTED_YET suppresses early retry attempts ---

    @Test
    fun `PAIRING_PROMPT_NOT_ACCEPTED_YET retryAttempt 0 is suppressed (high threshold)`() {
        TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET.withExtra("retryAttempt=0, bondState=BONDING")
        val p = PumpCriticalErrorClassifier.classify(TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET)
        assertEquals(Severity.TRANSIENT, p.severity)
        assertEquals(Int.MAX_VALUE, p.occurrenceThreshold)
    }

    @Test
    fun `PAIRING_PROMPT_NOT_ACCEPTED_YET retryAttempt 1 is suppressed`() {
        TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET.withExtra("retryAttempt=1, bondState=BONDING")
        val p = PumpCriticalErrorClassifier.classify(TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET)
        assertEquals(Severity.TRANSIENT, p.severity)
    }

    @Test
    fun `PAIRING_PROMPT_NOT_ACCEPTED_YET retryAttempt 2+ becomes ACTIONABLE`() {
        TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET.withExtra("retryAttempt=2, bondState=BONDING")
        val p = PumpCriticalErrorClassifier.classify(TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET)
        assertEquals(Severity.ACTIONABLE, p.severity)
        assertEquals(1, p.occurrenceThreshold)
    }

    @Test
    fun `parseRetryAttempt extracts integer from extra`() {
        assertEquals(7, PumpCriticalErrorClassifier.parseRetryAttempt("retryAttempt=7, bondState=NONE"))
        assertEquals(0, PumpCriticalErrorClassifier.parseRetryAttempt("retryAttempt=0"))
        assertNull(PumpCriticalErrorClassifier.parseRetryAttempt("nothing here"))
    }

    // --- ERROR_RESPONSE branches by ErrorCode ---

    @Test
    fun `ERROR_RESPONSE INVALID_AUTHENTICATION_ERROR is FATAL with RepairPump`() {
        val resp = ErrorResponse(0, ErrorResponse.ErrorCode.INVALID_AUTHENTICATION_ERROR.id())
        TandemError.ERROR_RESPONSE.withErrorResponse(resp)
        val p = PumpCriticalErrorClassifier.classify(TandemError.ERROR_RESPONSE)
        assertEquals(Severity.FATAL, p.severity)
        assertTrue(p.actions.contains(ErrorAction.RepairPump))
    }

    @Test
    fun `ERROR_RESPONSE TRANSACTION_ID_MISMATCH is TRANSIENT`() {
        val resp = ErrorResponse(0, ErrorResponse.ErrorCode.TRANSACTION_ID_MISMATCH.id())
        TandemError.ERROR_RESPONSE.withErrorResponse(resp)
        val p = PumpCriticalErrorClassifier.classify(TandemError.ERROR_RESPONSE)
        assertEquals(Severity.TRANSIENT, p.severity)
    }

    @Test
    fun `ERROR_RESPONSE MESSAGE_BUFFER_FULL is TRANSIENT`() {
        val resp = ErrorResponse(0, ErrorResponse.ErrorCode.MESSAGE_BUFFER_FULL.id())
        TandemError.ERROR_RESPONSE.withErrorResponse(resp)
        val p = PumpCriticalErrorClassifier.classify(TandemError.ERROR_RESPONSE)
        assertEquals(Severity.TRANSIENT, p.severity)
    }

    @Test
    fun `ERROR_RESPONSE BAD_OPCODE is FATAL bug-report tier`() {
        val resp = ErrorResponse(0x42, ErrorResponse.ErrorCode.BAD_OPCODE.id())
        TandemError.ERROR_RESPONSE.withErrorResponse(resp)
        val p = PumpCriticalErrorClassifier.classify(TandemError.ERROR_RESPONSE)
        assertEquals(Severity.FATAL, p.severity)
        assertTrue("body: ${p.body}", p.body.contains("bug in ControlX2"))
        assertEquals(ErrorResponse.ErrorCode.BAD_OPCODE.id(), p.errorCodeId)
        assertEquals(0x42, p.requestCodeId)
    }

    @Test
    fun `ERROR_RESPONSE without errorResponse falls back to FATAL with unknown code`() {
        // ERROR_RESPONSE with no associated ErrorResponse object — possible if pumpx2 didn't attach one.
        TandemError.ERROR_RESPONSE.withErrorResponse(null)
        val p = PumpCriticalErrorClassifier.classify(TandemError.ERROR_RESPONSE)
        assertEquals(Severity.FATAL, p.severity)
        assertNull(p.errorCodeId)
    }

    // --- JSON round-trip ---

    @Test
    fun `ErrorPresentation round-trips through JSON`() {
        val original = PumpCriticalErrorClassifier.classify(TandemError.SET_MTU_FAILED)
        val parsed = ErrorPresentation.fromJson(original.toJson())
        assertEquals(original.severity, parsed.severity)
        assertEquals(original.name, parsed.name)
        assertEquals(original.headline, parsed.headline)
        assertEquals(original.body, parsed.body)
        assertEquals(original.actions, parsed.actions)
        assertEquals(original.occurrenceThreshold, parsed.occurrenceThreshold)
    }

    @Test
    fun `ErrorPresentation JSON preserves errorCodeId and requestCodeId`() {
        val resp = ErrorResponse(0x55, ErrorResponse.ErrorCode.BAD_CARGO_LENGTH.id())
        TandemError.ERROR_RESPONSE.withErrorResponse(resp)
        val original = PumpCriticalErrorClassifier.classify(TandemError.ERROR_RESPONSE)
        val parsed = ErrorPresentation.fromJson(original.toJson())
        assertEquals(ErrorResponse.ErrorCode.BAD_CARGO_LENGTH.id(), parsed.errorCodeId)
        assertEquals(0x55, parsed.requestCodeId)
    }

    @Test
    fun `ErrorAction round-trips`() {
        for (a in listOf(
            ErrorAction.Retry, ErrorAction.RepairPump, ErrorAction.OpenBluetoothSettings,
            ErrorAction.OpenTconnectAppInfo, ErrorAction.EnableConnectionSharing, ErrorAction.CopyDiagnostics,
        )) {
            assertEquals(a, ErrorAction.fromKey(a.key()))
        }
    }

    // --- Per-error spot checks for the user-facing body voice ---

    @Test
    fun `SET_MTU_FAILED uses friendly Negotiating copy and threshold 3`() {
        val p = PumpCriticalErrorClassifier.classify(TandemError.SET_MTU_FAILED)
        assertEquals("Negotiating Bluetooth connection…", p.body)
        assertEquals(3, p.occurrenceThreshold)
        assertTrue("must not mention MTU", !p.body.contains("MTU"))
        assertTrue("must not mention HMAC", !p.body.contains("HMAC"))
    }

    @Test
    fun `SHARING_CONNECTION_WITH_TCONNECT_APP says Tandem app not t-connect jargon`() {
        val p = PumpCriticalErrorClassifier.classify(TandemError.SHARING_CONNECTION_WITH_TCONNECT_APP)
        assertTrue("body: ${p.body}", p.body.contains("Tandem app"))
        assertEquals("Another app is connected to the pump.", p.headline)
    }

    @Test
    fun `INVALID_SIGNED_HMAC_SIGNATURE is FATAL with RepairPump action`() {
        val p = PumpCriticalErrorClassifier.classify(TandemError.INVALID_SIGNED_HMAC_SIGNATURE)
        assertEquals(Severity.FATAL, p.severity)
        assertTrue("must not mention HMAC", !p.body.contains("HMAC"))
        assertTrue(p.actions.contains(ErrorAction.RepairPump))
    }

    @Test
    fun `UNEXPECTED_TRANSACTION_ID asks soft question about other apps`() {
        val p = PumpCriticalErrorClassifier.classify(TandemError.UNEXPECTED_TRANSACTION_ID)
        assertTrue("body should ask about other apps: ${p.body}", p.body.contains("?"))
        assertTrue(p.actions.contains(ErrorAction.OpenTconnectAppInfo))
    }
}
