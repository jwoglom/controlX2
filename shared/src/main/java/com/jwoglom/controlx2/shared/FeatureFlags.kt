package com.jwoglom.controlx2.shared

/**
 * Compile-time feature gates. Distinct from the runtime [FeatureFlag] enum:
 * these are `const val`s so the Kotlin compiler / R8 can statically eliminate
 * gated branches.
 */
object FeatureFlags {
    /** Connection sharing with the Tandem app is no longer supported. Gate any UI / code path that relies on it. */
    const val ConnectionSharing: Boolean = false
}
