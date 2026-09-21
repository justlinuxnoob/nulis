// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.setups

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import android.util.Log
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
import java.util.UUID

private val Context.setupsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "themes",
    corruptionHandler = replaceCorrupted("themes"),
)

/**
 * The setups the user saved: a whole phone - every page, the Look, the colours and the type -
 * kept under a name so it can be come back to.
 *
 * Nulis ships none of these. What it ships is layouts (what is where) and looks (how it is
 * drawn); a setup is only ever something the user made by saving what they had.
 *
 * The stored key is still `themes`, because that is what it was called when it was written and
 * renaming it would lose everybody's saved ones for nothing.
 */
class SetupRepository(context: Context) {

    private val dataStore = context.applicationContext.setupsDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Saved setups, newest last. A corrupt file reads as none rather than crashing. */
    val saved: Flow<List<SavedSetup>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> decode(prefs[KEY_SETUPS]) }

    suspend fun save(setup: SavedSetup) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_SETUPS]).filterNot { it.id == setup.id }
            prefs[KEY_SETUPS] = json.encodeToString(current + setup.copy(builtIn = false))
        }
    }

    suspend fun rename(id: String, name: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_SETUPS]).map { if (it.id == id) it.copy(name = name) else it }
            prefs[KEY_SETUPS] = json.encodeToString(current)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SETUPS] = json.encodeToString(decode(prefs[KEY_SETUPS]).filterNot { it.id == id })
        }
    }

    /** Replaces every saved setup at once. Used by restore; the caller has already validated them. */
    suspend fun replaceAll(setups: List<SavedSetup>) {
        dataStore.edit { prefs -> prefs[KEY_SETUPS] = json.encodeToString(setups.map { it.copy(builtIn = false) }) }
    }

    private fun decode(raw: String?): List<SavedSetup> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<SavedSetup>>(raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Saved setups are unreadable; starting from none", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Saved setups are invalid; starting from none", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SetupRepository"
        // The key kept its old name so that a setup saved before the rename still reads.
        private val KEY_SETUPS = stringPreferencesKey("themes")

        fun newId(): String = "user_${UUID.randomUUID()}"
    }
}
