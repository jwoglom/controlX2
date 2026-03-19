package com.jwoglom.controlx2.sync.nightscout.api

import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment

/**
 * Nightscout REST API interface
 *
 * Note: This is a basic implementation using HttpURLConnection.
 * In the future, this could be replaced with Retrofit for more robust HTTP handling.
 */
interface NightscoutApi {
    /**
     * Upload CGM entries to Nightscout
     * POST /api/v1/entries
     */
    suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int>

    /**
     * Upload treatments to Nightscout
     * POST /api/v1/treatments
     */
    suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int>

    /**
     * Upload device status to Nightscout
     * POST /api/v1/devicestatus
     */
    suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean>

    /**
     * Get last N entries from Nightscout
     * GET /api/v1/entries?count=N
     */
    suspend fun getLastEntries(count: Int = 1): Result<List<NightscoutEntry>>

    /**
     * Get last treatment of a specific event type
     * GET /api/v1/treatments?eventType=X&count=1
     */
    suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?>

    /**
     * Get latest announcements
     * GET /api/v1/treatments?find[eventType]=Announcement&count=N
     */
    suspend fun getAnnouncements(count: Int = 10): Result<List<NightscoutAnnouncement>>

    /**
     * Get announcements after a point in time and/or id marker
     */
    suspend fun getAnnouncementsSince(
        sinceTimestamp: Long? = null,
        sinceId: String? = null,
        count: Int = 100
    ): Result<List<NightscoutAnnouncement>>
}
