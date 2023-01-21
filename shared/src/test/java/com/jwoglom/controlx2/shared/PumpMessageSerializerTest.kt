package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.shared.Hex
import org.junit.Assert.assertEquals
import org.junit.Test

class PumpMessageSerializerTest {
    @Test
    fun toBytes_ApiVersionResponse() {
        assertEquals(
            """{"opCode":33,"cargo":"02000500","characteristic":"CURRENT_STATUS"}""",
            String(PumpMessageSerializer.toBytes(ApiVersionResponse(2, 5))))
    }

    @Test
    fun fromBytes_ApiVersionResponse() {
        val expected = ApiVersionResponse(2, 5)
        val actual = PumpMessageSerializer.fromBytes("""{"opCode":33,"cargo":"02000500","characteristic":"CURRENT_STATUS"}""".toByteArray())
        assertEquals(expected.javaClass, actual.javaClass)
        assertEquals(Hex.encodeHexString(expected.cargo), Hex.encodeHexString(actual.cargo))
        assertEquals(expected.majorVersion, (actual as ApiVersionResponse).majorVersion)
        assertEquals(expected.minorVersion, (actual as ApiVersionResponse).minorVersion)
    }

    @Test
    fun toBulkBytes_test() {
        assertEquals(
            """{"opCode":33,"cargo":"02000500","characteristic":"CURRENT_STATUS"};;;{"opCode":33,"cargo":"02000100","characteristic":"CURRENT_STATUS"}""",
            String(PumpMessageSerializer.toBulkBytes(listOf(ApiVersionResponse(2, 5), ApiVersionResponse(2, 1)))))
    }

    @Test
    fun fromBulkBytes_test() {
        val expected1 = ApiVersionResponse(2, 5)
        val expected2 = ApiVersionResponse(2, 1)
        val actual = PumpMessageSerializer.fromBulkBytes("""{"opCode":33,"cargo":"02000500","characteristic":"CURRENT_STATUS"};;;{"opCode":33,"cargo":"02000100","characteristic":"CURRENT_STATUS"}""".toByteArray())
        assertEquals(expected1.javaClass, actual[0].javaClass)
        assertEquals(Hex.encodeHexString(expected1.cargo), Hex.encodeHexString(actual[0].cargo))
        assertEquals(expected1.majorVersion, (actual[0] as ApiVersionResponse).majorVersion)
        assertEquals(expected1.minorVersion, (actual[0] as ApiVersionResponse).minorVersion)

        assertEquals(expected2.javaClass, actual[1].javaClass)
        assertEquals(Hex.encodeHexString(expected2.cargo), Hex.encodeHexString(actual[1].cargo))
        assertEquals(expected2.majorVersion, (actual[1] as ApiVersionResponse).majorVersion)
        assertEquals(expected2.minorVersion, (actual[1] as ApiVersionResponse).minorVersion)
    }
}