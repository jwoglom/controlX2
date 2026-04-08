package com.jwoglom.controlx2.sync.nightscout.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement

internal class NightscoutAnnouncementParser(
    private val gson: Gson
) {
    fun parseAnnouncements(response: String): List<NightscoutAnnouncement> {
        if (response.isBlank()) {
            return emptyList()
        }

        val json: JsonElement = JsonParser.parseString(response)
        val payloadArray = when {
            json.isJsonArray -> json.asJsonArray
            json.isJsonObject && json.asJsonObject.has("result") && json.asJsonObject["result"].isJsonArray -> {
                json.asJsonObject["result"].asJsonArray
            }
            else -> JsonArray()
        }

        if (payloadArray.size() == 0) {
            return emptyList()
        }

        val type = object : TypeToken<List<NightscoutAnnouncement>>() {}.type
        return gson.fromJson(payloadArray, type)
    }
}
