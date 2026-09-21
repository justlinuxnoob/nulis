// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.gesturesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gestures",
    corruptionHandler = replaceCorrupted("gestures"),
)

/** One stored string per trigger. Absent means "still on the default". */
class GesturesRepository(context: Context) {

    private val dataStore = context.applicationContext.gesturesDataStore

    val settings: Flow<GestureSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { stored ->
            GestureSettings(
                GestureTrigger.entries.mapNotNull { trigger ->
                    GestureBinding.parse(stored[key(trigger)])?.let { trigger to it }
                }.toMap(),
            )
        }

    suspend fun set(trigger: GestureTrigger, binding: GestureBinding) {
        dataStore.edit { it[key(trigger)] = binding.store() }
    }

    /** Clears every mapping, so each trigger falls back to [GestureTrigger.default]. */
    suspend fun reset() {
        dataStore.edit { prefs -> GestureTrigger.entries.forEach { prefs.remove(key(it)) } }
    }

    /** Every binding the user has actually set, as trigger id -> stored form. For backup. */
    suspend fun exportBindings(): Map<String, String> {
        val stored = dataStore.data.first()
        return GestureTrigger.entries.mapNotNull { trigger ->
            stored[key(trigger)]?.let { trigger.id to it }
        }.toMap()
    }

    /** Replaces every mapping with the ones in [bindings]; anything missing falls back to its default. */
    suspend fun importBindings(bindings: Map<String, String>) {
        dataStore.edit { prefs ->
            GestureTrigger.entries.forEach { trigger ->
                val value = bindings[trigger.id]
                if (value == null) prefs.remove(key(trigger)) else prefs[key(trigger)] = value
            }
        }
    }

    private fun key(trigger: GestureTrigger) = stringPreferencesKey("trigger_${trigger.id}")
}
