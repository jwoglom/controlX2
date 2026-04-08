package com.jwoglom.controlx2.sync.nightscout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightscoutUrlFormatterTest {
    @Test
    fun normalizeNightscoutUrl_addsHttpsWhenMissingScheme() {
        val normalized = normalizeNightscoutUrl("example.com")
        assertEquals("https://example.com", normalized)
    }

    @Test
    fun normalizeNightscoutUrl_removesTrailingSlash() {
        val normalized = normalizeNightscoutUrl("https://example.com/")
        assertEquals("https://example.com", normalized)
    }

    @Test
    fun normalizeNightscoutUrl_returnsNullForInvalidUrl() {
        val normalized = normalizeNightscoutUrl("http://")
        assertNull(normalized)
    }
}
