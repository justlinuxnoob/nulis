// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import android.util.Log
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

/**
 * A group of apps the user made: "Work", "Games", "Things I should delete". An app can be in at
 * most one category, which is what keeps the drawer a list rather than a filing system.
 */
@Serializable
@Immutable
data class AppCategory(
    val id: String,
    val name: String,
    /** App ids, in the order the user put them in. */
    val appIds: List<String> = emptyList(),
)

@Immutable
data class Categories(val all: List<AppCategory> = emptyList()) {
    /** app id -> category, for the drawer's lookup on every row. */
    val byApp: Map<String, AppCategory> = buildMap {
        all.forEach { category -> category.appIds.forEach { put(it, category) } }
    }

    fun of(appId: String): AppCategory? = byApp[appId]
}

private val Context.categoriesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "categories",
    corruptionHandler = replaceCorrupted("categories"),
)

/** The user's own groups, on-device, in their own order. */
class CategoryRepository(context: Context) {

    private val dataStore = context.applicationContext.categoriesDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val categories: Flow<Categories> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { Categories(decode(it[KEY])) }

    suspend fun create(name: String): String {
        val id = "cat_${UUID.randomUUID()}"
        dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(decode(prefs[KEY]) + AppCategory(id, name.trim()))
        }
        return id
    }

    suspend fun rename(id: String, name: String) {
        edit { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    }

    suspend fun delete(id: String) {
        edit { list -> list.filterNot { it.id == id } }
    }

    /** Moves [appId] into [categoryId], taking it out of whatever it was in. Null means "no group". */
    suspend fun assign(appId: String, categoryId: String?) {
        edit { list ->
            list.map { category ->
                when {
                    category.id == categoryId && appId !in category.appIds -> category.copy(appIds = category.appIds + appId)
                    category.id != categoryId && appId in category.appIds -> category.copy(appIds = category.appIds - appId)
                    else -> category
                }
            }
        }
    }

    /** Reorders the groups themselves, which is the order they appear in the drawer. */
    suspend fun move(id: String, delta: Int) {
        edit { list ->
            val from = list.indexOfFirst { it.id == id }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@edit list
            val mutable = list.toMutableList()
            mutable.add(to, mutable.removeAt(from))
            mutable
        }
    }

    suspend fun replaceAll(categories: List<AppCategory>) {
        dataStore.edit { it[KEY] = json.encodeToString(categories) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private suspend fun edit(transform: (List<AppCategory>) -> List<AppCategory>) {
        dataStore.edit { prefs -> prefs[KEY] = json.encodeToString(transform(decode(prefs[KEY]))) }
    }

    private fun decode(raw: String?): List<AppCategory> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<AppCategory>>(raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Categories unreadable; starting from none", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Categories invalid; starting from none", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CategoryRepository"
        val KEY = stringPreferencesKey("categories")
    }
}
