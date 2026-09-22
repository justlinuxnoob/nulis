// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.home.GridMetrics
import com.nulis.launcher.home.PageEditorState
import com.nulis.launcher.home.ResizeGrip
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Resize a block, then drag it somewhere else. The size it was given has to arrive with it.
 *
 * The two gestures are separate commits, and between them the editor holds a draft that is
 * ahead of what is saved. A move that reads anything other than that draft moves the block the
 * user had before they resized it, and the resize is silently undone by the drop.
 */
class EditorResizeThenMoveTest {

    private val noHaptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }

    /** 6 x 12 cells of 100 px each, so one cell is one hundred pixels in either direction. */
    private fun metrics() = GridMetrics(
        pageWidth = 600.dp,
        pageHeight = 1200.dp,
        gutter = 0.dp,
        density = Density(1f),
    )

    private fun state(layout: PageLayout): PageEditorState =
        PageEditorState(noHaptics).apply {
            sync(layout)
            metrics = metrics()
        }

    private fun clock(id: String, rect: GridRect) =
        Block(id = id, type = "clock", style = "plain", rect = rect)

    @Test
    fun `a block keeps the size it was resized to when it is then moved`() {
        val start = PageLayout(PageIds.HOME, blocks = listOf(clock("a", GridRect(0, 0, 6, 2))))
        var saved = start
        val s = state(start)

        // Pull the bottom edge down two cells: 6 x 2 becomes 6 x 4.
        s.startResize("a", ResizeGrip.BOTTOM)
        s.resizeBy(0f, 200f)
        s.endResize { saved = it }
        assertEquals(GridSpan(6, 4), saved.block("a")!!.area.span)

        // Now drag it three cells down the page.
        s.pickUp("a")
        s.dragBy(0f, 300f)
        s.drop { saved = it }

        val moved = saved.block("a")!!.area
        assertEquals("the move must not give the block its old height back", GridSpan(6, 4), moved.span)
        assertEquals("and it must actually have moved", 3, moved.row)
    }

    @Test
    fun `the same holds when the saved layout has not caught up with the resize`() {
        // The editor writes asynchronously, so the layout flowing back in can still be the one
        // from before the resize. That stale value must not become the draft the drag reads.
        val start = PageLayout(PageIds.HOME, blocks = listOf(clock("a", GridRect(0, 0, 6, 2))))
        var saved = start
        val s = state(start)

        s.startResize("a", ResizeGrip.BOTTOM)
        s.resizeBy(0f, 200f)
        s.endResize { saved = it }

        s.sync(start) // the write has not landed yet

        s.pickUp("a")
        s.dragBy(0f, 300f)
        s.drop { saved = it }

        assertEquals(GridSpan(6, 4), saved.block("a")!!.area.span)
    }
}
