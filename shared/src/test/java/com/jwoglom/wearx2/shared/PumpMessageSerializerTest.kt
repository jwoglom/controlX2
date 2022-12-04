package com.jwoglom.wearx2.shared

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
}