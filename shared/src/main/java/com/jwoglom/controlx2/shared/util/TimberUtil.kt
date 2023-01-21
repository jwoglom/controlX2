package com.jwoglom.controlx2.shared.util

import android.content.Context
import com.jwoglom.pumpx2.shared.L
import com.jwoglom.pumpx2.shared.QuadConsumer
import com.jwoglom.pumpx2.shared.TriConsumer
import timber.log.Timber
import java.util.function.BiConsumer
import java.util.function.Consumer

fun setupTimber(
    prefix: String,
    context: Context,
    logToFile: Boolean = false,
    shouldLog: (Int, String) -> Boolean = { _, _ -> false },
    writeCharacteristicFailedCallback: (String) -> Unit = {},
) {
    if (Timber.treeCount == 0) {
        val tree = DebugTree(prefix, context, logToFile, shouldLog, writeCharacteristicFailedCallback)
        Timber.plant(tree)
    }
    L.getPrintln = Consumer {  }
    L.getTimberDebug = TriConsumer { tag: String?, message: String?, args: String? ->
        Timber.tag("L:$tag").d(message, args)
    }
    L.getTimberInfo = TriConsumer { tag: String?, message: String?, args: String? ->
        Timber.tag("L:$tag").i(message, args)
    }
    L.getTimberWarning = TriConsumer { tag: String?, message: String?, args: String? ->
        Timber.tag("L:$tag").w(message, args)
    }
    L.getTimberWarningThrowable = QuadConsumer { tag: String?, t: Throwable?, message: String?, args: String? ->
        Timber.tag("L:$tag").w(t, message, args)
    }
    L.getTimberError = TriConsumer { tag: String?, message: String?, args: String? ->
        Timber.tag("L:$tag").e(message, args)
    }
    L.getTimberErrorThrowable = QuadConsumer { tag: String?, t: Throwable?, message: String?, args: String? ->
        Timber.tag("L:$tag").e(t, message, args)
    }
}