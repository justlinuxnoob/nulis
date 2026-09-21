// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.apps.AppCategory
import com.nulis.launcher.backup.BackupError
import com.nulis.launcher.backup.BackupResult
import com.nulis.launcher.backup.encode
import com.nulis.launcher.backup.message
import com.nulis.launcher.backup.parseBackup
import com.nulis.launcher.backup.BackupPreferences
import com.nulis.launcher.backup.BackupWellbeing
import com.nulis.launcher.backup.NulisBackup
import com.nulis.launcher.backup.preview
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.focus.FocusSession
import com.nulis.launcher.blocks.writing.JournalEntry
import com.nulis.launcher.blocks.writing.Note
import com.nulis.launcher.blocks.writing.Task
import com.nulis.launcher.setups.SavedSetup
import com.nulis.launcher.ui.theme.ColorTheme
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup format, read and written by the same code the app uses. The parse cases matter most:
 * a file that cannot be read has to say why rather than crash a home screen.
 */
class BackupFormatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun sample() = NulisBackup(
        createdAt = 1_758_400_000_000,
        appVersion = "0.1.0",
        pages = mapOf(
            PageIds.HOME to PageLayout(
                PageIds.HOME,
                listOf(Block("b1", "clock", "display"), Block("b2", "date", "iso")),
                align = BlockAlign.CENTER,
            ),
        ),
        setups = listOf(SavedSetup(id = "user_1", name = "Mine", lookId = "clean", colorTheme = ColorTheme.WHITE)),
        preferences = BackupPreferences(lookId = "clean", textScale = 1.2f),
        gestures = mapOf("swipe_up" to "OPEN_DRAWER"),
        appNames = mapOf("a/A" to "Renamed"),
        hiddenApps = listOf("b/B"),
        categories = listOf(AppCategory("c1", "Work", listOf("a/A"))),
        notes = listOf(Note("n1", "A note", updatedAt = 1)),
        journal = listOf(JournalEntry("j1", "An entry", 1)),
        tasks = listOf(Task("t1", "A task", createdAt = 1)),
        wellbeing = BackupWellbeing(pausedPackages = listOf("com.example"), limits = mapOf("com.example" to 30)),
        focusSessions = listOf(FocusSession("2026-09-21", 25)),
    )

    @Test
    fun `a backup round-trips`() {
        val original = sample()
        val decoded = json.decodeFromString<NulisBackup>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `the preview counts what the file holds`() {
        val preview = sample().preview()
        assertEquals(1, preview.pages)
        assertEquals(2, preview.blocks)
        assertEquals(1, preview.setups)
        assertEquals(1, preview.gestures)
        assertEquals(1, preview.renamedApps)
        assertEquals(1, preview.hiddenApps)
        assertEquals(1, preview.categories)
        assertEquals(1, preview.notes)
        assertEquals(1, preview.journal)
        assertEquals(1, preview.tasks)
        assertEquals(1, preview.focusSessions)
        assertTrue(preview.hasPreferences)
        assertTrue(preview.hasWellbeing)
    }

    @Test
    fun `a file written by an older nulis still decodes`() {
        val old = """{"format":1,"createdAt":1,"pages":{},"notes":[{"id":"n","text":"x","updatedAt":2}]}"""
        val decoded = json.decodeFromString<NulisBackup>(old)
        assertEquals(1, decoded.notes.size)
        assertEquals(emptyList<Any>(), decoded.setups)
        assertEquals(25, decoded.focusMinutes)
    }

    @Test
    fun `an unknown field from a newer nulis is ignored`() {
        val future = """{"format":1,"createdAt":1,"notes":[],"somethingNew":{"a":1}}"""
        val decoded = json.decodeFromString<NulisBackup>(future)
        assertEquals(0, decoded.notes.size)
    }

    @Test
    fun `the version is stored so a person reading the file knows what wrote it`() {
        val text = sample().encode()
        assertTrue(text.contains("\"appVersion\""))
        assertTrue(text.contains("\"format\": 1"))
    }

    // ------------------------------------------------------------------ refusing a bad file

    @Test
    fun `what nulis wrote is what nulis reads`() {
        val result = parseBackup(sample().encode())
        assertTrue(result is BackupResult.Ok)
        assertEquals(sample().copy(createdAt = (result as BackupResult.Ok).backup.createdAt), result.backup)
    }

    @Test
    fun `a photo is not a backup`() {
        assertEquals(BackupError.NotJson, (parseBackup("\u0089PNG\r\n\u001a\n") as BackupResult.Failed).error)
    }

    @Test
    fun `an empty or blank file says so`() {
        assertEquals(BackupError.Empty, (parseBackup("") as BackupResult.Failed).error)
        assertEquals(BackupError.Empty, (parseBackup("   ") as BackupResult.Failed).error)
        assertEquals(BackupError.Empty, (parseBackup(null) as BackupResult.Failed).error)
    }

    @Test
    fun `an empty json object has nothing to restore`() {
        assertEquals(BackupError.Empty, (parseBackup("{}") as BackupResult.Failed).error)
    }

    @Test
    fun `a truncated file is refused rather than half-applied`() {
        val text = sample().encode()
        val result = parseBackup(text.substring(0, text.length / 2))
        assertTrue(result is BackupResult.Failed)
        assertTrue((result as BackupResult.Failed).error is BackupError.Unreadable)
    }

    @Test
    fun `a file from a newer nulis is refused with its format number`() {
        val result = parseBackup("{\"format\":99,\"notes\":[{\"id\":\"n\",\"text\":\"x\",\"updatedAt\":1}]}")
        assertEquals(BackupError.TooNew(99), (result as BackupResult.Failed).error)
    }

    @Test
    fun `garbage in a field is refused rather than guessed at`() {
        val result = parseBackup("{\"format\":1,\"notes\":\"not a list\"}")
        assertTrue((result as BackupResult.Failed).error is BackupError.Unreadable)
    }

    @Test
    fun `every refusal has a sentence a person can act on`() {
        listOf(
            BackupError.NotJson,
            BackupError.Empty,
            BackupError.TooNew(2),
            BackupError.Unreadable("x"),
        ).forEach { assertTrue(it.message().length > 10) }
    }

    @Test
    fun `a file with only settings in it is still worth restoring`() {
        val result = parseBackup("{\"format\":1,\"preferences\":{\"lookId\":\"clean\"}}")
        assertTrue(result is BackupResult.Ok)
    }

    /**
     * The rename that must not lose anything: what a backup calls "themes" is what Nulis now
     * calls a setup, and a file written before the rename has to restore into the same place.
     */
    @Test
    fun `a backup written when setups were called themes still restores them`() {
        val old = """{"format":1,"createdAt":1,"pages":{},"themes":[{"id":"t1","name":"Old","lookId":"dot","colorTheme":"BLACK"}]}"""
        val decoded = json.decodeFromString<NulisBackup>(old)
        assertEquals(1, decoded.setups.size)
        assertEquals("Old", decoded.setups.first().name)
        // And it goes back out under the same name, so the file stays readable both ways.
        assertTrue(json.encodeToString(decoded).contains("\"themes\""))
    }

    /** A backup written before pages could be added or removed says nothing about the order. */
    @Test
    fun `a backup with no page order still decodes`() {
        val old = """{"format":1,"createdAt":1,"pages":{}}"""
        val decoded = json.decodeFromString<NulisBackup>(old)
        assertEquals(emptyList<String>(), decoded.pageOrder)
        assertEquals(null, decoded.homePage)
    }

}
