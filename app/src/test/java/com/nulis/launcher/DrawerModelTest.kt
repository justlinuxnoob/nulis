// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.apps.AppCategory
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.AppUsage
import com.nulis.launcher.apps.Categories
import com.nulis.launcher.apps.autoHiddenApps
import com.nulis.launcher.blocks.writing.Note
import com.nulis.launcher.blocks.writing.Task
import com.nulis.launcher.blocks.writing.WritingState
import com.nulis.launcher.drawer.CategoryDisplay
import com.nulis.launcher.drawer.DrawerEntry
import com.nulis.launcher.drawer.DrawerInput
import com.nulis.launcher.drawer.SearchHit
import com.nulis.launcher.drawer.SettingsTarget
import com.nulis.launcher.drawer.buildDrawer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerModelTest {

    private fun app(label: String, pkg: String = label.lowercase()) =
        AppInfo(label = label, packageName = pkg, activityName = "Main")

    private val apps = listOf(
        app("Almanac"),
        app("Bank"),
        app("Camera"),
        app("Calendar"),
        app("Signal"),
        app("Zoom"),
    )

    private fun entries(input: DrawerInput) = buildDrawer(input)

    private fun appNames(input: DrawerInput) =
        entries(input).filterIsInstance<DrawerEntry.App>().map { it.app.label }

    @Test
    fun `browsing groups by letter`() {
        val result = entries(DrawerInput(apps = apps, query = ""))
        val letters = result.filterIsInstance<DrawerEntry.LetterHeader>().map { it.letter }
        assertEquals(listOf("A", "B", "C", "S", "Z"), letters)
        assertEquals(apps.size, result.filterIsInstance<DrawerEntry.App>().size)
    }

    @Test
    fun `hidden apps are out of the list but in the search`() {
        val hidden = setOf(apps[0].id)
        assertFalse(appNames(DrawerInput(apps = apps, query = "", hiddenIds = hidden)).contains("Almanac"))
        assertTrue(appNames(DrawerInput(apps = apps, query = "alma", hiddenIds = hidden)).contains("Almanac"))
    }

    @Test
    fun `turning off hidden search really hides them`() {
        val hidden = setOf(apps[0].id)
        val names = appNames(DrawerInput(apps = apps, query = "alma", hiddenIds = hidden, searchHidden = false))
        assertTrue(names.isEmpty())
    }

    @Test
    fun `an app rested for going unused is out of the list but always searchable`() {
        val rested = setOf(apps[5].id)
        assertFalse(appNames(DrawerInput(apps = apps, query = "", autoHiddenIds = rested)).contains("Zoom"))
        assertTrue(appNames(DrawerInput(apps = apps, query = "zoo", autoHiddenIds = rested)).contains("Zoom"))
        // Even with "find hidden apps in search" off, because resting is Nulis's idea, not yours.
        assertTrue(appNames(DrawerInput(apps = apps, query = "zoo", autoHiddenIds = rested, searchHidden = false)).contains("Zoom"))
    }

    @Test
    fun `a category becomes a section and its apps leave the alphabet`() {
        val categories = Categories(listOf(AppCategory("c1", "Money", listOf(apps[1].id))))
        val result = entries(DrawerInput(apps = apps, query = "", categories = categories))
        val headers = result.filterIsInstance<DrawerEntry.CategoryHeader>()
        assertEquals(1, headers.size)
        assertEquals("Money", headers[0].category.name)
        assertEquals(1, headers[0].count)
        // "B" no longer has a letter header, because Bank is in the group above.
        assertFalse(result.filterIsInstance<DrawerEntry.LetterHeader>().map { it.letter }.contains("B"))
        // The app is still shown exactly once.
        assertEquals(1, result.filterIsInstance<DrawerEntry.App>().count { it.app.label == "Bank" })
    }

    @Test
    fun `an empty category is not drawn at all`() {
        val categories = Categories(listOf(AppCategory("c1", "Empty", emptyList())))
        val result = entries(DrawerInput(apps = apps, query = "", categories = categories))
        assertTrue(result.filterIsInstance<DrawerEntry.CategoryHeader>().isEmpty())
    }

    @Test
    fun `a collapsed group hides its apps only in group mode`() {
        val categories = Categories(listOf(AppCategory("c1", "Money", listOf(apps[1].id))))
        val collapsedInGroups = entries(
            DrawerInput(apps = apps, query = "", categories = categories, display = CategoryDisplay.GROUPS, collapsed = setOf("c1")),
        )
        assertTrue(collapsedInGroups.filterIsInstance<DrawerEntry.App>().none { it.app.label == "Bank" })
        assertTrue(collapsedInGroups.filterIsInstance<DrawerEntry.CategoryHeader>().single().collapsed)

        // Sections mode has nothing to collapse, so the same state still shows the app.
        val sections = entries(
            DrawerInput(apps = apps, query = "", categories = categories, display = CategoryDisplay.SECTIONS, collapsed = setOf("c1")),
        )
        assertTrue(sections.filterIsInstance<DrawerEntry.App>().any { it.app.label == "Bank" })
    }

    @Test
    fun `the recents row is first, and only when there is one`() {
        assertTrue(entries(DrawerInput(apps = apps, query = "")).none { it is DrawerEntry.Recents })
        val withRecents = entries(DrawerInput(apps = apps, query = "", recents = listOf(apps[0])))
        assertTrue(withRecents.first() is DrawerEntry.Recents)
    }

    @Test
    fun `recents never appear while searching`() {
        val result = entries(DrawerInput(apps = apps, query = "ca", recents = listOf(apps[0])))
        assertTrue(result.none { it is DrawerEntry.Recents })
    }

    @Test
    fun `a name that starts with the query comes before one that merely contains it`() {
        val list = listOf(app("Podcasts"), app("Camera"))
        assertEquals(listOf("Camera", "Podcasts"), appNames(DrawerInput(apps = list, query = "ca")))
    }

    @Test
    fun `a sum in the search field is answered above everything`() {
        val result = entries(DrawerInput(apps = apps, query = "12*12"))
        val first = result.first()
        assertTrue(first is DrawerEntry.Hit && first.hit is SearchHit.Math)
        assertEquals("144", ((first as DrawerEntry.Hit).hit as SearchHit.Math).result)
    }

    @Test
    fun `a unit conversion is answered too`() {
        val result = entries(DrawerInput(apps = apps, query = "1 km in m"))
        val math = result.filterIsInstance<DrawerEntry.Hit>().map { it.hit }.filterIsInstance<SearchHit.Math>()
        assertEquals("1,000", math.single().result)
    }

    @Test
    fun `an app name is never treated as a sum`() {
        val result = entries(DrawerInput(apps = apps, query = "signal"))
        assertTrue(result.filterIsInstance<DrawerEntry.Hit>().map { it.hit }.none { it is SearchHit.Math })
        assertEquals(listOf("Signal"), appNames(DrawerInput(apps = apps, query = "signal")))
    }

    @Test
    fun `notes and tasks are found, under a heading, after the apps`() {
        val writing = WritingState(
            notes = listOf(Note(id = "n1", text = "Camera settings for the trip", updatedAt = 0)),
            tasks = listOf(Task(id = "t1", text = "Buy a camera bag", createdAt = 0)),
        )
        val result = entries(DrawerInput(apps = apps, query = "camera", writing = writing))
        val appIndex = result.indexOfFirst { it is DrawerEntry.App }
        val sectionIndex = result.indexOfFirst { it is DrawerEntry.Section }
        assertTrue("apps come first", appIndex in 0 until sectionIndex)
        val hits = result.filterIsInstance<DrawerEntry.Hit>().map { it.hit }
        assertTrue(hits.any { it is SearchHit.Note && it.id == "n1" })
        assertTrue(hits.any { it is SearchHit.Task && it.id == "t1" })
    }

    @Test
    fun `a note whose snippet would only repeat its title carries no snippet`() {
        val short = WritingState(notes = listOf(Note(id = "n1", text = "Camera bag", updatedAt = 0)))
        val long = WritingState(
            notes = listOf(
                Note(id = "n2", text = "Things to pack\nCamera bag, the small one, and the charger", updatedAt = 0),
            ),
        )
        val shortHit = entries(DrawerInput(apps = apps, query = "camera", writing = short))
            .filterIsInstance<DrawerEntry.Hit>().map { it.hit }
            .filterIsInstance<SearchHit.Note>().single()
        assertEquals("Camera bag", shortHit.title)
        assertNull("a one-line note says it once", shortHit.snippet)

        val longHit = entries(DrawerInput(apps = apps, query = "camera", writing = long))
            .filterIsInstance<DrawerEntry.Hit>().map { it.hit }
            .filterIsInstance<SearchHit.Note>().single()
        assertNotNull("a longer note still shows where it matched", longHit.snippet)
        assertNotEquals(longHit.title, longHit.snippet)
    }

    @Test
    fun `a finished task is not offered`() {
        val writing = WritingState(tasks = listOf(Task(id = "t1", text = "Buy a camera bag", done = true, createdAt = 0)))
        val hits = entries(DrawerInput(apps = apps, query = "camera", writing = writing))
            .filterIsInstance<DrawerEntry.Hit>().map { it.hit }
        assertTrue(hits.none { it is SearchHit.Task })
    }

    @Test
    fun `writing search can be turned off`() {
        val writing = WritingState(notes = listOf(Note(id = "n1", text = "Camera settings", updatedAt = 0)))
        val hits = entries(DrawerInput(apps = apps, query = "camera", writing = writing, searchNotes = false))
            .filterIsInstance<DrawerEntry.Hit>().map { it.hit }
        assertTrue(hits.none { it is SearchHit.Note })
    }

    @Test
    fun `settings sections are findable by name and by keyword`() {
        fun settingsHits(query: String) = entries(DrawerInput(apps = apps, query = query))
            .filterIsInstance<DrawerEntry.Hit>().map { it.hit }.filterIsInstance<SearchHit.Setting>()

        // "Theme" is not a concept any more, but somebody typing it still has to land somewhere.
        assertTrue(settingsHits("theme").any { it.target == SettingsTarget.ARRANGEMENT })
        assertTrue(settingsHits("layout").any { it.target == SettingsTarget.ARRANGEMENT })
        assertTrue(settingsHits("pages").any { it.target == SettingsTarget.PAGE })
        assertTrue(settingsHits("font").any { it.target == SettingsTarget.TYPE })
        assertTrue(settingsHits("swipe").any { it.target == SettingsTarget.GESTURES })
        assertTrue(settingsHits("folder").any { it.target == SettingsTarget.CATEGORIES })
        assertTrue(settingsHits("backup").any { it.target == SettingsTarget.BACKUP })
    }

    @Test
    fun `settings search can be turned off`() {
        val hits = entries(DrawerInput(apps = apps, query = "theme", searchSettings = false))
            .filterIsInstance<DrawerEntry.Hit>().map { it.hit }
        assertTrue(hits.none { it is SearchHit.Setting })
    }

    @Test
    fun `every row has a distinct key so the list never reuses one`() {
        val categories = Categories(listOf(AppCategory("c1", "Money", listOf(apps[1].id))))
        val keys = entries(DrawerInput(apps = apps, query = "", categories = categories, recents = listOf(apps[0]))).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    // ------------------------------------------------------------------ resting

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `resting picks apps past the cutoff and nothing else`() {
        val now = 1_000L * day
        val usage = AppUsage(
            granted = true,
            lastUsed = mapOf(
                "alarmy" to now - 10 * day,
                "zoom" to now - 200 * day,
                "bank" to now - 31 * day,
            ),
        )
        val rested = autoHiddenApps(apps, usage, days = 30, now = now)
        assertEquals(setOf("zoom/Main", "bank/Main"), rested)
    }

    @Test
    fun `an app with no record is left alone`() {
        val now = 1_000L * day
        val usage = AppUsage(granted = true, lastUsed = mapOf("zoom" to now - 200 * day))
        val rested = autoHiddenApps(apps, usage, days = 30, now = now)
        assertEquals(setOf("zoom/Main"), rested)
    }

    @Test
    fun `nothing is rested without the permission or with the setting off`() {
        val now = 1_000L * day
        val usage = AppUsage(granted = true, lastUsed = mapOf("zoom" to now - 200 * day))
        assertTrue(autoHiddenApps(apps, usage, days = 0, now = now).isEmpty())
        assertTrue(autoHiddenApps(apps, usage.copy(granted = false), days = 30, now = now).isEmpty())
    }

    @Test
    fun `favourites and grouped apps are never rested`() {
        val now = 1_000L * day
        val usage = AppUsage(granted = true, lastUsed = mapOf("zoom" to now - 200 * day))
        assertTrue(autoHiddenApps(apps, usage, days = 30, now = now, keepIds = setOf("zoom/Main")).isEmpty())
    }

    @Test
    fun `nulis settings is never rested`() {
        val settings = AppInfo("Nulis Settings", "com.nulis.launcher", AppInfo.NULIS_SETTINGS)
        val now = 1_000L * day
        val usage = AppUsage(granted = true, lastUsed = mapOf("com.nulis.launcher" to now - 200 * day))
        assertTrue(autoHiddenApps(listOf(settings), usage, days = 30, now = now).isEmpty())
    }
}
