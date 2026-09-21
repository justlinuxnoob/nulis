// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.steps

import android.annotation.SuppressLint
import com.nulis.launcher.data.replaceCorrupted
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate

private val Context.stepsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "steps",
    corruptionHandler = replaceCorrupted("steps"),
)

/**
 * One day on the chart.
 *
 * [recorded] separates "you walked nothing that day" from "Nulis was not counting yet". Both used
 * to arrive as a zero, which is why a phone that had had the block for two days drew a week of
 * flat bars and looked broken rather than new.
 */
@Immutable
data class DayCount(val date: LocalDate, val steps: Int, val recorded: Boolean = true)

@Immutable
data class StepsState(
    val granted: Boolean = false,
    val sensorAvailable: Boolean = true,
    val today: Int = 0,
    val goal: Int = DEFAULT_GOAL,
    /** The last seven days ending today, oldest first; days before counting began say so. */
    val history: List<DayCount> = emptyList(),
    /** When today's count last went up, for the walking animation. 0 = never. */
    val lastStepAt: Long = 0L,
) {
    val progress: Float get() = (today / goal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_GOAL = 8_000
    }
}

/**
 * Daily steps from the hardware step counter, which reports steps since boot and only while
 * something listens. Nulis listens only while it is on screen (no service), and keeps the
 * arithmetic that turns the cumulative value into "today":
 *
 * today = carried + (counter - baseline), where baseline is the counter value at the first
 * reading of the day (or 0 after a reboot) and carried is what was already counted today before
 * a reboot moved the counter back to zero. Steps taken between the last reading of one day and
 * the first of the next land on the later day; without a background service that is the best
 * honest answer.
 */
class StepsRepository(private val context: Context) {

    private val dataStore = context.applicationContext.stepsDataStore
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("InlinedApi")
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    val state: Flow<StepsState> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val today = LocalDate.now()
            val date = prefs[KEY_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val counted = if (date == today) prefs[KEY_TODAY] ?: 0 else 0
            val history = decodeHistory(prefs[KEY_HISTORY]).toMutableMap()
            if (date != null && date != today) history[date] = prefs[KEY_TODAY] ?: 0
            history[today] = counted
            StepsState(
                granted = hasPermission(),
                sensorAvailable = sensor != null,
                today = counted,
                goal = prefs[KEY_GOAL] ?: StepsState.DEFAULT_GOAL,
                history = (6 downTo 0).map { back ->
                    val day = today.minusDays(back.toLong())
                    DayCount(day, history[day] ?: 0, recorded = history.containsKey(day))
                },
                lastStepAt = if (date == today) prefs[KEY_LAST_STEP_AT] ?: 0L else 0L,
            )
        }

    /** Raw cumulative counter readings while collected. Empty when there is no sensor. */
    fun counterReadings(): Flow<Float> = callbackFlow {
        val s = sensor
        if (s == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { trySend(event.values[0]) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /** Folds one cumulative reading into today's count, rolling the day and surviving reboots. */
    suspend fun onCounter(counter: Float) {
        val value = counter.toLong()
        val today = LocalDate.now()
        dataStore.edit { prefs ->
            val date = prefs[KEY_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            var baseline = prefs[KEY_BASELINE] ?: value
            var carried = prefs[KEY_CARRIED] ?: 0
            val lastCounter = prefs[KEY_LAST_COUNTER] ?: value
            val previousToday = prefs[KEY_TODAY] ?: 0
            if (date != today) {
                // New day: file yesterday's final count, start today from the last known reading.
                if (date != null) {
                    val history = decodeHistory(prefs[KEY_HISTORY]).toMutableMap()
                    history[date] = previousToday
                    val keep = history.filterKeys { !it.isBefore(today.minusDays(14)) }
                    prefs[KEY_HISTORY] = json.encodeToString(keep.map { it.key.toString() to it.value }.toMap())
                }
                prefs[KEY_DATE] = today.toString()
                baseline = if (value < lastCounter) 0L else lastCounter
                carried = 0
            } else if (value < lastCounter) {
                // Rebooted: the counter restarted. Keep what was counted, count the rest from zero.
                carried = previousToday
                baseline = 0L
            }
            val steps = (carried + (value - baseline)).coerceAtLeast(0L).toInt()
            if (steps > previousToday || date != today) prefs[KEY_LAST_STEP_AT] = System.currentTimeMillis()
            prefs[KEY_BASELINE] = baseline
            prefs[KEY_CARRIED] = carried
            prefs[KEY_LAST_COUNTER] = value
            prefs[KEY_TODAY] = steps
        }
    }

    /** Books the nightly read; see [StepsAlarm]. Called whenever the launcher comes to the front. */
    fun scheduleDailyRead() = StepsAlarm.schedule(context)

    suspend fun setGoal(goal: Int) {
        dataStore.edit { it[KEY_GOAL] = goal.coerceIn(1_000, 30_000) }
    }

    private fun decodeHistory(raw: String?): Map<LocalDate, Int> {
        if (raw == null) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
            .mapNotNull { (k, v) -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to v } }
            .toMap()
    }

    private companion object {
        val KEY_DATE = stringPreferencesKey("date")
        val KEY_BASELINE = longPreferencesKey("baseline")
        val KEY_CARRIED = intPreferencesKey("carried")
        val KEY_LAST_COUNTER = longPreferencesKey("last_counter")
        val KEY_TODAY = intPreferencesKey("today")
        val KEY_GOAL = intPreferencesKey("goal")
        val KEY_HISTORY = stringPreferencesKey("history")
        val KEY_LAST_STEP_AT = longPreferencesKey("last_step_at")
    }
}
