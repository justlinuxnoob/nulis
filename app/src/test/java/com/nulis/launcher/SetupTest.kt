// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.layout.LayoutPresets
import com.nulis.launcher.setups.ApplyOptions
import com.nulis.launcher.setups.SavedSetup
import com.nulis.launcher.setups.captureSetup
import com.nulis.launcher.setups.setupPages
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.Looks
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A saved setup is a whole phone under a name. Nulis ships none of them, so everything here is
 * about the two operations that matter: capturing what is on the phone now, and putting one back
 * without losing the things a setup has no business touching.
 */
class SetupTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun current(): Map<String, PageLayout?> = mapOf(
        PageIds.LEFT to PageLayout(
            PageIds.LEFT,
            listOf(
                Block("notes1", "notes", "list", rect = GridRect(0, 0, 6, 4)),
                Block("journal1", "journal", "latest", rect = GridRect(0, 4, 6, 3)),
            ),
        ),
        PageIds.HOME to PageLayout(
            PageIds.HOME,
            listOf(
                AppsBlockDefinition.withFavoriteIds(
                    Block("apps1", "apps", "grid", rect = GridRect(0, 6, 6, 4)),
                    listOf("a/A", "b/B"),
                ),
                Block("clock1", "clock", "display", rect = GridRect(0, 0, 6, 3)),
            ),
        ),
        PageIds.RIGHT to PageLayout(PageIds.RIGHT, emptyList()),
    )

    /** A setup built the way the app builds one: from what is on the phone right now. */
    private fun captured(name: String = "Mine"): SavedSetup = captureSetup(
        id = "user_1",
        name = name,
        lookId = Looks.Clean.id,
        colorTheme = ColorTheme.WHITE,
        customBackground = null,
        customInk = null,
        displayFont = Fonts.NotoSerif.id,
        bodyFont = null,
        textScale = 1.1f,
        uppercaseLabels = false,
        pages = current(),
        order = PageIds.all,
        homeId = PageIds.HOME,
    )

    /** A setup whose pages come from a shipped layout, for the apply cases. */
    private fun fromLayout(id: String): SavedSetup {
        val preset = LayoutPresets.byId(id)!!
        return SavedSetup(
            id = "setup_$id",
            name = preset.name,
            lookId = Looks.Dot.id,
            colorTheme = ColorTheme.BLACK,
            pages = preset.pages,
            pageOrder = preset.order,
            homeId = preset.homeId,
        )
    }

    @Test
    fun `saving the current setup keeps the pages and forgets the apps`() {
        val setup = captured()
        assertFalse(setup.builtIn)
        assertEquals("Mine", setup.name)
        assertEquals(3, setup.pages.size)
        assertEquals(PageIds.all, setup.order)
        val apps = setup.pages.values.flatMap { it.blocks }.first { it.type == "apps" }
        assertTrue("a saved setup must not carry someone's apps", AppsBlockDefinition.favoriteIds(apps).isEmpty())
        assertEquals(2, setup.page(PageIds.HOME).blocks.size)
    }

    @Test
    fun `saving keeps where every block was, not just which blocks there were`() {
        val saved = captured().page(PageIds.HOME).blocks.associateBy { it.type }
        assertEquals(GridRect(0, 0, 6, 3), saved.getValue("clock").rect)
        assertEquals(GridRect(0, 6, 6, 4), saved.getValue("apps").rect)
    }

    @Test
    fun `saving records the page order and which page is home`() {
        val setup = captureSetup(
            id = "u",
            name = "Five",
            lookId = Looks.Dot.id,
            colorTheme = ColorTheme.BLACK,
            customBackground = null,
            customInk = null,
            displayFont = null,
            bodyFont = null,
            textScale = 1f,
            uppercaseLabels = true,
            pages = current(),
            order = listOf(PageIds.RIGHT, PageIds.HOME, PageIds.LEFT),
            homeId = PageIds.RIGHT,
        )
        assertEquals(listOf(PageIds.RIGHT, PageIds.HOME, PageIds.LEFT), setup.order)
        assertEquals(PageIds.RIGHT, setup.config.homeId)
        assertEquals(PageIds.RIGHT, setup.cover.pageId)
    }

    @Test
    fun `applying a setup replaces every page it has, and only those`() {
        val setup = fromLayout("terminal")
        val pages = setupPages(setup, current(), ApplyOptions())
        assertEquals(setup.order.toSet(), pages.keys)
        pages.forEach { (id, layout) -> assertEquals(id, layout.pageId) }
        assertTrue(pages.getValue(PageIds.HOME).blocks.any { it.type == "clock" })
    }

    @Test
    fun `a one-page setup leaves one page`() {
        val setup = fromLayout("just_a_clock")
        assertEquals(1, setupPages(setup, current(), ApplyOptions()).size)
    }

    @Test
    fun `block ids are fresh so two pages can never collide`() {
        val pages = setupPages(fromLayout("compact_dashboard"), current(), ApplyOptions())
        val ids = pages.values.flatMap { it.blocks }.map { it.id }
        assertEquals("ids are unique across all pages", ids.size, ids.toSet().size)
        assertFalse("none of the old ids survive", ids.contains("apps1"))
    }

    @Test
    fun `keeping apps moves the favourites into the setup's apps block`() {
        val pages = setupPages(fromLayout("minimal"), current(), ApplyOptions(keepApps = true))
        val apps = pages.values.flatMap { it.blocks }.first { it.type == "apps" }
        assertEquals(listOf("a/A", "b/B"), AppsBlockDefinition.favoriteIds(apps))
    }

    @Test
    fun `not keeping apps leaves the setup's apps block empty`() {
        val pages = setupPages(fromLayout("minimal"), current(), ApplyOptions(keepApps = false))
        val apps = pages.values.flatMap { it.blocks }.first { it.type == "apps" }
        assertTrue(AppsBlockDefinition.favoriteIds(apps).isEmpty())
    }

    @Test
    fun `only the first apps block gets the favourites`() {
        val base = fromLayout("minimal")
        val setup = base.copy(
            pages = base.pages.mapValues { (_, page) ->
                val spot = page.firstFit(com.nulis.launcher.blocks.GridSpan(2, 1))
                if (spot == null) page else page.copy(blocks = page.blocks + Block("extra", "apps", "list", rect = spot))
            },
        )
        val pages = setupPages(setup, current(), ApplyOptions(keepApps = true))
        val withFavorites = pages.values.flatMap { it.blocks }
            .filter { it.type == "apps" && AppsBlockDefinition.favoriteIds(it).isNotEmpty() }
        assertEquals(1, withFavorites.size)
    }

    @Test
    fun `keeping writing blocks carries across only what the setup leaves out`() {
        // Just a clock has no writing blocks at all, so both should be carried.
        val pages = setupPages(fromLayout("just_a_clock"), current(), ApplyOptions(keepWritingBlocks = true))
        val types = pages.values.flatMap { it.blocks }.map { it.type }
        assertTrue(types.contains("notes"))
        assertTrue(types.contains("journal"))
    }

    @Test
    fun `a carried writing block lands somewhere real`() {
        val pages = setupPages(fromLayout("just_a_clock"), current(), ApplyOptions(keepWritingBlocks = true))
        pages.values.forEach { page ->
            page.blocks.forEach { assertTrue("${it.type} is off the grid", it.area.fitsGrid) }
            page.blocks.forEachIndexed { index, one ->
                page.blocks.drop(index + 1).forEach { other ->
                    assertFalse("${one.type} sits on ${other.type}", one.area.overlaps(other.area))
                }
            }
        }
    }

    @Test
    fun `a setup that already has a writing block does not get a second one`() {
        val pages = setupPages(fromLayout("writer"), current(), ApplyOptions(keepWritingBlocks = true))
        val notes = pages.values.flatMap { it.blocks }.count { it.type == "notes" }
        assertEquals(1, notes)
    }

    @Test
    fun `not keeping writing blocks drops them`() {
        val pages = setupPages(fromLayout("just_a_clock"), current(), ApplyOptions(keepWritingBlocks = false))
        assertTrue(pages.values.flatMap { it.blocks }.none { it.type == "notes" })
    }

    @Test
    fun `a setup round-trips through json`() {
        val setup = captured()
        assertEquals(setup, json.decodeFromString<SavedSetup>(json.encodeToString(setup)))
    }

    /** A theme saved when these were called themes is a setup now, and still reads. */
    @Test
    fun `a theme saved by an older version still decodes as a setup`() {
        val old = """{"id":"x","name":"Old","lookId":"dot","colorTheme":"BLACK","builtIn":true}"""
        val decoded = json.decodeFromString<SavedSetup>(old)
        assertEquals("Old", decoded.name)
        assertEquals(1f, decoded.textScale, 0.0001f)
        assertTrue(decoded.pages.isEmpty())
        assertEquals(PageIds.HOME, decoded.cover.pageId)
        assertNotNull(Looks.byId(decoded.lookId))
    }
}
