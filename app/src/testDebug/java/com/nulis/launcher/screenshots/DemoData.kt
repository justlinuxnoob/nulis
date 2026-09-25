// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.battery.BatteryState
import com.nulis.launcher.blocks.calendar.CalendarEvent
import com.nulis.launcher.blocks.calendar.CalendarState
import com.nulis.launcher.blocks.focus.FocusState
import com.nulis.launcher.blocks.music.MusicState
import com.nulis.launcher.blocks.screentime.AppUsage
import com.nulis.launcher.blocks.screentime.ScreenTimeState
import com.nulis.launcher.blocks.steps.DayCount
import com.nulis.launcher.blocks.steps.StepsState
import com.nulis.launcher.blocks.writing.Habit
import com.nulis.launcher.blocks.writing.Habits
import com.nulis.launcher.blocks.writing.JournalEntry
import com.nulis.launcher.blocks.writing.Note
import com.nulis.launcher.blocks.writing.Task
import com.nulis.launcher.blocks.writing.WritingState
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A believable, nobody-in-particular day for block previews: a quarter to ten on a Tuesday, a
 * walk half done, a song playing, two things on the list.
 */
object DemoData {
    val now: LocalDateTime = LocalDateTime.of(2026, 3, 17, 9, 41)
    private val millis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val apps: List<AppInfo> = DemoPhone.apps.map { AppInfo(it.label, it.packageName, "${it.packageName}.Main") }
    val homeApps: List<AppInfo> = listOf("Phone", "Messages", "Camera", "Browser", "Photos", "Clock")
        .map { label -> apps.first { it.label == label } }

    /** A phone on its first day: apps, and nothing else granted, written or playing. */
    val emptyContext: BlockContext = BlockContext(
        time = now,
        apps = apps,
        homeApps = homeApps,
        onLaunchApp = { _, _ -> },
    )

    private fun demoHabits(): Habits {
        val habits = listOf(Habit("h1", "Read", millis), Habit("h2", "Walk", millis), Habit("h3", "Stretch", millis))
        val today = now.toLocalDate()
        fun days(vararg back: Long) = back.map { today.minusDays(it).toString() }
        return Habits(
            habits = habits,
            done = mapOf("h1" to days(0, 1, 2, 3, 4, 6), "h2" to days(1, 2, 5), "h3" to days(0, 2, 3)),
        )
    }

    private fun pkg(label: String) = apps.first { it.label == label }.packageName

    val context: BlockContext = BlockContext(
        time = now,
        apps = apps,
        homeApps = homeApps,
        onLaunchApp = { _, _ -> },
        battery = BatteryState(percent = 76),
        music = MusicState(
            granted = true,
            title = "Midnight Ferry",
            artist = "Halcyon Pines",
            album = "Harbour Lights",
            playing = true,
            playerPackage = pkg("Music"),
            playerLabel = "Music",
            canSkipPrevious = true,
            canSkipNext = true,
        ),
        writing = WritingState(
            notes = listOf(
                Note("n1", "Groceries\nOats, lemons, coffee, basil", pinned = true, updatedAt = millis),
                Note("n2", "Ideas for the weekend\nCoast walk if it is dry", updatedAt = millis - 86_400_000),
            ),
            journal = listOf(JournalEntry("j1", "Slow morning. Finished the book on the train.", millis - 3_600_000)),
            tasks = listOf(
                Task("t1", "Call the dentist", createdAt = millis),
                Task("t2", "Return library books", createdAt = millis),
                Task("t3", "Water the plants", done = true, createdAt = millis),
            ),
            habits = demoHabits(),
        ),
        screenTime = ScreenTimeState(
            granted = true,
            totalMinutes = 94,
            apps = listOf(
                AppUsage(pkg("Messages"), "Messages", 31),
                AppUsage(pkg("Browser"), "Browser", 24),
                AppUsage(pkg("Maps"), "Maps", 18),
                AppUsage(pkg("Music"), "Music", 12),
                AppUsage("", AppUsage.OTHER, 9),
            ),
        ),
        steps = StepsState(
            granted = true,
            today = 5_240,
            history = (6 downTo 1).map { back ->
                DayCount(now.toLocalDate().minusDays(back.toLong()), listOf(7_900, 10_450, 6_120, 8_830, 4_300, 9_210)[6 - back])
            } + DayCount(now.toLocalDate(), 5_240),
        ),
        calendar = CalendarState(
            granted = true,
            events = listOf(
                CalendarEvent(1, "Standup", now.withHour(10).withMinute(0), now.withHour(10).withMinute(15), false, 0xFF4A7AB0.toInt()),
                CalendarEvent(2, "Lunch with Sam", now.withHour(12).withMinute(30), now.withHour(13).withMinute(30), false, 0xFF3A9A60.toInt()),
                CalendarEvent(3, "Yoga", now.plusDays(1).withHour(18).withMinute(0), now.plusDays(1).withHour(19).withMinute(0), false, 0xFFC03860.toInt()),
            ),
        ),
        focus = FocusState(todayMinutes = 50, weekMinutes = 185, todaySessions = 2),
        nextAlarm = now.plusDays(1).withHour(7).withMinute(0),
    )
}
