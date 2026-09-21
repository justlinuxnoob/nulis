// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.wellbeing

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Everything the wellbeing features remember. All of it is the user's own choice, all of it is
 * a nudge rather than a lock: nothing here can stop an app opening, and nothing here uses the
 * Accessibility Service.
 */
@Immutable
data class Wellbeing(
    /** Packages that get a breathing pause before they open. */
    val pausedPackages: Set<String> = emptySet(),
    /** How long that pause lasts. */
    val pauseSeconds: Int = 5,
    /** Package -> minutes a day the user asked to be reminded at. */
    val limits: Map<String, Int> = emptyMap(),
    /** Packages allowed through while a focus session is running. */
    val focusAllowed: Set<String> = emptySet(),
    /** True to hide non-allowed apps during a focus session rather than only dimming them. */
    val focusHides: Boolean = false,
    /** Whether a focus session guards app launches at all. */
    val focusGuards: Boolean = true,
) {
    fun pauses(packageName: String): Boolean = packageName in pausedPackages

    fun limitFor(packageName: String): Int? = limits[packageName]
}

private val Context.wellbeingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wellbeing",
    corruptionHandler = replaceCorrupted("wellbeing"),
)

class WellbeingRepository(context: Context) {

    private val dataStore = context.applicationContext.wellbeingDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<Wellbeing> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            Wellbeing(
                pausedPackages = prefs[KEY_PAUSED] ?: emptySet(),
                pauseSeconds = (prefs[KEY_PAUSE_SECONDS] ?: 5).coerceIn(1, 60),
                limits = decodeLimits(prefs[KEY_LIMITS]),
                focusAllowed = prefs[KEY_FOCUS_ALLOWED] ?: emptySet(),
                focusHides = prefs[KEY_FOCUS_HIDES] ?: false,
                focusGuards = prefs[KEY_FOCUS_GUARDS] ?: true,
            )
        }

    suspend fun setPaused(packageName: String, paused: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_PAUSED] ?: emptySet()
            prefs[KEY_PAUSED] = if (paused) current + packageName else current - packageName
        }
    }

    suspend fun setPauseSeconds(seconds: Int) {
        dataStore.edit { it[KEY_PAUSE_SECONDS] = seconds.coerceIn(1, 60) }
    }

    /** A limit of zero or less removes it. */
    suspend fun setLimit(packageName: String, minutes: Int) {
        dataStore.edit { prefs ->
            val current = decodeLimits(prefs[KEY_LIMITS]).toMutableMap()
            if (minutes <= 0) current.remove(packageName) else current[packageName] = minutes
            prefs[KEY_LIMITS] = json.encodeToString(current)
        }
    }

    suspend fun setFocusAllowed(packageName: String, allowed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FOCUS_ALLOWED] ?: emptySet()
            prefs[KEY_FOCUS_ALLOWED] = if (allowed) current + packageName else current - packageName
        }
    }

    suspend fun setFocusHides(hides: Boolean) {
        dataStore.edit { it[KEY_FOCUS_HIDES] = hides }
    }

    suspend fun setFocusGuards(guards: Boolean) {
        dataStore.edit { it[KEY_FOCUS_GUARDS] = guards }
    }

    suspend fun replaceAll(wellbeing: Wellbeing) {
        dataStore.edit { prefs ->
            prefs[KEY_PAUSED] = wellbeing.pausedPackages
            prefs[KEY_PAUSE_SECONDS] = wellbeing.pauseSeconds
            prefs[KEY_LIMITS] = json.encodeToString(wellbeing.limits)
            prefs[KEY_FOCUS_ALLOWED] = wellbeing.focusAllowed
            prefs[KEY_FOCUS_HIDES] = wellbeing.focusHides
            prefs[KEY_FOCUS_GUARDS] = wellbeing.focusGuards
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun decodeLimits(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Int>>(raw)
        } catch (e: SerializationException) {
            Log.w(TAG, "Limits unreadable; starting from none", e)
            emptyMap()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Limits invalid; starting from none", e)
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "WellbeingRepository"
        val KEY_PAUSED = stringSetPreferencesKey("paused")
        val KEY_PAUSE_SECONDS = intPreferencesKey("pause_seconds")
        val KEY_LIMITS = stringPreferencesKey("limits")
        val KEY_FOCUS_ALLOWED = stringSetPreferencesKey("focus_allowed")
        val KEY_FOCUS_HIDES = booleanPreferencesKey("focus_hides")
        val KEY_FOCUS_GUARDS = booleanPreferencesKey("focus_guards")
    }
}

/** Why Nulis is putting a screen between the user and an app. */
enum class PauseReason { CHOSEN, OVER_LIMIT, FOCUS }

/** The whole decision, in one pure function, so it can be tested without a phone. */
data class PauseDecision(val reason: PauseReason?, val minutesToday: Int, val limit: Int?)

/**
 * Whether opening [packageName] should go through a pause first, and why.
 *
 * Order matters: a focus session is the strongest reason, then going past a limit you set, then
 * simply having asked for a pause on this app. Nulis's own settings row never pauses, and
 * neither does anything while the pause is off for that app and no limit or session applies.
 */
fun pauseDecision(
    packageName: String,
    wellbeing: Wellbeing,
    minutesToday: Int,
    focusRunning: Boolean,
): PauseDecision {
    val limit = wellbeing.limitFor(packageName)
    val reason = when {
        focusRunning && wellbeing.focusGuards && packageName !in wellbeing.focusAllowed -> PauseReason.FOCUS
        limit != null && minutesToday >= limit -> PauseReason.OVER_LIMIT
        wellbeing.pauses(packageName) -> PauseReason.CHOSEN
        else -> null
    }
    return PauseDecision(reason, minutesToday, limit)
}

/**
 * True when an app should be drawn faded on the home page and in the drawer: a focus session is
 * running and this app is not one of the ones allowed through.
 */
fun isDimmedByFocus(packageName: String, wellbeing: Wellbeing, focusRunning: Boolean): Boolean =
    focusRunning && wellbeing.focusGuards && packageName !in wellbeing.focusAllowed
