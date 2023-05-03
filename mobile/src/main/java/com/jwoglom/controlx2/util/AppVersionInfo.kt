package com.jwoglom.controlx2.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.google.common.base.Strings
import com.jwoglom.controlx2.BuildConfig
import hu.supercluster.paperwork.Paperwork
import org.json.JSONObject
import java.util.UUID

class AppVersionInfo(val context: Context) {
    val version: String
        get() = BuildConfig.VERSION_NAME

    val pumpX2: String
        get() = com.jwoglom.pumpx2.BuildConfig.PUMPX2_VERSION

    val buildVersion: String
        get() = Paperwork(context).get("build_version")

    val buildTime: String
        get() = Paperwork(context).get("build_time")

    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("version", version)
        o.put("pumpX2", pumpX2)
        o.put("buildVersion", buildVersion)
        o.put("buildTime", buildTime)

        return o
    }

}