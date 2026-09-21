// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.home.makeRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Dragging a block is the one thing in the editor that can quietly lose somebody's work, so the
 * rule it has to keep is stated here as an invariant and then hammered with random drags: a move
 * either happens in full, with every block still on the page and nothing on top of anything
 * else, or it does not happen at all.
 */
class MakeRoomTest {

    private fun at(id: String, col: Int, row: Int, cols: Int, rows: Int) =
        Block(id = id, type = "date", style = "uppercase", rect = GridRect(col, row, cols, rows))

    private fun page(vararg blocks: Block) = PageLayout(PageIds.HOME, blocks.toList())

    @Test
    fun `a move into empty space simply happens`() {
        val layout = page(at("a", 0, 0, 3, 2))
        val moved = makeRoom(layout, "a", GridRect(3, 6, 3, 2))
        assertEquals(GridRect(3, 6, 3, 2), moved?.block("a")?.rect)
    }

    @Test
    fun `two blocks of the same shape swap`() {
        val layout = page(at("a", 0, 0, 3, 2), at("b", 3, 0, 3, 2))
        val moved = makeRoom(layout, "a", GridRect(3, 0, 3, 2))
        assertEquals(GridRect(3, 0, 3, 2), moved?.block("a")?.rect)
        assertEquals(GridRect(0, 0, 3, 2), moved?.block("b")?.rect)
    }

    @Test
    fun `a displaced block slides into the space the dragged one left`() {
        val layout = page(at("a", 0, 0, 6, 2), at("b", 0, 4, 6, 2))
        val moved = makeRoom(layout, "a", GridRect(0, 4, 6, 2))
        assertNotNull(moved)
        assertEquals(2, moved!!.blocks.size)
        assertFalse(moved.block("a")!!.area.overlaps(moved.block("b")!!.area))
    }

    @Test
    fun `a move with nowhere for the displaced block to go is refused whole`() {
        // The page is full except for the one row the dragged block is about to vacate, and the
        // block it lands on is two rows tall, so there is nowhere for that one to stand.
        val layout = page(
            at("clock", 0, 1, 6, 3),
            at("date", 0, 4, 6, 1),
            at("apps", 0, 5, 6, 3),
            at("music", 0, 8, 6, 2),
            at("greeting", 0, 10, 6, 2),
        )
        assertNull(makeRoom(layout, "date", GridRect(0, 10, 6, 1)))
    }

    @Test
    fun `a move off the grid is refused`() {
        val layout = page(at("a", 0, 0, 3, 2))
        assertNull(makeRoom(layout, "a", GridRect(5, 0, 3, 2)))
        assertNull(makeRoom(layout, "a", GridRect(0, 11, 3, 2)))
        assertNull(makeRoom(layout, "a", GridRect(-1, 0, 3, 2)))
    }

    /** The invariant, against a thousand random pages and a thousand random drags. */
    @Test
    fun `a move never loses a block and never leaves two on the same cell`() {
        val random = Random(20260921)
        repeat(1000) { round ->
            val layout = randomPage(random)
            if (layout.blocks.isEmpty()) return@repeat
            val target = layout.blocks[random.nextInt(layout.blocks.size)]
            val want = GridRect(
                col = random.nextInt(-1, PageGrid.COLUMNS + 1),
                row = random.nextInt(-1, PageGrid.ROWS + 1),
                cols = target.area.cols,
                rows = target.area.rows,
            )
            val moved = makeRoom(layout, target.id, want) ?: return@repeat
            assertEquals("round $round lost a block", layout.blocks.size, moved.blocks.size)
            assertEquals(
                "round $round lost an id",
                layout.blocks.map { it.id }.toSet(),
                moved.blocks.map { it.id }.toSet(),
            )
            moved.blocks.forEach { block ->
                assertTrue("round $round put ${block.id} off the grid", block.area.fitsGrid)
            }
            moved.blocks.forEachIndexed { index, one ->
                moved.blocks.drop(index + 1).forEach { other ->
                    assertFalse(
                        "round $round overlapped ${one.id} and ${other.id}",
                        one.area.overlaps(other.area),
                    )
                }
            }
        }
    }

    private fun randomPage(random: Random): PageLayout {
        val blocks = ArrayList<Block>()
        val taken = ArrayList<GridRect>()
        repeat(random.nextInt(1, 7)) { index ->
            val cols = random.nextInt(1, PageGrid.COLUMNS + 1)
            val rows = random.nextInt(1, 4)
            val spot = com.nulis.launcher.blocks.GridOccupancy(taken)
                .firstFit(com.nulis.launcher.blocks.GridSpan(cols, rows)) ?: return@repeat
            taken += spot
            blocks += at("b$index", spot.col, spot.row, spot.cols, spot.rows)
        }
        return PageLayout(PageIds.HOME, blocks)
    }
}
