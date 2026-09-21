// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.contacts.ContactShortcut
import com.nulis.launcher.blocks.contacts.ContactsBlockDefinition
import com.nulis.launcher.blocks.countdown.Countdown
import com.nulis.launcher.blocks.countdown.CountdownBlockDefinition
import com.nulis.launcher.blocks.quote.BundledQuotes
import com.nulis.launcher.blocks.quote.Quote
import com.nulis.launcher.blocks.quote.QuoteBlockDefinition
import com.nulis.launcher.blocks.quote.quoteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.nulis.launcher.blocks.glance.GlanceSettings
import com.nulis.launcher.blocks.glance.GlanceSlot
import com.nulis.launcher.blocks.glance.shortDuration
import org.junit.Test
import java.time.LocalDate

/**
 * The blocks that keep a list inside their own settings map all encode it the same way. The
 * separators have to survive anything a person can type, which is the whole reason they are
 * control characters.
 */
class BlockSettingsTest {

    private fun blank(type: String) = Block(id = "b", type = type, style = "x")

    @Test
    fun `countdowns round-trip and come back in date order`() {
        val items = listOf(
            Countdown("Later", LocalDate.of(2027, 1, 1)),
            Countdown("Sooner", LocalDate.of(2026, 6, 1)),
        )
        val block = CountdownBlockDefinition.withItems(blank("countdown"), items)
        val read = CountdownBlockDefinition.items(block)
        assertEquals(listOf("Sooner", "Later"), read.map { it.label })
        assertEquals(LocalDate.of(2026, 6, 1), read[0].date)
    }

    @Test
    fun `a countdown label with punctuation and emoji survives`() {
        val label = "Ana's 30th | trip, 100% 🎉 \"quoted\""
        val block = CountdownBlockDefinition.withItems(blank("countdown"), listOf(Countdown(label, LocalDate.of(2026, 12, 1))))
        assertEquals(label, CountdownBlockDefinition.items(block).single().label)
    }

    @Test
    fun `an empty countdown list reads back empty`() {
        val block = CountdownBlockDefinition.withItems(blank("countdown"), emptyList())
        assertTrue(CountdownBlockDefinition.items(block).isEmpty())
    }

    @Test
    fun `a corrupt countdown entry is dropped, not fatal`() {
        val block = blank("countdown").copy(settings = mapOf("items" to "broken-with-no-date"))
        assertTrue(CountdownBlockDefinition.items(block).isEmpty())
    }

    @Test
    fun `days until counts from today`() {
        val today = LocalDate.of(2026, 9, 21)
        assertEquals(0L, Countdown("x", today).daysFrom(today))
        assertEquals(10L, Countdown("x", today.plusDays(10)).daysFrom(today))
        assertEquals(-3L, Countdown("x", today.minusDays(3)).daysFrom(today))
    }

    @Test
    fun `pinned people round-trip`() {
        val people = listOf(ContactShortcut("Mum", "+370 600 00000"), ContactShortcut("Sam O'Neill", "555-0100"))
        val block = ContactsBlockDefinition.withItems(blank("contacts"), people)
        assertEquals(people, ContactsBlockDefinition.items(block))
    }

    @Test
    fun `initials take the first letter of up to two words`() {
        assertEquals("MP", ContactShortcut("Marija Petraitis", "1").initials)
        assertEquals("S", ContactShortcut("Sam", "1").initials)
        assertEquals("JS", ContactShortcut("jean-sebastien", "1").initials)
        assertEquals("?", ContactShortcut("", "1").initials)
    }

    @Test
    fun `a person with no number is not a shortcut`() {
        val block = blank("contacts").copy(settings = mapOf("people" to "Nameless"))
        assertTrue(ContactsBlockDefinition.items(block).isEmpty())
    }

    @Test
    fun `own quotes round-trip with their authors`() {
        val quotes = listOf(Quote("A line, with a comma", "Someone"), Quote("No attribution", ""))
        val block = QuoteBlockDefinition.withMine(blank("quote"), quotes)
        assertEquals(quotes, QuoteBlockDefinition.mine(block))
    }

    @Test
    fun `the quote of the day is the same all day and different tomorrow`() {
        val today = LocalDate.of(2026, 9, 21)
        assertEquals(quoteFor(today, BundledQuotes), quoteFor(today, BundledQuotes))
        assertNotEquals(quoteFor(today, BundledQuotes), quoteFor(today.plusDays(1), BundledQuotes))
    }

    @Test
    fun `shuffling steps to another quote`() {
        val today = LocalDate.of(2026, 9, 21)
        assertNotEquals(quoteFor(today, BundledQuotes, 0), quoteFor(today, BundledQuotes, 1))
    }

    @Test
    fun `an empty pool has no quote of the day`() {
        assertNull(quoteFor(LocalDate.now(), emptyList()))
    }

    @Test
    fun `a fortnight of days never repeats a quote twice running`() {
        val start = LocalDate.of(2026, 1, 1)
        val chosen = (0 until 14).map { quoteFor(start.plusDays(it.toLong()), BundledQuotes) }
        chosen.zipWithNext { a, b -> assertNotEquals(a, b) }
    }

    @Test
    fun `every bundled quote has a line and a name`() {
        assertTrue(BundledQuotes.size >= 25)
        BundledQuotes.forEach { quote ->
            assertTrue("empty quote", quote.text.isNotBlank())
            assertTrue("no attribution on: ${quote.text}", quote.author.isNotBlank())
        }
    }

    // ------------------------------------------------------------------ glance

    private fun glance(settings: Map<String, String>) =
        Block(id = "g", type = "glance", style = "line", settings = settings)

    @Test
    fun `a glance block keeps its slots in the order they were turned on`() {
        val chosen = listOf(GlanceSlot.BATTERY, GlanceSlot.TIME, GlanceSlot.MUSIC)
        val back = GlanceSettings.from(glance(GlanceSettings(slots = chosen).toMap()))
        assertEquals(chosen, back.slots)
    }

    @Test
    fun `a glance block that has never been configured takes the default slots`() {
        assertEquals(GlanceSettings().slots, GlanceSettings.from(glance(emptyMap())).slots)
    }

    @Test
    fun `a glance block emptied of every slot stays empty`() {
        val emptied = GlanceSettings(slots = emptyList()).toMap()
        assertTrue(GlanceSettings.from(glance(emptied)).slots.isEmpty())
    }

    @Test
    fun `a glance block ignores a slot id it does not know`() {
        val back = GlanceSettings.from(glance(mapOf(GlanceSettings.KEY_SLOTS to "time,weather,battery")))
        assertEquals(listOf(GlanceSlot.TIME, GlanceSlot.BATTERY), back.slots)
    }

    @Test
    fun `screen time reads as hours and minutes past the hour`() {
        assertEquals("45m", shortDuration(45))
        assertEquals("1h 35m", shortDuration(95))
        assertEquals("0m", shortDuration(0))
    }
}