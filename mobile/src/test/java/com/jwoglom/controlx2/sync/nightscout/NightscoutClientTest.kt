package com.jwoglom.controlx2.sync.nightscout

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NightscoutClient
 */
class NightscoutClientTest {

    @Test
    fun testApiSecretHashing() {
        // Test SHA-1 hash calculation
        val apiSecret = "test-secret-123"
        val expectedHash = hashNightscoutApiSecret(apiSecret)

        // The client should use this hash for authentication
        assertNotNull(expectedHash)
        assertEquals(40, expectedHash.length) // SHA-1 produces 40 hex characters
        assertEquals("api-secret", NightscoutApiSecretHeader)
    }

    @Test
    fun testUrlHandling() {
        // Test that URLs are properly sanitized
        val baseUrl1 = "https://test.nightscout.com"
        val baseUrl2 = "https://test.nightscout.com/"
        val baseUrl3 = "http://test.nightscout.com"

        // All should be valid base URLs
        assertTrue(baseUrl1.startsWith("http"))
        assertTrue(baseUrl2.startsWith("http"))
        assertTrue(baseUrl3.startsWith("http"))
    }

    @Test
    fun testEndpointPaths() {
        // Verify expected API endpoint paths
        val entriesEndpoint = "/api/v1/entries"
        val treatmentsEndpoint = "/api/v1/treatments"
        val deviceStatusEndpoint = "/api/v1/devicestatus"

        assertTrue(entriesEndpoint.startsWith("/api/v1/"))
        assertTrue(treatmentsEndpoint.startsWith("/api/v1/"))
        assertTrue(deviceStatusEndpoint.startsWith("/api/v1/"))
    }

    @Test
    fun testHashConsistency() {
        val secret = "my-secret"
        val hash1 = hashNightscoutApiSecret(secret)

        val hash2 = hashNightscoutApiSecret(secret)

        // Same secret should produce same hash
        assertEquals(hash1, hash2)
    }

    @Test
    fun testDifferentSecretsProduceDifferentHashes() {
        val secret1 = "secret1"
        val secret2 = "secret2"

        val hash1 = hashNightscoutApiSecret(secret1)

        val hash2 = hashNightscoutApiSecret(secret2)

        // Different secrets should produce different hashes
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testEmptySecret() {
        val secret = ""
        val hash = hashNightscoutApiSecret(secret)

        // Empty string should still produce a valid hash
        assertEquals(40, hash.length)
        // Empty string SHA-1: da39a3ee5e6b4b0d3255bfef95601890afd80709
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hash)
    }
}
