package com.jwoglom.wearx2.shared.util

import com.jwoglom.pumpx2.shared.L
import com.jwoglom.pumpx2.shared.QuadConsumer
import com.jwoglom.pumpx2.shared.TriConsumer
import timber.log.Timber
import java.util.function.BiConsumer
import java.util.function.Consumer

fun setupTimber(
    prefix: String,
    writeCharacteristicFailedCallback: () -> Unit = {},
) {
    if (Timber.treeCount == 0) {
        Timber.plant(DebugTree(prefix, writeCharacteristicFailedCallback))
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