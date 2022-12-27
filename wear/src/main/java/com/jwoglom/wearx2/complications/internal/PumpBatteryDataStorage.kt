package com.jwoglom.wearx2.complications.internal
/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.jwoglom.wearx2.util.StatePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "WearX2Complication")

/**
 * Returns the current state for a given complication.
 */
fun ComplicationToggleArgs.getPumpBatteryState(context: Context): Long? {
    val pumpBattery = StatePrefs(context).pumpBattery
    return pumpBattery?.first?.toLongOrNull()

//    val stateKey = getStatePreferenceKey("pumpBatteryState")
//    return context.dataStore.data
//        .map { preferences ->
//            preferences[stateKey] ?: 0
//        }
//        .first()
}
//
///**
// * Updates the current state for a given complication, incrementing it by 1.
// */
//suspend fun ComplicationToggleArgs.updatePumpBatteryState(context: Context, value: Pair<String, Instant>?) {
//    val stateKey = getStatePreferenceKey("pumpBatteryState")
//    context.dataStore.edit { preferences ->
//        preferences[stateKey] = value
//    }
//}