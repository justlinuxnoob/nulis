// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.writing

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

private val Context.writingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "writing",
    corruptionHandler = replaceCorrupted("writing"),
)

/** Notes, journal entries and tasks as three JSON lists, on-device only. */
class WritingRepository(context: Context) {

    private val dataStore = context.applicationContext.writingDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val state: Flow<WritingState> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            WritingState(
                notes = decode<List<Note>>(prefs[KEY_NOTES]).sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }),
                journal = decode<List<JournalEntry>>(prefs[KEY_JOURNAL]).sortedByDescending { it.createdAt },
                tasks = decode<List<Task>>(prefs[KEY_TASKS]).sortedWith(compareBy<Task> { it.done }.thenBy { it.createdAt }),
            )
        }

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun editNotes(transform: (List<Note>) -> List<Note>) = dataStore.edit { prefs ->
        prefs[KEY_NOTES] = json.encodeToString(transform(decode(prefs[KEY_NOTES])))
    }

    suspend fun editJournal(transform: (List<JournalEntry>) -> List<JournalEntry>) = dataStore.edit { prefs ->
        prefs[KEY_JOURNAL] = json.encodeToString(transform(decode(prefs[KEY_JOURNAL])))
    }

    suspend fun editTasks(transform: (List<Task>) -> List<Task>) = dataStore.edit { prefs ->
        prefs[KEY_TASKS] = json.encodeToString(transform(decode(prefs[KEY_TASKS])))
    }

    /** Replaces all three lists at once. Used by restore and by "Reset Nulis". */
    suspend fun replaceAll(notes: List<Note>, journal: List<JournalEntry>, tasks: List<Task>) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTES] = json.encodeToString(notes)
            prefs[KEY_JOURNAL] = json.encodeToString(journal)
            prefs[KEY_TASKS] = json.encodeToString(tasks)
        }
    }

    private inline fun <reified T : List<*>> decode(raw: String?): T {
        if (raw == null) return emptyList<Any>() as T
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable writing data, starting empty", e)
            emptyList<Any>() as T
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid writing data, starting empty", e)
            emptyList<Any>() as T
        }
    }

    private companion object {
        const val TAG = "WritingRepository"
        val KEY_NOTES = stringPreferencesKey("notes")
        val KEY_JOURNAL = stringPreferencesKey("journal")
        val KEY_TASKS = stringPreferencesKey("tasks")
    }
}
