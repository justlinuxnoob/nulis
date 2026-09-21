// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.glance

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One thing a Glance block can show. A slot with nothing to say is simply left out of the row. */
enum class GlanceSlot(val id: String, val label: String, val selfEvident: Boolean) {
    TIME("time", "Time", true),
    DATE("date", "Date", true),
    BATTERY("battery", "Battery", true),
    // A bare 8,431 or 07:00 says nothing on a line of other numbers, so these carry their word.
    STEPS("steps", "Steps", false),
    SCREEN_TIME("screen", "Screen time", false),
    ALARM("alarm", "Next alarm", false),
    MUSIC("music", "Now playing", false),
    ;

    companion object {
        fun byId(id: String): GlanceSlot? = entries.firstOrNull { it.id == id }
    }
}

/** What a slot is showing right now: the reading itself, and the word that names it. */
data class GlanceReading(val slot: GlanceSlot, val value: String, val caption: String) {
    /**
     * The reading on its own, for the styles that have no room for a caption underneath. A time
     * or a percentage explains itself; a step count next to a screen-time total does not, so
     * those carry their word along.
     */
    val inline: String get() = if (slot.selfEvident) value else "$value ${caption.lowercase()}"
}

/**
 * What a Glance block remembers: which slots it shows, in the order they were turned on, and
 * whether the clock reads 24 hours.
 */
data class GlanceSettings(
    val slots: List<GlanceSlot> = listOf(GlanceSlot.TIME, GlanceSlot.DATE, GlanceSlot.BATTERY),
    val hour24: Boolean = true,
) {
    fun toMap(): Map<String, String> = mapOf(
        KEY_SLOTS to slots.joinToString(",") { it.id },
        KEY_24 to hour24.toString(),
    )

    companion object {
        const val KEY_SLOTS = "slots"
        const val KEY_24 = "hour24"

        fun from(block: Block): GlanceSettings {
            val raw = block.settings[KEY_SLOTS]
            val slots = raw?.split(",")?.mapNotNull { GlanceSlot.byId(it.trim()) }?.distinct()
            return GlanceSettings(
                // A block that has been emptied of every slot shows nothing, which is a thing
                // somebody may want; only a block that has never been configured takes the default.
                slots = if (raw == null) GlanceSettings().slots else slots.orEmpty(),
                hour24 = block.settings[KEY_24]?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}

/**
 * The readings a block has to show, in its own slot order. A slot whose permission is off, or
 * that has nothing to report - no alarm set, nothing playing - returns null and takes no room,
 * so the line never carries a dash where a number should be.
 */
fun GlanceSettings.readings(context: BlockContext): List<GlanceReading> =
    slots.mapNotNull { it.read(this, context) }

private fun GlanceSlot.read(settings: GlanceSettings, context: BlockContext): GlanceReading? = when (this) {
    GlanceSlot.TIME -> GlanceReading(
        this,
        context.time.format(DateTimeFormatter.ofPattern(if (settings.hour24) "HH:mm" else "h:mm a", Locale.getDefault())),
        "Time",
    )
    GlanceSlot.DATE -> GlanceReading(
        this,
        context.time.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
        "Today",
    )
    GlanceSlot.BATTERY -> GlanceReading(
        this,
        "${context.battery.percent}%",
        if (context.battery.charging) "Charging" else "Battery",
    )
    GlanceSlot.STEPS -> context.steps.takeIf { it.granted }?.let {
        GlanceReading(this, groupThousands(it.today), "Steps")
    }
    GlanceSlot.SCREEN_TIME -> context.screenTime.takeIf { it.granted }?.let {
        GlanceReading(this, shortDuration(it.totalMinutes), "Screen")
    }
    GlanceSlot.ALARM -> context.nextAlarm?.let {
        GlanceReading(
            this,
            it.format(DateTimeFormatter.ofPattern(if (settings.hour24) "HH:mm" else "h:mm a", Locale.getDefault())),
            if (it.toLocalDate() == context.time.toLocalDate()) "Alarm" else "Alarm " + it.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
        )
    }
    GlanceSlot.MUSIC -> context.music.takeIf { it.granted && it.title.isNotBlank() }?.let {
        GlanceReading(this, it.title, it.artist.ifBlank { "Playing" })
    }
}

/** 8431 -> "8,431", in the reader's own grouping. */
fun groupThousands(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

/** 95 minutes -> "1h 35m"; under an hour stays in minutes. */
fun shortDuration(minutes: Int): String {
    val hours = minutes / 60
    return if (hours <= 0) "${minutes}m" else "${hours}h ${minutes % 60}m"
}
