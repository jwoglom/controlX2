package com.jwoglom.controlx2.messaging

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.StateSyncBus
import timber.log.Timber

/**
 * Factory for creating MessageBus and StateSyncBus implementations with runtime detection.
 * Automatically chooses between Local (phone-only) and Wear (phone-watch) implementations
 * based on:
 * - Availability of Wear OS libraries
 * - Google Play Services availability
 * - Connected Wear OS devices
 */
object MessageBusFactory {

    /**
     * Create a MessageBus instance with automatic implementation detection.
     * @param context Android context
     * @return MessageBus implementation (LocalMessageBus or WearMessageBus)
     */
    fun createMessageBus(context: Context): MessageBus {
        return when {
            shouldUseWearOs(context) -> {
                Timber.i("MessageBusFactory: Using WearMessageBus (Wear OS available)")
                try {
                    WearMessageBus(context)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to create WearMessageBus, falling back to LocalMessageBus")
                    LocalMessageBus(context)
                }
            }
            else -> {
                Timber.i("MessageBusFactory: Using LocalMessageBus (phone-only mode)")
                LocalMessageBus(context)
            }
        }
    }

    /**
     * Create a StateSyncBus instance with automatic implementation detection.
     * @param context Android context
     * @return StateSyncBus implementation (LocalStateSyncBus or WearStateSyncBus)
     */
    fun createStateSyncBus(context: Context): StateSyncBus {
        return when {
            shouldUseWearOs(context) -> {
                Timber.i("MessageBusFactory: Using WearStateSyncBus (Wear OS available)")
                try {
                    WearStateSyncBus(context)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to create WearStateSyncBus, falling back to LocalStateSyncBus")
                    LocalStateSyncBus(context)
                }
            }
            else -> {
                Timber.i("MessageBusFactory: Using LocalStateSyncBus (phone-only mode)")
                LocalStateSyncBus(context)
            }
        }
    }

    /**
     * Determine if Wear OS should be used based on runtime availability.
     * Checks:
     * 1. Wear OS libraries are present (reflection check)
     * 2. Google Play Services is available
     * 3. Wearable API is accessible
     *
     * @return true if Wear OS should be used, false for phone-only mode
     */
    private fun shouldUseWearOs(context: Context): Boolean {
        // Check if Wear OS classes are available (handles compileOnly dependency)
        if (!isWearOsClassesAvailable()) {
            Timber.d("Wear OS classes not available")
            return false
        }

        // Check if Google Play Services is available
        if (!isGooglePlayServicesAvailable(context)) {
            Timber.d("Google Play Services not available")
            return false
        }

        // Additional check: Can we actually instantiate Wearable API?
        if (!canAccessWearableApi(context)) {
            Timber.d("Cannot access Wearable API")
            return false
        }

        Timber.d("Wear OS is available and will be used")
        return true
    }

    /**
     * Check if Wear OS classes are available in the classpath.
     * Uses reflection to avoid ClassNotFoundException when compileOnly is used.
     */
    private fun isWearOsClassesAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.wearable.Wearable")
            Class.forName("com.google.android.gms.wearable.MessageClient")
            Class.forName("com.google.android.gms.wearable.DataClient")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Check if Google Play Services is available on the device.
     */
    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val apiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
            resultCode == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            Timber.w(e, "Error checking Google Play Services availability")
            false
        }
    }

    /**
     * Check if we can actually access the Wearable API.
     * This handles cases where the library is present but not properly configured.
     */
    private fun canAccessWearableApi(context: Context): Boolean {
        return try {
            // Try to get a Wearable client to ensure the API is accessible
            val wearableClass = Class.forName("com.google.android.gms.wearable.Wearable")
            val getNodeClientMethod = wearableClass.getMethod("getNodeClient", Context::class.java)
            getNodeClientMethod.invoke(null, context)
            true
        } catch (e: Exception) {
            Timber.w(e, "Cannot access Wearable API")
            false
        }
    }

    /**
     * For testing: Force a specific implementation type.
     * @param useWearOs true to force Wear OS, false to force local
     */
    @Volatile
    private var forcedMode: Boolean? = null

    fun forceWearOsMode(enabled: Boolean) {
        forcedMode = enabled
        Timber.w("MessageBusFactory: Forced mode set to ${if (enabled) "Wear OS" else "Local"}")
    }

    fun clearForcedMode() {
        forcedMode = null
        Timber.i("MessageBusFactory: Forced mode cleared")
    }
}
