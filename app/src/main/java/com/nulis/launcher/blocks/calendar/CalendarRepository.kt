// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One entry from the phone's calendar. Read-only; Nulis never writes to a calendar. */
@Immutable
data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    /** The calendar's own colour, so a busy day still reads as several calendars. */
    val color: Int,
) {
    val date: LocalDate get() = start.toLocalDate()
}

@Immutable
data class CalendarState(
    val granted: Boolean = false,
    /** Events from today through the next two weeks, in time order. */
    val events: List<CalendarEvent> = emptyList(),
) {
    fun on(date: LocalDate): List<CalendarEvent> = events.filter { it.date == date }

    /** The next event that has not finished yet. */
    fun next(now: LocalDateTime): CalendarEvent? = events.firstOrNull { it.end.isAfter(now) }
}

/**
 * Reads the phone's calendar, and only reads it. The permission is optional: without it the
 * week strip still works, because a week strip needs no data at all.
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Two weeks from the start of today. Enough for a week strip and an agenda, and no more. */
    suspend fun load(days: Int = 14): CalendarState = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CalendarState(granted = false)
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.now().plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
            it.build()
        }
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR,
        )
        val events = ArrayList<CalendarEvent>()
        try {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val allDay = cursor.getInt(4) == 1
                    events += CalendarEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        start = at(cursor.getLong(2), allDay, zone),
                        end = at(cursor.getLong(3), allDay, zone),
                        allDay = allDay,
                        color = cursor.getInt(5),
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Calendar read refused", e)
            return@withContext CalendarState(granted = false)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Calendar provider unavailable", e)
        }
        CalendarState(granted = true, events = events)
    }

    /**
     * All-day events are stored at UTC midnight, so reading them in the local zone would slide
     * them onto the day before for anyone west of Greenwich.
     */
    private fun at(millis: Long, allDay: Boolean, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(if (allDay) ZoneId.of("UTC") else zone).toLocalDateTime()

    private companion object {
        const val TAG = "CalendarRepository"
    }
}
