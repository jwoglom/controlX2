package com.jwoglom.controlx2.shared.util

import android.content.Context
import android.content.Intent

/**
 * Cold-starts the app: schedules the launcher activity in a fresh task and
 * exits the current process. Used when a config change requires the full
 * process to be torn down so caches, workers, and singletons re-initialize —
 * specifically after toggles of the device role, service-enabled pref, or
 * Nightscout/xDrip settings that the running services have already bound to.
 *
 * Previously duplicated in four places (mobile MainActivity + CommService,
 * watch MainActivity + WearPumpCommService) plus an inline copy in
 * PumpSetup.kt. Callers should prefer sending `TO_SERVER_APP_RELOAD` through
 * the message bus; this helper is the common implementation behind that
 * path.
 *
 * Note: `Runtime.exit(0)` is intentional — `finishAffinity()` alone does not
 * kill the process, which means Timber, workers, and connection pools keep
 * their old state across the "reload".
 */
fun triggerAppReload(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent!!.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}
