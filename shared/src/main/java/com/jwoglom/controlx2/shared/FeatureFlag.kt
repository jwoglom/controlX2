package com.jwoglom.controlx2.shared

import android.content.Context

/**
 * Per-device runtime gates for experimental behavior. Backed by the shared
 * "WearX2" SharedPreferences file (the same store mobile's `Prefs` and
 * wear's `WearPrefs` use), so values follow the app data lifecycle and are
 * wiped by Clear data. Values are NOT synced between phone and watch — toggle
 * on each device independently.
 *
 * Adding a flag is one enum entry; the per-module Feature Flags screens
 * iterate `values()` automatically.
 */
enum class FeatureFlag {
    BTHostSwitch,
    ;

    val slug: String get() = name

    companion object {
        private const val PREFS_NAME = "WearX2"
        private const val PREFIX = "feature-flag-"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun enabled(context: Context, flag: FeatureFlag): Boolean =
            prefs(context).getBoolean(PREFIX + flag.slug, false)

        fun set(context: Context, flag: FeatureFlag, value: Boolean) {
            prefs(context).edit().putBoolean(PREFIX + flag.slug, value).commit()
        }
    }
}
