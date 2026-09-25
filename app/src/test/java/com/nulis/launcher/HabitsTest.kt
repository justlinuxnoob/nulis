// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.backup.BackupResult
import com.nulis.launcher.backup.NulisBackup
import com.nulis.launcher.backup.encode
import com.nulis.launcher.backup.parseBackup
import com.nulis.launcher.backup.preview
import com.nulis.launcher.blocks.writing.Habit
import com.nulis.launcher.blocks.writing.Habits
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitsTest {

    private val today = LocalDate.of(2026, 3, 17)
    private val read = Habit("read", "Read", 0L)
    private fun ticked(vararg daysAgo: Long) = daysAgo.fold(Habits(listOf(read))) { h, d -> h.toggle("read", today.minusDays(d)) }

    @Test
    fun toggling_twice_unticks() {
        val once = Habits(listOf(read)).toggle("read", today)
        assertTrue(once.isDone("read", today))
        assertFalse(once.toggle("read", today).isDone("read", today))
    }

    @Test
    fun a_streak_counts_back_from_today() {
        assertEquals(3, ticked(0, 1, 2, 4).streak("read", today))
    }

    @Test
    fun a_streak_waits_for_today_until_midnight() {
        assertEquals(2, ticked(1, 2).streak("read", today))
        assertEquals(0, ticked(2, 3).streak("read", today))
    }

    @Test
    fun history_older_than_the_limit_is_dropped() {
        val old = Habits(listOf(read), mapOf("read" to listOf(today.minusDays(Habits.KeepDays + 5).toString())))
        assertEquals(listOf(today.toString()), old.toggle("read", today).done["read"])
    }

    @Test
    fun deleting_a_habit_takes_its_days_with_it() {
        val gone = ticked(0).delete("read")
        assertTrue(gone.habits.isEmpty())
        assertNull(gone.done["read"])
    }

    @Test
    fun a_backup_from_before_habits_reads_and_says_nothing_about_them() {
        val old = """{"format":1,"createdAt":1,"appVersion":"0.1.0","notes":[{"id":"n","text":"Groceries","updatedAt":1}],"tasks":[]}"""
        val parsed = parseBackup(old)
        assertTrue(parsed is BackupResult.Ok)
        val backup = (parsed as BackupResult.Ok).backup
        assertNull("an old file must not wipe the habits on the phone", backup.habits)
        assertNull(backup.preview().habits)
    }

    @Test
    fun habits_survive_a_round_trip() {
        val habits = ticked(0, 1)
        val parsed = parseBackup(NulisBackup(createdAt = 1, habits = habits).encode())
        assertEquals(habits, (parsed as BackupResult.Ok).backup.habits)
    }
}
