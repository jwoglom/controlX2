package com.jwoglom.wearx2.shared.util

import com.jwoglom.pumpx2.shared.L
import timber.log.Timber
import java.util.function.Consumer

fun setupTimber(prefix: String) {
    if (Timber.treeCount == 0) {
        Timber.plant(DebugTree(prefix))
    }
    L.getPrintln = Consumer {  }
}