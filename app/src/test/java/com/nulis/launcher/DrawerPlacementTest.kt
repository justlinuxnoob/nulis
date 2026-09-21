// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.gestures.GestureAction
import com.nulis.launcher.gestures.GestureBinding
import com.nulis.launcher.gestures.GestureSettings
import com.nulis.launcher.gestures.GestureTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving the drawer changes two things that can lock somebody out of their own phone: what the
 * upward swipe does, and whether the escape hatch is still needed. Both are decided by plain
 * data, so both can be held here rather than found on a phone.
 */
class DrawerPlacementTest {

    @Test
    fun `swipe up still opens the drawer when the drawer lives there`() {
        val settings = GestureSettings(swipeUpIsFree = false)
        assertEquals(GestureAction.OPEN_DRAWER, settings.action(GestureTrigger.SWIPE_UP))
    }

    @Test
    fun `moving the drawer to a page hands the upward swipe back`() {
        val settings = GestureSettings(swipeUpIsFree = true, drawerIsAPage = true)
        assertEquals(GestureAction.NOTHING, settings.action(GestureTrigger.SWIPE_UP))
    }

    @Test
    fun `a swipe up you bound yourself keeps doing what you bound it to`() {
        val settings = GestureSettings(
            bindings = mapOf(GestureTrigger.SWIPE_UP to GestureBinding(GestureAction.NEW_NOTE)),
            swipeUpIsFree = true,
            drawerIsAPage = true,
        )
        assertEquals(GestureAction.NEW_NOTE, settings.action(GestureTrigger.SWIPE_UP))
    }

    @Test
    fun `a launcher whose gestures all do nothing keeps one way back in`() {
        val nothing = GestureTrigger.entries.associateWith { GestureBinding(GestureAction.NOTHING) }
        val settings = GestureSettings(bindings = nothing)
        assertTrue(settings.lockedOut)
        assertEquals(GestureAction.OPEN_DRAWER, settings.action(GestureTrigger.SWIPE_UP))
    }

    @Test
    fun `a drawer on a page is the way back in, so nothing has to be taken over`() {
        val nothing = GestureTrigger.entries.associateWith { GestureBinding(GestureAction.NOTHING) }
        val settings = GestureSettings(bindings = nothing, swipeUpIsFree = true, drawerIsAPage = true)
        assertFalse(settings.lockedOut)
        assertEquals(GestureAction.NOTHING, settings.action(GestureTrigger.SWIPE_UP))
    }

    @Test
    fun `turning the drawer off still leaves a way back in`() {
        val nothing = GestureTrigger.entries.associateWith { GestureBinding(GestureAction.NOTHING) }
        val settings = GestureSettings(bindings = nothing, swipeUpIsFree = true, drawerIsAPage = false)
        assertTrue(settings.lockedOut)
        assertEquals(GestureAction.OPEN_DRAWER, settings.action(GestureTrigger.SWIPE_UP))
    }

    @Test
    fun `every placement has an id that survives a round trip`() {
        DrawerPlacement.entries.forEach { placement ->
            assertEquals(placement, DrawerPlacement.byId(placement.id))
        }
        assertNull(DrawerPlacement.byId("something else"))
    }

    @Test
    fun `only the sheet keeps the upward swipe`() {
        assertFalse(DrawerPlacement.SWIPE_UP.freesSwipeUp)
        assertTrue(DrawerPlacement.LEFT_PAGE.freesSwipeUp)
        assertTrue(DrawerPlacement.RIGHT_PAGE.freesSwipeUp)
        assertTrue(DrawerPlacement.OFF.freesSwipeUp)
        assertFalse(DrawerPlacement.OFF.isPage)
    }

    @Test
    fun `a block aligned the way its page already is goes back to following the page`() {
        val layout = PageLayout(
            pageId = "home",
            blocks = listOf(
                Block("a", "clock", "display", align = BlockAlign.CENTER),
                Block("b", "clock", "display", align = BlockAlign.LEFT),
                Block("c", "clock", "display", align = null),
            ),
            align = BlockAlign.CENTER,
        ).normalized()
        assertNull("matching the page means no override", layout.blocks[0].align)
        assertEquals("a real override survives", BlockAlign.LEFT, layout.blocks[1].align)
        assertNull(layout.blocks[2].align)
        // What is drawn does not change, which is the whole point of doing it this way.
        assertEquals(BlockAlign.CENTER, layout.alignOf(layout.blocks[0]))
        assertEquals(BlockAlign.LEFT, layout.alignOf(layout.blocks[1]))
    }
}
