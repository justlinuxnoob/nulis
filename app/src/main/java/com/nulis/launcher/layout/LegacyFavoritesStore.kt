// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.legacyFavoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorites",
    corruptionHandler = replaceCorrupted("favorites"),
)

/**
 * Reads the favorites saved by versions before the block system existed, so they can be
 * migrated into the default Apps block. Only ever read; the block layout is the source of truth now.
 */
class LegacyFavoritesStore(context: Context) {

    private val dataStore = context.applicationContext.legacyFavoritesDataStore

    suspend fun readOnce(): List<String> {
        val prefs = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        return prefs[KEY_IDS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    private companion object {
        val KEY_IDS = stringPreferencesKey("favorite_ids")
    }
}
