// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.blocks.battery.BatteryBlockDefinition
import com.nulis.launcher.blocks.glance.GlanceBlockDefinition
import com.nulis.launcher.blocks.greeting.GreetingBlockDefinition
import com.nulis.launcher.blocks.journal.JournalBlockDefinition
import com.nulis.launcher.blocks.music.MusicBlockDefinition
import com.nulis.launcher.blocks.notes.NotesBlockDefinition
import com.nulis.launcher.blocks.screentime.ScreenTimeBlockDefinition
import com.nulis.launcher.blocks.spacer.SpacerBlockDefinition
import com.nulis.launcher.blocks.steps.StepsBlockDefinition
import com.nulis.launcher.blocks.tasks.TasksBlockDefinition
import com.nulis.launcher.blocks.week.WeekBlockDefinition
import com.nulis.launcher.blocks.clock.ClockBlockDefinition
import com.nulis.launcher.blocks.calculator.CalculatorBlockDefinition
import com.nulis.launcher.blocks.calendar.CalendarBlockDefinition
import com.nulis.launcher.blocks.contacts.ContactsBlockDefinition
import com.nulis.launcher.blocks.focus.FocusBlockDefinition
import com.nulis.launcher.blocks.`fun`.DotCatBlockDefinition
import com.nulis.launcher.blocks.`fun`.HourglassBlockDefinition
import com.nulis.launcher.blocks.`fun`.LifeBlockDefinition
import com.nulis.launcher.blocks.`fun`.PetBlockDefinition
import com.nulis.launcher.blocks.photo.PhotoBlockDefinition
import com.nulis.launcher.widgets.WidgetBlockDefinition
import com.nulis.launcher.blocks.countdown.CountdownBlockDefinition
import com.nulis.launcher.blocks.quote.QuoteBlockDefinition
import com.nulis.launcher.blocks.date.DateBlockDefinition
import java.util.UUID

/** The single place where block types are registered. */
object BlockRegistry {

    val definitions: List<BlockDefinition> = listOf(
        ClockBlockDefinition,
        DateBlockDefinition,
        GlanceBlockDefinition,
        AppsBlockDefinition,
        BatteryBlockDefinition,
        MusicBlockDefinition,
        GreetingBlockDefinition,
        NotesBlockDefinition,
        JournalBlockDefinition,
        TasksBlockDefinition,
        StepsBlockDefinition,
        ScreenTimeBlockDefinition,
        WeekBlockDefinition,
        CountdownBlockDefinition,
        QuoteBlockDefinition,
        CalculatorBlockDefinition,
        CalendarBlockDefinition,
        ContactsBlockDefinition,
        PhotoBlockDefinition,
        WidgetBlockDefinition,
        FocusBlockDefinition,
        DotCatBlockDefinition,
        PetBlockDefinition,
        LifeBlockDefinition,
        HourglassBlockDefinition,
        SpacerBlockDefinition,
    )

    fun definition(type: String): BlockDefinition? = definitions.firstOrNull { it.type == type }

    /** A fresh block of [type] with that type's default style, size and settings. */
    fun newBlock(type: String): Block {
        val definition = requireNotNull(definition(type)) { "Unknown block type: $type" }
        return Block(
            id = UUID.randomUUID().toString(),
            type = type,
            style = definition.styles.first().id,
            size = definition.defaultSize,
            settings = definition.defaultSettings(),
        )
    }
}
