// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.nameOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pages are a list with a mark on one of them. Everything that can go wrong here goes wrong
 * quietly - a home id naming a page that was deleted, a list that has emptied itself - and ends
 * with a launcher showing a black screen with no way out, so [PagesConfig.sane] is the guard and
 * this is what it promises.
 */
class PagesTest {

    @Test
    fun `a fresh install has the three pages Nulis always had, home in the middle`() {
        val config = PagesConfig()
        assertEquals(listOf(PageIds.LEFT, PageIds.HOME, PageIds.RIGHT), config.ids)
        assertEquals(1, config.homeIndex)
    }

    @Test
    fun `a home id naming a page that is gone moves to the middle of what is left`() {
        val config = PagesConfig(listOf("a", "b", "c"), homeId = "vanished").sane()
        assertEquals("b", config.homeId)
    }

    @Test
    fun `an empty page list is never allowed`() {
        val config = PagesConfig(emptyList(), homeId = "nothing").sane()
        assertEquals(1, config.ids.size)
        assertTrue(config.homeId in config.ids)
    }

    @Test
    fun `duplicates are dropped and the list is capped`() {
        val config = PagesConfig(listOf("a", "a", "b", "c", "d", "e", "f"), homeId = "a").sane()
        assertEquals(listOf("a", "b", "c", "d", "e"), config.ids)
        assertEquals(PagesConfig.MAX, config.ids.size)
    }

    @Test
    fun `five is the most, and one is the fewest`() {
        assertFalse(PagesConfig(List(5) { "p$it" }, "p0").canAdd)
        assertTrue(PagesConfig(List(4) { "p$it" }, "p0").canAdd)
        assertFalse(PagesConfig(listOf("only"), "only").canRemove)
        assertTrue(PagesConfig(listOf("a", "b"), "a").canRemove)
    }

    @Test
    fun `pages are named by where they sit relative to home`() {
        val config = PagesConfig(listOf("p0", PageIds.LEFT, PageIds.HOME, PageIds.RIGHT, "p4"), PageIds.HOME)
        assertEquals("Home", config.nameOf(PageIds.HOME))
        assertEquals("Left", config.nameOf(PageIds.LEFT))
        assertEquals("Right", config.nameOf(PageIds.RIGHT))
        assertEquals("2 left", config.nameOf("p0"))
        assertEquals("2 right", config.nameOf("p4"))
    }

    @Test
    fun `the page marked home is called home wherever it sits`() {
        val config = PagesConfig(listOf(PageIds.LEFT, PageIds.HOME, PageIds.RIGHT), PageIds.RIGHT)
        assertEquals("Home", config.nameOf(PageIds.RIGHT))
        assertEquals("Left", config.nameOf(PageIds.HOME))
        assertEquals("2 left", config.nameOf(PageIds.LEFT))
    }
}
