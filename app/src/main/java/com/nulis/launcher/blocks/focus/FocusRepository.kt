// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.focus

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate

/** One finished stretch of focus. Only the day and the length; nothing about what was done. */
@Serializable
data class FocusSession(val date: String, val minutes: Int)

/** A session that is running right now, held in memory only. */
@Immutable
data class RunningFocus(
    /** Wall clock when the session ends. */
    val endsAtMillis: Long,
    val totalMinutes: Int,
    /** True for the short break that follows a stretch of work. */
    val isBreak: Boolean,
)

@Immutable
data class FocusState(
    val running: RunningFocus? = null,
    /** Minutes of focus finished today. */
    val todayMinutes: Int = 0,
    /** Minutes of focus finished in the last seven days, today included. */
    val weekMinutes: Int = 0,
    /** How many finished sessions there have been today. */
    val todaySessions: Int = 0,
    /** Length of a work stretch, in minutes. */
    val focusMinutes: Int = 25,
    /** Length of the break that follows one. */
    val breakMinutes: Int = 5,
    /** Every session of the last 30 days, newest first; the weekly summary reads this. */
    val history: List<FocusSession> = emptyList(),
)

/** Starting and stopping a session, and changing its length. Implemented by the view model. */
interface FocusActions {
    fun start(isBreak: Boolean)
    fun stop()
    fun finish()
    fun setLengths(focusMinutes: Int, breakMinutes: Int)

    object None : FocusActions {
        override fun start(isBreak: Boolean) = Unit
        override fun stop() = Unit
        override fun finish() = Unit
        override fun setLengths(focusMinutes: Int, breakMinutes: Int) = Unit
    }
}

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "focus",
    corruptionHandler = replaceCorrupted("focus"),
)

/**
 * The log of finished sessions, and the two lengths. Nothing about a session leaves the phone
 * and nothing about it is identifying: a date and a number of minutes.
 */
class FocusRepository(context: Context) {

    private val dataStore = context.applicationContext.focusDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<FocusState> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val history = decode(prefs[KEY_SESSIONS])
            val today = LocalDate.now().toString()
            val weekStart = LocalDate.now().minusDays(6)
            FocusState(
                todayMinutes = history.filter { it.date == today }.sumOf { it.minutes },
                todaySessions = history.count { it.date == today },
                weekMinutes = history.filter { runCatching { LocalDate.parse(it.date) >= weekStart }.getOrDefault(false) }.sumOf { it.minutes },
                focusMinutes = prefs[KEY_FOCUS_MINUTES] ?: 25,
                breakMinutes = prefs[KEY_BREAK_MINUTES] ?: 5,
                history = history,
            )
        }

    /** Files a finished session and forgets anything older than a month. */
    suspend fun record(minutes: Int) {
        if (minutes <= 0) return
        dataStore.edit { prefs ->
            val cutoff = LocalDate.now().minusDays(30)
            val kept = decode(prefs[KEY_SESSIONS])
                .filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
            prefs[KEY_SESSIONS] = json.encodeToString(listOf(FocusSession(LocalDate.now().toString(), minutes)) + kept)
        }
    }

    suspend fun setLengths(focusMinutes: Int, breakMinutes: Int) {
        dataStore.edit {
            it[KEY_FOCUS_MINUTES] = focusMinutes.coerceIn(1, 180)
            it[KEY_BREAK_MINUTES] = breakMinutes.coerceIn(1, 60)
        }
    }

    /** Wipes the log. Used by "Reset Nulis". */
    suspend fun clear() {
        dataStore.edit { it.remove(KEY_SESSIONS) }
    }

    /** Replaces the whole log; used by restore, which has already validated the data. */
    suspend fun replaceAll(sessions: List<FocusSession>, focusMinutes: Int, breakMinutes: Int) {
        dataStore.edit {
            it[KEY_SESSIONS] = json.encodeToString(sessions)
            it[KEY_FOCUS_MINUTES] = focusMinutes
            it[KEY_BREAK_MINUTES] = breakMinutes
        }
    }

    private fun decode(raw: String?): List<FocusSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<FocusSession>>(raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Focus log unreadable; starting empty", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Focus log invalid; starting empty", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "FocusRepository"
        val KEY_SESSIONS = stringPreferencesKey("sessions")
        val KEY_FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val KEY_BREAK_MINUTES = intPreferencesKey("break_minutes")

        @Suppress("unused")
        val KEY_LEGACY = longPreferencesKey("unused")
    }
}
