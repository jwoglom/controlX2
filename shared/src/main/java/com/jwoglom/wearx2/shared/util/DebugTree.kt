package com.jwoglom.wearx2.shared.util

class DebugTree(val prefix: String) : timber.log.Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (tag?.startsWith("PumpX2") == true) return;
        super.log(priority, "WearX2:${prefix}:$tag", message, t)
    }
}