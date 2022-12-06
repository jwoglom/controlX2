package com.jwoglom.wearx2.shared.util

class DebugTree(val prefix: String) : timber.log.Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, "WearX2:${prefix}:$tag", message, t)
    }
}