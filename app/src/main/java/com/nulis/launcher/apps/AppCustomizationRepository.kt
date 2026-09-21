// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.appCustomizationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_customization",
    corruptionHandler = replaceCorrupted("app_customization"),
)

/**
 * What the user has changed about individual apps: a new name, a different icon from the active
 * pack, and whether the app is kept out of the drawer. Keyed by [AppInfo.id].
 */
@Immutable
data class AppCustomization(
    val names: Map<String, String> = emptyMap(),
    val icons: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = names.isEmpty() && icons.isEmpty() && hidden.isEmpty()
}

/** Per-app names, icons and hidden state, stored on-device. */
class AppCustomizationRepository(context: Context) {

    private val dataStore = context.applicationContext.appCustomizationDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<AppCustomization> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppCustomization(
                names = decodeMap(prefs[KEY_NAMES]),
                icons = decodeMap(prefs[KEY_ICONS]),
                hidden = decodeMap(prefs[KEY_HIDDEN]).keys,
            )
        }

    /** A blank or unchanged name clears the override. */
    suspend fun setName(appId: String, name: String?, original: String) {
        editMap(KEY_NAMES) { map ->
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed == original) map - appId else map + (appId to trimmed)
        }
    }

    suspend fun setIcon(appId: String, drawable: String?) {
        editMap(KEY_ICONS) { map -> if (drawable == null) map - appId else map + (appId to drawable) }
    }

    suspend fun setHidden(appId: String, hidden: Boolean) {
        editMap(KEY_HIDDEN) { map -> if (hidden) map + (appId to "1") else map - appId }
    }

    /** Drawable names only mean something inside the pack they came from. */
    suspend fun clearIcons() {
        dataStore.edit { it.remove(KEY_ICONS) }
    }

    /** Replaces every per-app change at once. Used by restore. */
    suspend fun replaceAll(names: Map<String, String>, icons: Map<String, String>, hidden: Set<String>) {
        dataStore.edit { prefs ->
            if (names.isEmpty()) prefs.remove(KEY_NAMES) else prefs[KEY_NAMES] = json.encodeToString(names)
            if (icons.isEmpty()) prefs.remove(KEY_ICONS) else prefs[KEY_ICONS] = json.encodeToString(icons)
            if (hidden.isEmpty()) prefs.remove(KEY_HIDDEN) else prefs[KEY_HIDDEN] = json.encodeToString(hidden.associateWith { "1" })
        }
    }

    /** Forgets every per-app change. Used by "Reset Nulis". */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private suspend fun editMap(key: Preferences.Key<String>, transform: (Map<String, String>) -> Map<String, String>) {
        dataStore.edit { prefs ->
            val updated = transform(decodeMap(prefs[key]))
            if (updated.isEmpty()) prefs.remove(key) else prefs[key] = json.encodeToString(updated)
        }
    }

    private fun decodeMap(raw: String?): Map<String, String> = try {
        raw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
    } catch (e: SerializationException) {
        emptyMap()
    }

    private companion object {
        val KEY_NAMES = stringPreferencesKey("names")
        val KEY_ICONS = stringPreferencesKey("icons")
        val KEY_HIDDEN = stringPreferencesKey("hidden")
    }
}
