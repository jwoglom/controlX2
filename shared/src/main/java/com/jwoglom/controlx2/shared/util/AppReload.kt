package com.jwoglom.controlx2.shared.util

import android.content.Context
import android.content.Intent
import java.util.concurrent.CopyOnWriteArrayList

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
 *
 * Before exiting, [shutdownHooks] are invoked synchronously (each gets up to
 * [gracefulTimeoutMs]) so callers like CommService can cleanly release the
 * BLE pump connection. Without that, `Runtime.exit(0)` while a GATT link is
 * open leaves the pump in a "peer vanished" state where the next reconnect
 * renegotiates to a high-latency link and silently drops the JPAKE3
 * handshake — surfacing as a spurious "pairing code was invalid" error on
 * the user's next pair attempt.
 */

private val shutdownHooks = CopyOnWriteArrayList<(Long) -> Unit>()

/**
 * Register a hook to run synchronously before [triggerAppReload] exits the
 * process. Hooks receive the per-hook graceful timeout in milliseconds and
 * are expected to perform best-effort cleanup within that budget.
 */
fun registerAppReloadShutdownHook(hook: (timeoutMs: Long) -> Unit) {
    shutdownHooks.add(hook)
}

fun unregisterAppReloadShutdownHook(hook: (timeoutMs: Long) -> Unit) {
    shutdownHooks.remove(hook)
}

@JvmOverloads
fun triggerAppReload(context: Context, gracefulTimeoutMs: Long = 600L) {
    shutdownHooks.forEach { hook ->
        try {
            hook(gracefulTimeoutMs)
        } catch (t: Throwable) {
            // Swallow — we are about to exit; one misbehaving hook should not
            // prevent the rest from running or block the relaunch.
        }
    }
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent!!.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}
