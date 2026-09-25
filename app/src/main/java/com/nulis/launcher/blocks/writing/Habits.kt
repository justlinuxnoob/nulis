// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.writing

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Something somebody wants to do every day: read, walk, water the plants. */
@Serializable
data class Habit(val id: String, val name: String, val createdAt: Long)

/**
 * Every habit, and the days each one was done.
 *
 * Days are stored as ISO dates ("2026-03-17") per habit, which is what a person reading a backup
 * would expect to find, and what survives a change of time zone: a day is a day, not an instant.
 * Only the last [KeepDays] are kept, so a year of ticks is a few kilobytes and never grows past it.
 */
@Immutable
@Serializable
data class Habits(
    val habits: List<Habit> = emptyList(),
    val done: Map<String, List<String>> = emptyMap(),
) {
    fun isDone(habitId: String, date: LocalDate): Boolean = date.toString() in done[habitId].orEmpty()

    /** How many of today's habits are ticked. */
    fun doneOn(date: LocalDate): Int = habits.count { isDone(it.id, date) }

    /**
     * Days in a row, ending today - or ending yesterday, if today is not ticked yet: a streak is
     * not broken at breakfast by a walk that happens in the evening.
     */
    fun streak(habitId: String, today: LocalDate): Int {
        val days = done[habitId].orEmpty().toHashSet()
        var day = if (today.toString() in days) today else today.minusDays(1)
        var count = 0
        while (day.toString() in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun toggle(habitId: String, date: LocalDate): Habits {
        val key = date.toString()
        val days = done[habitId].orEmpty()
        val next = if (key in days) days - key else (days + key).sorted()
        val oldest = date.minusDays(KeepDays).toString()
        return copy(done = done + (habitId to next.filter { it >= oldest }))
    }

    fun add(habit: Habit): Habits = copy(habits = habits + habit)

    fun rename(habitId: String, name: String): Habits =
        copy(habits = habits.map { if (it.id == habitId) it.copy(name = name) else it })

    fun delete(habitId: String): Habits = copy(habits = habits.filterNot { it.id == habitId }, done = done - habitId)

    companion object {
        const val MaxHabits = 8
        const val KeepDays = 400L
    }
}
