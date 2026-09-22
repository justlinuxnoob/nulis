// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.widgets.WidgetBlockDefinition
import com.nulis.launcher.widgets.WidgetHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which widget ids are still spoken for.
 *
 * A widget id is the one thing a block owns that lives outside Nulis: the system holds it, and
 * an id given back cannot be taken again without sending the user through the picker. So the
 * rule that decides what may be released is worth pinning down on its own - the cost of getting
 * it wrong is somebody's widget disappearing, and the rule is the only thing standing between
 * the two outcomes.
 */
class WidgetIdsTest {

    private var n = 0

    private fun widget(id: Int?, at: Int = 0): Block = Block(
        id = "w${n++}",
        type = WidgetBlockDefinition.type,
        style = "plain",
        settings = if (id == null) emptyMap() else mapOf("widget_id" to id.toString()),
        rect = GridRect(0, at, 6, 2),
    )

    private fun page(vararg blocks: Block) = PageLayout(PageIds.HOME, blocks = blocks.toList())

    @Test
    fun `an id on a page is in use`() {
        assertEquals(setOf(7), WidgetBlockDefinition.idsIn(listOf(page(widget(7)))))
    }

    @Test
    fun `blocks with no widget chosen hold no id`() {
        val ids = WidgetBlockDefinition.idsIn(listOf(page(widget(null), widget(WidgetHost.INVALID, at = 3))))
        assertTrue("a block with no widget must not pin an id: $ids", ids.isEmpty())
    }

    @Test
    fun `two blocks may name the same widget`() {
        // Duplicating a widget block copies its id, so deleting one of the pair must not make
        // the widget releasable while the other is still on the page.
        val both = page(widget(12), widget(12, at = 3))
        assertEquals(setOf(12), WidgetBlockDefinition.idsIn(listOf(both)))
        val one = page(widget(12))
        assertTrue(12 in WidgetBlockDefinition.idsIn(listOf(one)))
    }

    @Test
    fun `every page counts, not just the one on screen`() {
        val pages = listOf(page(widget(1)), PageLayout("left", blocks = listOf(widget(2))))
        assertEquals(setOf(1, 2), WidgetBlockDefinition.idsIn(pages))
    }

    @Test
    fun `an id no page names is the only kind that may be released`() {
        val live = WidgetBlockDefinition.idsIn(listOf(page(widget(4))))
        val heldByTheHost = setOf(4, 9)
        assertEquals(setOf(9), heldByTheHost - live)
        assertFalse("an id still on a page must never be released", 4 in (heldByTheHost - live))
    }

    @Test
    fun `other block types are ignored`() {
        val clock = Block(id = "c1", type = "clock", style = "plain", rect = GridRect(0, 6, 6, 2))
        assertEquals(setOf(5), WidgetBlockDefinition.idsIn(listOf(page(widget(5), clock))))
    }
}
