// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.home.Hint
import com.nulis.launcher.home.nextHomeHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintsTest {

    private fun next(
        learned: Set<Hint> = emptySet(),
        placement: DrawerPlacement = DrawerPlacement.SWIPE_UP,
        swipeUpOpensDrawer: Boolean = true,
        longPressEdits: Boolean = true,
        pages: Int = 2,
    ) = nextHomeHint(learned.map { it.key }.toSet(), placement, swipeUpOpensDrawer, longPressEdits, pages)

    @Test
    fun hints_come_one_at_a_time_in_order_of_importance() {
        assertEquals(Hint.DRAWER, next()?.hint)
        assertEquals(Hint.EDIT, next(setOf(Hint.DRAWER))?.hint)
        assertEquals(Hint.PAGES, next(setOf(Hint.DRAWER, Hint.EDIT))?.hint)
        assertNull(next(setOf(Hint.DRAWER, Hint.EDIT, Hint.PAGES)))
    }

    @Test
    fun a_drawer_that_is_off_or_rebound_is_not_advertised() {
        assertEquals(Hint.EDIT, next(placement = DrawerPlacement.OFF)?.hint)
        assertEquals(Hint.EDIT, next(swipeUpOpensDrawer = false)?.hint)
    }

    @Test
    fun a_drawer_on_a_page_is_pointed_at_sideways() {
        assertEquals("All your apps are on the page to the left", next(placement = DrawerPlacement.LEFT_PAGE)?.text)
    }

    @Test
    fun one_page_has_no_pages_hint() {
        assertNull(next(setOf(Hint.DRAWER, Hint.EDIT), pages = 1))
    }

    @Test
    fun a_rebound_long_press_still_leads_to_the_editor_through_a_block() {
        assertEquals("Hold a block to change this page", next(setOf(Hint.DRAWER), longPressEdits = false)?.text)
    }
}
